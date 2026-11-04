const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-smartatt-b40e99d1-a0d3-40cb-8a6e-dbf2c8483395";
const TEACHER_A = "teacher_alice_01";
const TEACHER_B = "teacher_bob_02";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function makeValidEmbedding(dim = 192) {
  return Array.from({ length: dim }, (_, i) => (i % 7) * 0.05);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function getDb(ctx) {
  return ctx.firestore(DATABASE_ID);
}

function serverTimestamp() {
  // Firestore compat SDK converts JS Date to Firestore Timestamp; subtract 1s to stay <= request.time
  return new Date(Date.now() - 1000);
}

test("1. Unauthenticated caller cannot read or write students or attendance", async () => {
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  await assertFails(unauthDb.collection("students").get());
  await assertFails(unauthDb.collection("attendance").get());
});

test("2. Teacher profile PII is strictly isolated to owning teacher", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(TEACHER_A));
  const bobDb = getDb(testEnv.authenticatedContext(TEACHER_B));
  const ts = serverTimestamp();

  await assertSucceeds(
    aliceDb.collection("teachers").doc(TEACHER_A).set({
      teacherId: TEACHER_A,
      displayName: "Ms. Alice",
      email: "alice@school.edu",
      schoolName: "ABC High School",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("teachers").doc(TEACHER_A).get());
  await assertFails(bobDb.collection("teachers").doc(TEACHER_A).get());
  await assertFails(aliceDb.collection("teachers").get());
});

test("3. Student enrollment enforces 192D embedding, rejects ghost fields, and isolates cross-teacher access", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(TEACHER_A));
  const bobDb = getDb(testEnv.authenticatedContext(TEACHER_B));
  const ts = serverTimestamp();

  const validStudent = {
    teacherId: TEACHER_A,
    name: "Aarav Sharma",
    rollNumber: "601",
    className: "Class 6",
    photoBase64: "data:image/jpeg;base64,abc",
    faceEmbedding: makeValidEmbedding(192),
    sampleCount: 5,
    registrationDate: "2026-11-04",
    createdAt: ts,
    updatedAt: ts,
  };

  await assertSucceeds(aliceDb.collection("students").doc("stu_001").set(validStudent));

  // Reject ghost field injection (Shadow Update Test)
  await assertFails(
    aliceDb.collection("students").doc("stu_ghost").set({
      ...validStudent,
      isAdmin: true,
    })
  );

  // Reject empty or fake short embedding on enrollment
  await assertFails(
    aliceDb.collection("students").doc("stu_empty_emb").set({
      ...validStudent,
      faceEmbedding: [0.1, 0.2],
    })
  );

  // Query alignment: unfiltered list rejected; teacherId-filtered list succeeds
  await assertFails(aliceDb.collection("students").get());
  await assertSucceeds(aliceDb.collection("students").where("teacherId", "==", TEACHER_A).get());

  // Cross-teacher biometric read rejected
  await assertFails(bobDb.collection("students").doc("stu_001").get());
});

test("4. Student biometric removal action is permitted while immutable fields are locked", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(TEACHER_A));
  const ts = serverTimestamp();

  await assertSucceeds(
    aliceDb.collection("students").doc("stu_bio").set({
      teacherId: TEACHER_A,
      name: "Maya Patel",
      rollNumber: "702",
      className: "Class 7",
      faceEmbedding: makeValidEmbedding(192),
      sampleCount: 5,
      registrationDate: "2026-11-04",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Teacher removes biometric embedding
  await assertSucceeds(
    aliceDb.collection("students").doc("stu_bio").update({
      faceEmbedding: [],
      sampleCount: 0,
      updatedAt: serverTimestamp(),
    })
  );

  // Reject mutating teacherId
  await assertFails(
    aliceDb.collection("students").doc("stu_bio").update({
      teacherId: TEACHER_B,
      updatedAt: serverTimestamp(),
    })
  );
});

test("5. Attendance requires existing owned student, deterministic same-day ID, and blocks duplicate overwrites", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(TEACHER_A));
  const ts = serverTimestamp();

  await assertSucceeds(
    aliceDb.collection("students").doc("stu_att_1").set({
      teacherId: TEACHER_A,
      name: "Liam Chen",
      rollNumber: "801",
      className: "Class 8",
      faceEmbedding: makeValidEmbedding(192),
      sampleCount: 5,
      registrationDate: "2026-11-04",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const detId = `${TEACHER_A}_stu_att_1_2026-11-04`;
  const record = {
    teacherId: TEACHER_A,
    studentId: "stu_att_1",
    studentName: "Liam Chen",
    rollNumber: "801",
    className: "Class 8",
    date: "2026-11-04",
    time: "08:30 AM",
    status: "Present",
    verificationMethod: "AI Face Recognition",
    confidenceScore: 0.91,
    createdAt: serverTimestamp(),
  };

  // Valid attendance succeeds
  await assertSucceeds(aliceDb.collection("attendance").doc(detId).set(record));

  // Duplicate overwrite on same day fails (update locked)
  await assertFails(
    aliceDb.collection("attendance").doc(detId).set({
      ...record,
      time: "09:15 AM",
      createdAt: serverTimestamp(),
    })
  );

  // Orphaned attendance for non-existent student fails
  const fakeDetId = `${TEACHER_A}_nonexistent_2026-11-04`;
  await assertFails(
    aliceDb.collection("attendance").doc(fakeDetId).set({
      ...record,
      studentId: "nonexistent",
      createdAt: serverTimestamp(),
    })
  );
});
