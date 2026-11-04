package com.example.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Strictly on-device repository used exclusively when the user enters "Continue as Guest"
 * without a Google ID. Uses zero Firebase Auth or Cloud Firestore calls while supporting
 * full 192D MobileFaceNet biometric enrollment, live camera matching, duplicate prevention,
 * and CSV exports persisted locally in `filesDir/guest_attendance_store.json`.
 */
class GuestLocalAttendanceRepository(context: Context) : AttendanceDataSource {

    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "guest_attendance_store.json")
    private val mutex = Mutex()

    private val _studentsFlow = MutableStateFlow<List<StudentDoc>>(emptyList())
    private val _attendanceFlow = MutableStateFlow<List<AttendanceDoc>>(emptyList())
    private val _classesFlow = MutableStateFlow<List<SchoolClassDoc>>(emptyList())

    init {
        loadFromDiskSync()
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

    override suspend fun ensureTeacherAndClassesInitialized(
        teacherId: String,
        displayName: String,
        email: String,
        schoolName: String
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_classesFlow.value.isEmpty()) {
                val nowTs = Timestamp.now()
                _classesFlow.value = SCHOOL_CLASSES.map { cls ->
                    val slug = cls.lowercase(Locale.US).replace(" ", "_")
                    SchoolClassDoc(
                        id = "${teacherId}_$slug",
                        teacherId = teacherId,
                        className = cls,
                        schoolName = schoolName,
                        createdAt = nowTs,
                        updatedAt = nowTs
                    )
                }
                persistToDiskLocked()
            }
        }
    }

    override fun observeStudents(teacherId: String): Flow<List<StudentDoc>> = _studentsFlow.asStateFlow()

    override fun observeClasses(teacherId: String): Flow<List<SchoolClassDoc>> = _classesFlow.asStateFlow()

    override fun observeAttendance(teacherId: String): Flow<List<AttendanceDoc>> = _attendanceFlow.asStateFlow()

    override suspend fun registerStudent(
        name: String,
        rollNumber: String,
        className: String,
        photoBase64: String?,
        embedding: FloatArray,
        sampleCount: Int
    ): RegisterStudentOutcome = withContext(Dispatchers.IO) {
        if (embedding.size < 128 || sampleCount < 1) {
            return@withContext RegisterStudentOutcome.InvalidBiometrics(
                "Valid 192-dimensional MobileFaceNet embedding is required before saving."
            )
        }
        val normalizedRoll = rollNumber.trim()
        val trimmedName = name.trim()

        mutex.withLock {
            val current = _studentsFlow.value
            if (current.any { it.className == className && it.rollNumber.equals(normalizedRoll, ignoreCase = true) }) {
                return@withLock RegisterStudentOutcome.DuplicateRollNumber(normalizedRoll, className)
            }

            val now = Timestamp.now()
            val newDoc = StudentDoc(
                id = "guest_stu_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                teacherId = GUEST_TEACHER_ID,
                name = trimmedName,
                rollNumber = normalizedRoll,
                className = className,
                photoBase64 = photoBase64,
                faceEmbedding = embedding.map { it.toDouble() },
                sampleCount = sampleCount.toLong(),
                registrationDate = todayDateString(),
                createdAt = now,
                updatedAt = now
            )

            _studentsFlow.value = (current + newDoc).sortedWith(compareBy({ it.className }, { it.rollNumber }))
            persistToDiskLocked()
            RegisterStudentOutcome.Registered(newDoc)
        }
    }

    override suspend fun updateStudentMetadata(
        original: StudentDoc,
        newName: String,
        newRollNumber: String,
        newClassName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedRoll = newRollNumber.trim()
            mutex.withLock {
                val current = _studentsFlow.value
                val conflict = current.any {
                    it.id != original.id &&
                        it.className == newClassName &&
                        it.rollNumber.equals(normalizedRoll, ignoreCase = true)
                }
                if (conflict) {
                    throw IllegalArgumentException("Roll number $normalizedRoll already exists in $newClassName.")
                }

                val now = Timestamp.now()
                _studentsFlow.value = current.map { stu ->
                    if (stu.id == original.id) {
                        stu.copy(
                            name = newName.trim(),
                            rollNumber = normalizedRoll,
                            className = newClassName,
                            updatedAt = now
                        )
                    } else stu
                }.sortedWith(compareBy({ it.className }, { it.rollNumber }))

                // Keep attendance snapshot names in sync
                _attendanceFlow.value = _attendanceFlow.value.map { att ->
                    if (att.studentId == original.id) {
                        att.copy(
                            studentName = newName.trim(),
                            rollNumber = normalizedRoll,
                            className = newClassName
                        )
                    } else att
                }
                persistToDiskLocked()
            }
        }
    }

    override suspend fun clearStudentBiometrics(student: StudentDoc): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    val now = Timestamp.now()
                    _studentsFlow.value = _studentsFlow.value.map { stu ->
                        if (stu.id == student.id) {
                            stu.copy(
                                faceEmbedding = emptyList(),
                                sampleCount = 0L,
                                updatedAt = now
                            )
                        } else stu
                    }
                    persistToDiskLocked()
                }
            }
        }

    override suspend fun deleteStudent(
        student: StudentDoc,
        retainHistoricalAttendance: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                _studentsFlow.value = _studentsFlow.value.filterNot { it.id == student.id }
                if (!retainHistoricalAttendance) {
                    _attendanceFlow.value = _attendanceFlow.value.filterNot { it.studentId == student.id }
                }
                persistToDiskLocked()
            }
        }
    }

    override suspend fun markStudentPresentTransaction(
        student: StudentDoc,
        confidenceScore: Float,
        verificationMethod: String,
        dateOverride: String?
    ): MarkAttendanceOutcome = withContext(Dispatchers.IO) {
        val dateStr = dateOverride ?: todayDateString()
        val timeStr = currentTimeString()
        val deterministicId = "${GUEST_TEACHER_ID}_${student.id}_$dateStr"

        mutex.withLock {
            val existing = _attendanceFlow.value.firstOrNull {
                it.id == deterministicId || (it.studentId == student.id && it.date == dateStr)
            }
            if (existing != null) {
                return@withLock MarkAttendanceOutcome.AlreadyMarkedToday(existing, student)
            }

            val record = AttendanceDoc(
                id = deterministicId,
                teacherId = GUEST_TEACHER_ID,
                studentId = student.id,
                studentName = student.name,
                rollNumber = student.rollNumber,
                className = student.className,
                date = dateStr,
                time = timeStr,
                status = "Present",
                verificationMethod = verificationMethod,
                confidenceScore = confidenceScore.toDouble().coerceIn(0.0, 1.0),
                createdAt = Timestamp.now()
            )
            _attendanceFlow.value = listOf(record) + _attendanceFlow.value
            persistToDiskLocked()
            MarkAttendanceOutcome.MarkedSuccess(record, student)
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
                "Attached is the exported attendance report from AI Smart School Attendance System."
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun loadFromDiskSync() {
        if (!storeFile.exists()) return
        runCatching {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            val stuArr = root.optJSONArray("students") ?: JSONArray()
            val loadedStudents = mutableListOf<StudentDoc>()
            for (i in 0 until stuArr.length()) {
                val obj = stuArr.getJSONObject(i)
                val embJson = obj.optJSONArray("faceEmbedding") ?: JSONArray()
                val embList = List(embJson.length()) { idx -> embJson.getDouble(idx) }
                val sec = obj.optLong("createdAtSec", System.currentTimeMillis() / 1000L)
                val ts = Timestamp(sec, 0)
                loadedStudents += StudentDoc(
                    id = obj.getString("id"),
                    teacherId = obj.optString("teacherId", GUEST_TEACHER_ID),
                    name = obj.getString("name"),
                    rollNumber = obj.getString("rollNumber"),
                    className = obj.getString("className"),
                    photoBase64 = obj.optString("photoBase64", "").ifBlank { null },
                    faceEmbedding = embList,
                    sampleCount = obj.optLong("sampleCount", 0L),
                    registrationDate = obj.optString("registrationDate", ""),
                    createdAt = ts,
                    updatedAt = ts
                )
            }
            _studentsFlow.value = loadedStudents.sortedWith(compareBy({ it.className }, { it.rollNumber }))

            val attArr = root.optJSONArray("attendance") ?: JSONArray()
            val loadedAtt = mutableListOf<AttendanceDoc>()
            for (i in 0 until attArr.length()) {
                val obj = attArr.getJSONObject(i)
                val sec = obj.optLong("createdAtSec", System.currentTimeMillis() / 1000L)
                loadedAtt += AttendanceDoc(
                    id = obj.getString("id"),
                    teacherId = obj.optString("teacherId", GUEST_TEACHER_ID),
                    studentId = obj.getString("studentId"),
                    studentName = obj.getString("studentName"),
                    rollNumber = obj.getString("rollNumber"),
                    className = obj.getString("className"),
                    date = obj.getString("date"),
                    time = obj.getString("time"),
                    status = obj.optString("status", "Present"),
                    verificationMethod = obj.optString("verificationMethod", "AI Face Recognition"),
                    confidenceScore = obj.optDouble("confidenceScore", 0.0),
                    createdAt = Timestamp(sec, 0)
                )
            }
            _attendanceFlow.value = loadedAtt.sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
    }

    private fun persistToDiskLocked() {
        runCatching {
            val root = JSONObject()
            val stuArr = JSONArray()
            for (stu in _studentsFlow.value) {
                val obj = JSONObject().apply {
                    put("id", stu.id)
                    put("teacherId", stu.teacherId)
                    put("name", stu.name)
                    put("rollNumber", stu.rollNumber)
                    put("className", stu.className)
                    if (!stu.photoBase64.isNullOrBlank()) put("photoBase64", stu.photoBase64)
                    put("faceEmbedding", JSONArray(stu.faceEmbedding))
                    put("sampleCount", stu.sampleCount)
                    put("registrationDate", stu.registrationDate)
                    put("createdAtSec", stu.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L))
                }
                stuArr.put(obj)
            }
            root.put("students", stuArr)

            val attArr = JSONArray()
            for (att in _attendanceFlow.value) {
                val obj = JSONObject().apply {
                    put("id", att.id)
                    put("teacherId", att.teacherId)
                    put("studentId", att.studentId)
                    put("studentName", att.studentName)
                    put("rollNumber", att.rollNumber)
                    put("className", att.className)
                    put("date", att.date)
                    put("time", att.time)
                    put("status", att.status)
                    put("verificationMethod", att.verificationMethod)
                    put("confidenceScore", att.confidenceScore)
                    put("createdAtSec", att.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L))
                }
                attArr.put(obj)
            }
            root.put("attendance", attArr)

            val tmpFile = File(appContext.filesDir, "guest_attendance_store.json.tmp")
            tmpFile.writeText(root.toString(), Charsets.UTF_8)
            tmpFile.renameTo(storeFile)
        }
    }

    companion object {
        const val GUEST_TEACHER_ID = "guest_local_teacher"
        private const val PREFS_NAME = "smart_attendance_session_prefs"
        private const val KEY_GUEST_ACTIVE = "guest_mode_active"

        fun isGuestSessionSaved(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_GUEST_ACTIVE, false)
        }

        fun setGuestSessionSaved(context: Context, active: Boolean) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_GUEST_ACTIVE, active)
                .apply()
        }
    }
}
