package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.FaceDescriptorEngine
import com.example.domain.MatchEvaluation
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class AttendanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun orthogonalUnitEmbedding(basisIndex: Int, jitter: Float = 0f): FloatArray {
        val raw = FloatArray(192) { idx ->
            val primary = sin((idx + 1) * (basisIndex + 1) * 0.37).toFloat()
            val secondary = cos((idx + 3) * (basisIndex + 2) * 0.19).toFloat() * jitter
            primary + secondary
        }
        return FaceDescriptorEngine.l2Normalize(raw)
    }

    @Test
    fun fullScenario_registerThreeStudents_matchEach_rejectUnknown_markAttendance_preventDuplicate_andBiometricClear() = runBlocking {
        val uniqueRun = UUID.randomUUID().toString().take(6)
        val teacherUid = signInTestUser("teacher_$uniqueRun@school.edu")
        val repository = AttendanceRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureTeacherAndClassesInitialized(
                teacherId = teacherUid,
                displayName = "Ms. Rivera",
                email = "teacher_$uniqueRun@school.edu"
            )
        }

        // TEST 1: Register Student A, Student B, Student C with 192D embeddings in Firestore
        val embA = orthogonalUnitEmbedding(1)
        val embB = orthogonalUnitEmbedding(5)
        val embC = orthogonalUnitEmbedding(11)

        val regA = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerStudent("Student A", "601_$uniqueRun", "Class 6", null, embA, 5)
        }
        val regB = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerStudent("Student B", "701_$uniqueRun", "Class 7", null, embB, 5)
        }
        val regC = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerStudent("Student C", "801_$uniqueRun", "Class 8", null, embC, 5)
        }

        assertTrue(regA is RegisterStudentOutcome.Registered)
        assertTrue(regB is RegisterStudentOutcome.Registered)
        assertTrue(regC is RegisterStudentOutcome.Registered)

        // Prevent duplicate roll number within same class
        val dupA = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.registerStudent("Duplicate A", "601_$uniqueRun", "Class 6", null, embA, 5)
        }
        assertTrue(dupA is RegisterStudentOutcome.DuplicateRollNumber)

        // TEST 5 (Persistence via Firestore Stream): Observe all 3 students from Firestore
        val persistedStudents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(teacherUid).first { it.size >= 3 }
        }
        assertEquals(3, persistedStudents.size)
        assertTrue(persistedStudents.all { it.hasValidBiometrics && it.faceEmbedding.size == 192 })

        val candidates = persistedStudents.map { it to it.toFloatEmbedding() }

        // TEST 2: Real-Time Recognition identifies Student A, Student B, Student C accurately
        val evalA = FaceDescriptorEngine.evaluateClosestMatch(orthogonalUnitEmbedding(1, 0.04f), candidates)
        assertTrue(evalA is MatchEvaluation.VerifiedMatch)
        assertEquals("Student A", (evalA as MatchEvaluation.VerifiedMatch).candidate.name)

        val evalB = FaceDescriptorEngine.evaluateClosestMatch(orthogonalUnitEmbedding(5, 0.04f), candidates)
        assertTrue(evalB is MatchEvaluation.VerifiedMatch)
        assertEquals("Student B", (evalB as MatchEvaluation.VerifiedMatch).candidate.name)

        val evalC = FaceDescriptorEngine.evaluateClosestMatch(orthogonalUnitEmbedding(11, 0.04f), candidates)
        assertTrue(evalC is MatchEvaluation.VerifiedMatch)
        assertEquals("Student C", (evalC as MatchEvaluation.VerifiedMatch).candidate.name)

        // TEST 3: Unregistered Face -> UNKNOWN STUDENT, no attendance created
        val unregisteredEmb = orthogonalUnitEmbedding(29)
        val evalUnknown = FaceDescriptorEngine.evaluateClosestMatch(unregisteredEmb, candidates)
        assertTrue(evalUnknown is MatchEvaluation.UnknownFace)

        // TEST 4: Automatic Attendance saved to Firestore & duplicate attendance prevented
        val studentA = evalA.candidate
        val mark1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.markStudentPresentTransaction(studentA, evalA.similarity)
        }
        assertTrue(mark1 is MarkAttendanceOutcome.MarkedSuccess)

        val markDuplicate = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.markStudentPresentTransaction(studentA, evalA.similarity)
        }
        assertTrue(markDuplicate is MarkAttendanceOutcome.AlreadyMarkedToday)

        val attendanceList = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAttendance(teacherUid).first { it.isNotEmpty() }
        }
        assertEquals(1, attendanceList.size)
        assertEquals(studentA.id, attendanceList.first().studentId)

        // Biometric removal security capability
        val clearRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.clearStudentBiometrics(studentA)
        }
        assertTrue(clearRes.isSuccess)
        val refreshedA = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getStudentById(studentA.id).getOrNull()
        }
        assertFalse(refreshedA?.hasValidBiometrics ?: true)
    }

    @Test
    fun crossTeacherAccess_and_unauthenticatedAccess_failWithPermissionDenied() = runBlocking {
        val uniqueRun = UUID.randomUUID().toString().take(6)
        signInTestUser("alice_$uniqueRun@school.edu")
        val aliceRepo = AttendanceRepository(firestore)

        val emb = orthogonalUnitEmbedding(3)
        val reg = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.registerStudent("Alice Student", "901_$uniqueRun", "Class 9", null, emb, 5)
        } as RegisterStudentOutcome.Registered

        // Switch to Teacher Bob -> cannot read Alice's student document
        signInTestUser("bob_$uniqueRun@school.edu")
        val bobRepo = AttendanceRepository(firestore)
        val crossRead = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getStudentById(reg.student.id)
        }
        assertTrue(crossRead.isFailure)
        val crossEx = crossRead.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, crossEx?.code)

        // Sign out -> unauthenticated stream rejected with PERMISSION_DENIED
        auth.signOut()
        var caughtEx: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeStudents("any_uid").first()
            }
        } catch (e: Exception) {
            caughtEx = e
        }
        val unauthFirestoreEx = checkNotNull(caughtEx?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException on unauthenticated observeStudents, got $caughtEx"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, unauthFirestoreEx.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
