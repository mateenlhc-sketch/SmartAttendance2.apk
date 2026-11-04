# Firestore Security Specification — AI Smart School Attendance System

## 1. Data Invariants
1. **Authentication Mandatory**: Every read (`get`, `list`) and write (`create`, `update`, `delete`) across `/teachers/{teacherId}`, `/classes/{classId}`, `/students/{studentId}`, and `/attendance/{attendanceId}` requires `request.auth != null`.
2. **Teacher Multi-Tenant Isolation**:
   - In `/teachers/{teacherId}`, `teacherId == request.auth.uid` and `data.teacherId == request.auth.uid`. PII (`email`, `displayName`) is readable and writable strictly by the owning teacher (`request.auth.uid == teacherId`).
   - In `/classes/{classId}`, `/students/{studentId}`, and `/attendance/{attendanceId}`, every document carries `teacherId` equal to `request.auth.uid` on creation, `teacherId` is immutable on update, and `allow list` / `allow get` strictly enforce `resource.data.teacherId == request.auth.uid`.
3. **Biometric Privacy & Local Processing**:
   - Live camera frames are never uploaded to Firestore.
   - `faceEmbedding` in `/students/{studentId}` is a bounded array of numbers (`size() <= 256`, specifically 192 floats from MobileFaceNet, or `size() == 0` if biometrics were removed by the teacher).
   - Teachers can clear a student's biometric vector (`faceEmbedding: []`, `sampleCount: 0`) via an explicit update action while preserving student enrollment or historical attendance.
4. **Temporal & Relational Integrity**:
   - All `createdAt` and `updatedAt` fields must be server timestamps (`is timestamp && <= request.time`), with `createdAt` and `teacherId` strictly immutable across updates.
   - Attendance records (`/attendance/{attendanceId}`) reference an existing student via `exists(/databases/$(database)/documents/students/$(incoming().studentId))` and verifying ownership on creation, preventing orphaned or cross-tenant attendance writes.
   - Deterministic attendance document IDs (`{teacherId}_{studentId}_{date}`) prevent duplicate attendance for the same student on the same school day (`allow create` only when document does not exist, and `allow update: if false` to lock attendance records once marked).

## 2. Dirty Dozen Adversarial Payloads
1. **Unauthenticated Student Scrape**: `unauthenticatedContext().collection("students").get()` -> Rejected (`isSignedIn()` fails).
2. **Cross-Teacher Biometric Theft**: Teacher B calls `get` on Teacher A's `/students/{studentId}` -> Rejected (`existing().teacherId == request.auth.uid` fails).
3. **Unfiltered List Query Scraping**: Teacher A calls `collection("students").get()` without `.where("teacherId", "==", uid)` -> Rejected by Query Enforcer (`resource.data.teacherId == request.auth.uid`).
4. **Teacher PII Leak**: Teacher B calls `get` on `/teachers/{teacherA}` -> Rejected (`request.auth.uid == teacherId` fails).
5. **Identity Spoofing on Student Create**: Teacher A writes `/students/stu_1` with `teacherId: "teacher_B"` -> Rejected (`incoming().teacherId == request.auth.uid` fails).
6. **Shadow / Ghost Field Injection**: Teacher A creates `/students/stu_1` with extra key `"isAdmin": true` -> Rejected (`incoming().keys().hasOnly(...)` fails).
7. **Denial-of-Wallet Oversized String / ID**: Attacker injects a 500-char ID or 100KB `photoBase64` / `name` -> Rejected by `isValidId()` (`size() <= 128`) and strict string `.size()` bounds.
8. **Unbounded Biometric Array**: Attacker writes 10,000 floats into `faceEmbedding` -> Rejected (`data.faceEmbedding.size() <= 256`).
9. **Client Clock Skew / Epoch Forgery**: Client sends `createdAt: 1700000000000` (integer) or future timestamp -> Rejected (`incoming().createdAt is timestamp && incoming().createdAt <= request.time`).
10. **Immutable Field Tampering on Update**: Teacher A updates `/students/stu_1` mutating `teacherId` or `createdAt` -> Rejected (`incoming().teacherId == existing().teacherId && incoming().createdAt == existing().createdAt`).
11. **Orphaned / Unregistered Attendance Injection**: Teacher A writes `/attendance/att_1` for a non-existent `studentId` or another teacher's student -> Rejected (`exists(...)` and `get(...).data.teacherId == request.auth.uid` fail).
12. **Duplicate Same-Day Attendance Overwrite**: Attempting to overwrite an existing `/attendance/{teacherId}_{studentId}_{date}` document -> Rejected (`allow update: if false`).
