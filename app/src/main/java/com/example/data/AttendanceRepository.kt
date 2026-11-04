package com.example.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class RegisterStudentOutcome {
    data class Registered(val student: StudentDoc) : RegisterStudentOutcome()
    data class DuplicateRollNumber(val rollNumber: String, val className: String) : RegisterStudentOutcome()
    data class InvalidBiometrics(val reason: String) : RegisterStudentOutcome()
}

sealed class MarkAttendanceOutcome {
    data class MarkedSuccess(val record: AttendanceDoc, val student: StudentDoc) : MarkAttendanceOutcome()
    data class AlreadyMarkedToday(val existing: AttendanceDoc, val student: StudentDoc) : MarkAttendanceOutcome()
}

interface AttendanceDataSource {
    fun todayDateString(): String
    fun currentTimeString(): String
    fun formattedReadableDate(isoDate: String): String
    suspend fun ensureTeacherAndClassesInitialized(
        teacherId: String,
        displayName: String,
        email: String,
        schoolName: String = "ABC High School"
    )
    fun observeStudents(teacherId: String): Flow<List<StudentDoc>>
    fun observeClasses(teacherId: String): Flow<List<SchoolClassDoc>>
    fun observeAttendance(teacherId: String): Flow<List<AttendanceDoc>>
    suspend fun registerStudent(
        name: String,
        rollNumber: String,
        className: String,
        photoBase64: String?,
        embedding: FloatArray,
        sampleCount: Int
    ): RegisterStudentOutcome
    suspend fun updateStudentMetadata(
        original: StudentDoc,
        newName: String,
        newRollNumber: String,
        newClassName: String
    ): Result<Unit>
    suspend fun clearStudentBiometrics(student: StudentDoc): Result<Unit>
    suspend fun deleteStudent(
        student: StudentDoc,
        retainHistoricalAttendance: Boolean
    ): Result<Unit>
    suspend fun markStudentPresentTransaction(
        student: StudentDoc,
        confidenceScore: Float,
        verificationMethod: String = "AI Face Recognition",
        dateOverride: String? = null
    ): MarkAttendanceOutcome
    fun buildCsvContent(
        date: String,
        classFilter: String,
        students: List<StudentDoc>,
        attendanceRecords: List<AttendanceDoc>,
        includeHistoricalAllDates: Boolean = false
    ): String
    suspend fun writeCsvToCacheAndGetShareIntent(
        context: Context,
        csvContent: String,
        fileName: String
    ): Intent
}

class AttendanceRepository(private val db: FirebaseFirestore) : AttendanceDataSource {

    // Secondary convenience constructor resolving provisioned named database ID
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireTeacherId(): String {
        return Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("Authorized teacher must sign in with Google before accessing Firestore.")
    }

    override fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    override fun currentTimeString(): String =
        SimpleDateFormat("hh:mm a", Locale.US).format(Date())

    override fun formattedReadableDate(isoDate: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US)
            val parsed = parser.parse(isoDate)
            if (parsed != null) formatter.format(parsed) else isoDate
        } catch (_: Exception) {
            isoDate
        }
    }

    /**
     * Bootstraps the authenticated teacher profile in `/teachers/{teacherId}` and
     * standard school classes (`Class 6`..`Class 9`) in `/classes/{classId}` on sign-in.
     */
    override suspend fun ensureTeacherAndClassesInitialized(
        teacherId: String,
        displayName: String,
        email: String,
        schoolName: String
    ) {
        val teacherRef = db.collection("teachers").document(teacherId)
        try {
            val existing = teacherRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "teacherId" to teacherId,
                    "displayName" to displayName.ifBlank { "Authorized Teacher" }.take(100),
                    "email" to email.ifBlank { "teacher@school.edu" }.take(150),
                    "schoolName" to schoolName.take(120),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                teacherRef.set(payload).await()
            }

            // Ensure standard school classes exist in /classes collection
            for (cls in SCHOOL_CLASSES) {
                val slug = cls.lowercase(Locale.US).replace(" ", "_")
                val classDocId = "${teacherId}_$slug"
                val classRef = db.collection("classes").document(classDocId)
                val classSnap = classRef.get().await()
                if (!classSnap.exists()) {
                    classRef.set(
                        mapOf(
                            "teacherId" to teacherId,
                            "className" to cls,
                            "schoolName" to schoolName.take(120),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, teacherRef.path)
            throw e
        }
    }

    /**
     * Real-time stream of registered students scoped strictly to `teacherId`.
     */
    override fun observeStudents(teacherId: String): Flow<List<StudentDoc>> {
        val path = "students"
        return db.collection(path)
            .whereEqualTo("teacherId", teacherId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StudentDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(compareBy({ it.className }, { it.rollNumber }))
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    /**
     * Real-time stream of school classes scoped strictly to `teacherId`.
     */
    override fun observeClasses(teacherId: String): Flow<List<SchoolClassDoc>> {
        val path = "classes"
        return db.collection(path)
            .whereEqualTo("teacherId", teacherId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SchoolClassDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.className }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    /**
     * Real-time stream of attendance records scoped strictly to `teacherId`.
     */
    override fun observeAttendance(teacherId: String): Flow<List<AttendanceDoc>> {
        val path = "attendance"
        return db.collection(path)
            .whereEqualTo("teacherId", teacherId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AttendanceDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { err ->
                if (err is Exception) handleFirestoreError(err, OperationType.LIST, path)
                throw err
            }
    }

    suspend fun getStudentById(studentId: String): Result<StudentDoc?> = runCatching {
        val docRef = db.collection("students").document(studentId)
        try {
            docRef.get().await().toObject(
                StudentDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    /**
     * Saves a newly enrolled student with genuine numerical facial embeddings in Firestore
     * after checking for duplicate roll numbers within the same class.
     */
    override suspend fun registerStudent(
        name: String,
        rollNumber: String,
        className: String,
        photoBase64: String?,
        embedding: FloatArray,
        sampleCount: Int
    ): RegisterStudentOutcome {
        val teacherId = requireTeacherId()
        if (embedding.size < 128 || sampleCount < 1) {
            return RegisterStudentOutcome.InvalidBiometrics(
                "Valid 192-dimensional MobileFaceNet embedding is required before saving."
            )
        }
        val normalizedRoll = rollNumber.trim()
        val trimmedName = name.trim()

        // Query teacher's students in the same class to prevent duplicate roll numbers
        val duplicateCheckPath = "students"
        try {
            val existingQuery = db.collection(duplicateCheckPath)
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("className", className)
                .whereEqualTo("rollNumber", normalizedRoll)
                .get()
                .await()

            if (!existingQuery.isEmpty) {
                return RegisterStudentOutcome.DuplicateRollNumber(normalizedRoll, className)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, duplicateCheckPath)
            throw e
        }

        val studentId = "stu_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val docRef = db.collection("students").document(studentId)
        val regDate = todayDateString()
        val embeddingList: List<Double> = embedding.map { it.toDouble() }

        val payload = mutableMapOf<String, Any>(
            "teacherId" to teacherId,
            "name" to trimmedName,
            "rollNumber" to normalizedRoll,
            "className" to className,
            "faceEmbedding" to embeddingList,
            "sampleCount" to sampleCount.toLong(),
            "registrationDate" to regDate,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!photoBase64.isNullOrBlank()) {
            payload["photoBase64"] = photoBase64
        }

        try {
            docRef.set(payload).await()
            val savedSnap = docRef.get().await()
            val savedDoc = savedSnap.toObject(
                StudentDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: StudentDoc(
                id = studentId,
                teacherId = teacherId,
                name = trimmedName,
                rollNumber = normalizedRoll,
                className = className,
                photoBase64 = photoBase64,
                faceEmbedding = embeddingList,
                sampleCount = sampleCount.toLong(),
                registrationDate = regDate
            )
            return RegisterStudentOutcome.Registered(savedDoc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    /**
     * Updates student metadata (name, rollNumber, className) while enforcing roll number uniqueness.
     */
    override suspend fun updateStudentMetadata(
        original: StudentDoc,
        newName: String,
        newRollNumber: String,
        newClassName: String
    ): Result<Unit> = runCatching {
        val teacherId = requireTeacherId()
        require(original.id.isNotBlank()) { "Student ID must not be blank" }
        val normalizedRoll = newRollNumber.trim()

        val conflictQuery = db.collection("students")
            .whereEqualTo("teacherId", teacherId)
            .whereEqualTo("className", newClassName)
            .whereEqualTo("rollNumber", normalizedRoll)
            .get()
            .await()

        val hasConflict = conflictQuery.documents.any { it.id != original.id }
        if (hasConflict) {
            throw IllegalArgumentException("Roll number $normalizedRoll already exists in $newClassName.")
        }

        val docRef = db.collection("students").document(original.id)
        try {
            docRef.update(
                mapOf(
                    "name" to newName.trim(),
                    "rollNumber" to normalizedRoll,
                    "className" to newClassName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    /**
     * Security feature: Removes a student's stored facial biometric vector from Firestore
     * while retaining their student directory profile and historical attendance.
     */
    override suspend fun clearStudentBiometrics(student: StudentDoc): Result<Unit> = runCatching {
        requireTeacherId()
        require(student.id.isNotBlank()) { "Student ID must not be blank" }
        val docRef = db.collection("students").document(student.id)
        try {
            docRef.update(
                mapOf(
                    "faceEmbedding" to emptyList<Double>(),
                    "sampleCount" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    /**
     * Deletes a student from `/students/{studentId}`, with an explicit option to retain
     * or delete historical attendance records.
     */
    override suspend fun deleteStudent(
        student: StudentDoc,
        retainHistoricalAttendance: Boolean
    ): Result<Unit> = runCatching {
        val teacherId = requireTeacherId()
        require(student.id.isNotBlank()) { "Student ID must not be blank" }

        if (!retainHistoricalAttendance) {
            val attQuery = db.collection("attendance")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("studentId", student.id)
                .get()
                .await()
            for (attDoc in attQuery.documents) {
                try {
                    attDoc.reference.delete().await()
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.DELETE, attDoc.reference.path)
                    throw e
                }
            }
        }

        val docRef = db.collection("students").document(student.id)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    /**
     * Concurrency-safe attendance marking using a Firestore Transaction and deterministic
     * document ID `{teacherId}_{studentId}_{date}` so duplicate same-day attendance is impossible.
     */
    override suspend fun markStudentPresentTransaction(
        student: StudentDoc,
        confidenceScore: Float,
        verificationMethod: String,
        dateOverride: String?
    ): MarkAttendanceOutcome {
        val teacherId = requireTeacherId()
        require(student.id.isNotBlank()) { "Cannot mark attendance for unregistered student" }

        val dateStr = dateOverride ?: todayDateString()
        val timeStr = currentTimeString()
        val deterministicId = "${teacherId}_${student.id}_$dateStr"
        val attRef = db.collection("attendance").document(deterministicId)

        try {
            val wasCreated = db.runTransaction { transaction ->
                val existingSnap = transaction.get(attRef)
                if (existingSnap.exists()) {
                    false
                } else {
                    val payload = mapOf(
                        "teacherId" to teacherId,
                        "studentId" to student.id,
                        "studentName" to student.name,
                        "rollNumber" to student.rollNumber,
                        "className" to student.className,
                        "date" to dateStr,
                        "time" to timeStr,
                        "status" to "Present",
                        "verificationMethod" to verificationMethod,
                        "confidenceScore" to confidenceScore.toDouble().coerceIn(0.0, 1.0),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    transaction.set(attRef, payload)
                    true
                }
            }.await()

            val snap = attRef.get().await()
            val record = snap.toObject(
                AttendanceDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: AttendanceDoc(
                id = deterministicId,
                teacherId = teacherId,
                studentId = student.id,
                studentName = student.name,
                rollNumber = student.rollNumber,
                className = student.className,
                date = dateStr,
                time = timeStr,
                status = "Present",
                verificationMethod = verificationMethod,
                confidenceScore = confidenceScore.toDouble()
            )

            return if (wasCreated) {
                MarkAttendanceOutcome.MarkedSuccess(record, student)
            } else {
                MarkAttendanceOutcome.AlreadyMarkedToday(record, student)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            // If concurrent transaction or security rule blocked overwrite because doc now exists, read existing
            if (e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS ||
                e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            ) {
                val existingCheck = runCatching {
                    attRef.get().await().toObject(
                        AttendanceDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }.getOrNull()
                if (existingCheck != null) {
                    return MarkAttendanceOutcome.AlreadyMarkedToday(existingCheck, student)
                }
            }
            handleFirestoreError(e, OperationType.CREATE, attRef.path)
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, attRef.path)
            throw e
        }
    }

    override fun buildCsvContent(
        date: String,
        classFilter: String,
        students: List<StudentDoc>,
        attendanceRecords: List<AttendanceDoc>,
        includeHistoricalAllDates: Boolean
    ): String = buildSharedCsvContent(
        date = date,
        classFilter = classFilter,
        students = students,
        attendanceRecords = attendanceRecords,
        includeHistoricalAllDates = includeHistoricalAllDates
    )

    override suspend fun writeCsvToCacheAndGetShareIntent(
        context: Context,
        csvContent: String,
        fileName: String
    ): Intent = withContext(Dispatchers.IO) {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val outFile = File(reportsDir, fileName)
        outFile.writeText(csvContent, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "ABC High School Attendance Report - $fileName")
            putExtra(
                Intent.EXTRA_TEXT,
                "Attached is the exported Cloud Firestore attendance report from AI Smart School Attendance System."
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

internal fun buildSharedCsvContent(
    date: String,
    classFilter: String,
    students: List<StudentDoc>,
    attendanceRecords: List<AttendanceDoc>,
    includeHistoricalAllDates: Boolean = false
): String {
    val sb = StringBuilder()
    if (includeHistoricalAllDates) {
        sb.appendLine("School,Date,Time,Class,Roll Number,Student Name,Status,Verification Method,Confidence")
        val filteredRecords = attendanceRecords.filter {
            classFilter == "All Classes" || it.className == classFilter
        }
        for (att in filteredRecords) {
            val confPct = "${(att.confidenceScore * 100.0).toInt()}%"
            sb.appendLine(
                listOf(
                    "ABC High School",
                    att.date,
                    att.time,
                    att.className,
                    att.rollNumber,
                    att.studentName,
                    att.status,
                    att.verificationMethod,
                    confPct
                ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
            )
        }
    } else {
        val attByStudentId = attendanceRecords
            .filter { it.date == date }
            .associateBy { it.studentId }
        val filteredStudents = if (classFilter == "All Classes") {
            students
        } else {
            students.filter { it.className == classFilter }
        }
        sb.appendLine("School,Report Date,Class Filter,Roll Number,Student Name,Class,Registration Date,Status,Time Marked,Verification Method,Confidence")
        for (stu in filteredStudents) {
            val att = attByStudentId[stu.id]
            val status = att?.status ?: "Absent"
            val time = att?.time ?: "--"
            val method = att?.verificationMethod ?: "--"
            val conf = if (att != null) "${(att.confidenceScore * 100.0).toInt()}%" else "--"
            sb.appendLine(
                listOf(
                    "ABC High School",
                    date,
                    classFilter,
                    stu.rollNumber,
                    stu.name,
                    stu.className,
                    stu.registrationDate,
                    status,
                    time,
                    method,
                    conf
                ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
            )
        }
    }
    return sb.toString()
}
