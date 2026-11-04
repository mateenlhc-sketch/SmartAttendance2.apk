package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AttendanceDataSource
import com.example.data.AttendanceDoc
import com.example.data.MarkAttendanceOutcome
import com.example.data.PhotoStorageHelper
import com.example.data.RegisterStudentOutcome
import com.example.data.SchoolClassDoc
import com.example.data.StudentDoc
import com.example.domain.FaceDescriptorEngine
import com.example.domain.MatchEvaluation
import com.example.domain.ModelLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

enum class AppScreen(val routeId: String) {
    Splash("splash"),
    Dashboard("dashboard"),
    Register("register"),
    Recognition("recognition"),
    Students("students"),
    Reports("reports"),
    Settings("settings"),
    AttendanceSuccess("attendance_success")
}

sealed interface StreamState<out T> {
    data object Loading : StreamState<Nothing>
    data class Success<T>(val data: T) : StreamState<T>
    data class Error(val message: String) : StreamState<Nothing>
}

sealed class RecognitionStatusState {
    data object Idle : RecognitionStatusState()
    data object AnalyzingFace : RecognitionStatusState()
    data class CooldownActive(val remainingSeconds: Int, val lastStudentName: String) : RecognitionStatusState()
    data class VerifiedStudent(
        val student: StudentDoc,
        val record: AttendanceDoc,
        val similarityPct: Int,
        val euclideanDistance: Float,
        val wasAlreadyMarkedToday: Boolean
    ) : RecognitionStatusState()
    data class UncertainIdentity(
        val closestStudentName: String,
        val similarityPct: Int,
        val requiredPct: Int
    ) : RecognitionStatusState()
    data class UnknownStudent(
        val bestSimilarityPct: Int,
        val requiredPct: Int
    ) : RecognitionStatusState()
    data class NoUsableFace(val guidance: String) : RecognitionStatusState()
}

data class AttendanceSuccessPayload(
    val student: StudentDoc,
    val record: AttendanceDoc,
    val similarityPct: Int
)

data class DashboardMetrics(
    val totalStudents: Int = 0,
    val presentToday: Int = 0,
    val absentToday: Int = 0,
    val attendancePercentage: Int = 0,
    val todayDateIso: String = "",
    val todayReadable: String = "",
    val recentRecordsToday: List<Pair<AttendanceDoc, StudentDoc?>> = emptyList()
)

data class AppUiState(
    val currentScreen: AppScreen = AppScreen.Dashboard,
    val previousScreenBeforeSubPage: AppScreen = AppScreen.Dashboard,
    val modelLoadState: ModelLoadState = ModelLoadState.Loading(0, "Preparing MobileFaceNet 192D…"),
    val snackbarMessage: String? = null,
    val recognitionClassFilter: String = "All Classes",
    val similarityThreshold: Float = FaceDescriptorEngine.DEFAULT_SIMILARITY_THRESHOLD,
    val recognitionStatus: RecognitionStatusState = RecognitionStatusState.Idle,
    val successPayload: AttendanceSuccessPayload? = null,
    val studentSearchQuery: String = "",
    val studentClassFilter: String = "All Classes",
    val reportSelectedTab: Int = 0, // 0 = Daily, 1 = Class, 2 = Historical / Statistics
    val reportSelectedDate: String = "",
    val reportSelectedClass: String = "All Classes",
    val isBusy: Boolean = false
)

class AttendanceViewModel(
    private val repository: AttendanceDataSource,
    val currentTeacherId: String,
    val teacherDisplayName: String,
    val teacherEmail: String,
    val isGuestMode: Boolean = false
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppUiState(reportSelectedDate = repository.todayDateString())
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val recognitionLock = Mutex()
    private var cooldownUntilEpochMs: Long = 0L
    private var lastRecognizedName: String = ""

    // Two-tier Firestore real-time streams scoped to authenticated teacherId
    val studentsState: StateFlow<StreamState<List<StudentDoc>>> = repository.observeStudents(currentTeacherId)
        .map<List<StudentDoc>, StreamState<List<StudentDoc>>> { StreamState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing students", err)
            emit(StreamState.Error(err.localizedMessage ?: "Failed to load students from Firestore"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamState.Loading)

    val attendanceState: StateFlow<StreamState<List<AttendanceDoc>>> = repository.observeAttendance(currentTeacherId)
        .map<List<AttendanceDoc>, StreamState<List<AttendanceDoc>>> { StreamState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing attendance", err)
            emit(StreamState.Error(err.localizedMessage ?: "Failed to load attendance from Firestore"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamState.Loading)

    val classesState: StateFlow<StreamState<List<SchoolClassDoc>>> = repository.observeClasses(currentTeacherId)
        .map<List<SchoolClassDoc>, StreamState<List<SchoolClassDoc>>> { StreamState.Success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing classes", err)
            emit(StreamState.Error(err.localizedMessage ?: "Failed to load classes"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), StreamState.Loading)

    val students: StateFlow<List<StudentDoc>> = studentsState
        .map { (it as? StreamState.Success)?.data ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allAttendance: StateFlow<List<AttendanceDoc>> = attendanceState
        .map { (it as? StreamState.Success)?.data ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(
        students,
        allAttendance
    ) { studentList, attendanceList ->
        val today = repository.todayDateString()
        val readable = repository.formattedReadableDate(today)
        val studentMap = studentList.associateBy { it.id }

        val todayRecords = attendanceList.filter { it.date == today }
        val presentEnrolledIds = todayRecords
            .map { it.studentId }
            .filter { studentMap.containsKey(it) }
            .toSet()

        val total = studentList.size
        val present = presentEnrolledIds.size
        val absent = (total - present).coerceAtLeast(0)
        val pct = if (total > 0) ((present * 100f) / total).toInt() else 0

        val enrichedRecent = todayRecords.map { att -> att to studentMap[att.studentId] }

        DashboardMetrics(
            totalStudents = total,
            presentToday = present,
            absentToday = absent,
            attendancePercentage = pct,
            todayDateIso = today,
            todayReadable = readable,
            recentRecordsToday = enrichedRecent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardMetrics())

    init {
        viewModelScope.launch {
            runCatching {
                repository.ensureTeacherAndClassesInitialized(
                    teacherId = currentTeacherId,
                    displayName = teacherDisplayName,
                    email = teacherEmail
                )
            }.onFailure { e ->
                Log.w(TAG, "Failed bootstrapping teacher/classes profile", e)
            }
        }
    }

    fun ensureFaceModelLoaded(context: Context) {
        if (_uiState.value.modelLoadState is ModelLoadState.Ready) return
        viewModelScope.launch {
            FaceDescriptorEngine.initializeModel(context.applicationContext) { progress ->
                _uiState.update { it.copy(modelLoadState = progress) }
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { state ->
            val prev = if (screen == AppScreen.Settings || screen == AppScreen.AttendanceSuccess) {
                if (state.currentScreen != AppScreen.Settings && state.currentScreen != AppScreen.AttendanceSuccess) {
                    state.currentScreen
                } else {
                    state.previousScreenBeforeSubPage
                }
            } else {
                state.previousScreenBeforeSubPage
            }
            state.copy(
                currentScreen = screen,
                previousScreenBeforeSubPage = prev
            )
        }
    }

    fun navigateBackFromSubScreen() {
        _uiState.update { state ->
            val target = when (state.currentScreen) {
                AppScreen.Settings -> state.previousScreenBeforeSubPage
                AppScreen.AttendanceSuccess -> AppScreen.Recognition
                else -> AppScreen.Dashboard
            }
            state.copy(currentScreen = target)
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    // Live Multi-Angle Student Registration -> Cloud Firestore
    fun registerStudentWithLiveBiometrics(
        name: String,
        rollNumber: String,
        className: String,
        optionalProfileBitmap: Bitmap?,
        capturedSampleEmbeddings: List<FloatArray>,
        autoCapturedFaceBitmap: Bitmap?,
        onSuccess: (StudentDoc) -> Unit
    ) {
        if (name.isBlank() || rollNumber.isBlank()) {
            showMessage("Please enter Student Full Name and Roll Number.")
            return
        }
        val centroid = FaceDescriptorEngine.aggregateMultiAngleEmbeddings(capturedSampleEmbeddings)
        if (centroid == null) {
            showMessage("Face samples were inconsistent or invalid. Please retry live face registration in clear lighting.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                val chosenBitmap = optionalProfileBitmap ?: autoCapturedFaceBitmap
                val photoDataUri = withContext(Dispatchers.Default) {
                    chosenBitmap?.let { PhotoStorageHelper.encodeThumbnailDataUri(it) }
                }

                when (
                    val outcome = repository.registerStudent(
                        name = name,
                        rollNumber = rollNumber,
                        className = className,
                        photoBase64 = photoDataUri,
                        embedding = centroid,
                        sampleCount = capturedSampleEmbeddings.size
                    )
                ) {
                    is RegisterStudentOutcome.DuplicateRollNumber -> {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                snackbarMessage = "Roll Number ${outcome.rollNumber} already exists in ${outcome.className}."
                            )
                        }
                    }

                    is RegisterStudentOutcome.InvalidBiometrics -> {
                        _uiState.update {
                            it.copy(isBusy = false, snackbarMessage = outcome.reason)
                        }
                    }

                    is RegisterStudentOutcome.Registered -> {
                        val storageLabel = if (isGuestMode) "on-device Guest storage" else "Firebase Firestore"
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                snackbarMessage = "${outcome.student.name} saved to $storageLabel with ${capturedSampleEmbeddings.size}-angle 192D embeddings!"
                            )
                        }
                        onSuccess(outcome.student)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        snackbarMessage = "Firestore registration failed: ${e.localizedMessage ?: "Check connection"}"
                    )
                }
            }
        }
    }

    // Real-Time Facial Recognition & Automatic Attendance
    fun setRecognitionClassFilter(classFilter: String) {
        _uiState.update {
            it.copy(
                recognitionClassFilter = classFilter,
                recognitionStatus = RecognitionStatusState.Idle
            )
        }
    }

    fun setSimilarityThreshold(threshold: Float) {
        _uiState.update {
            it.copy(similarityThreshold = threshold.coerceIn(0.60f, 0.90f))
        }
    }

    fun resetRecognitionStatus() {
        _uiState.update { it.copy(recognitionStatus = RecognitionStatusState.Idle) }
    }

    /**
     * Continuously or on-demand evaluates a live camera frame locally on-device (never uploading
     * camera frames to Firebase), matches against Firestore-enrolled 192D embeddings, enforces
     * post-recognition cooldown, rejects unknown/uncertain faces, and marks Present via transaction.
     */
    fun processLiveRecognitionFrame(frameBitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now < cooldownUntilEpochMs) {
            val remaining = (((cooldownUntilEpochMs - now) + 999L) / 1000L).toInt().coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    recognitionStatus = RecognitionStatusState.CooldownActive(
                        remainingSeconds = remaining,
                        lastStudentName = lastRecognizedName
                    )
                )
            }
            return
        }

        if (!recognitionLock.tryLock()) return

        viewModelScope.launch {
            try {
                val extracted = FaceDescriptorEngine.detectAndExtractEmbedding(frameBitmap)
                if (extracted == null) {
                    _uiState.update { state ->
                        if (state.recognitionStatus !is RecognitionStatusState.UnknownStudent &&
                            state.recognitionStatus !is RecognitionStatusState.VerifiedStudent
                        ) {
                            state.copy(
                                recognitionStatus = RecognitionStatusState.NoUsableFace(
                                    "Align a single clear face inside the green frame."
                                )
                            )
                        } else state
                    }
                    return@launch
                }

                val (liveEmbedding, _, _) = extracted
                val stateSnap = _uiState.value
                val threshold = stateSnap.similarityThreshold
                val classFilter = stateSnap.recognitionClassFilter

                val enrolledPool = students.value.filter { stu ->
                    stu.hasValidBiometrics &&
                        (classFilter == "All Classes" || stu.className == classFilter)
                }

                if (enrolledPool.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            recognitionStatus = RecognitionStatusState.UnknownStudent(
                                bestSimilarityPct = 0,
                                requiredPct = (threshold * 100f).toInt()
                            )
                        )
                    }
                    return@launch
                }

                val candidates = enrolledPool.map { it to it.toFloatEmbedding() }
                when (val evaluation = FaceDescriptorEngine.evaluateClosestMatch(liveEmbedding, candidates, threshold)) {
                    is MatchEvaluation.UnknownFace -> {
                        _uiState.update {
                            it.copy(
                                recognitionStatus = RecognitionStatusState.UnknownStudent(
                                    bestSimilarityPct = (evaluation.bestSimilarity * 100f).toInt(),
                                    requiredPct = (evaluation.threshold * 100f).toInt()
                                )
                            )
                        }
                    }

                    is MatchEvaluation.UncertainMatch -> {
                        _uiState.update {
                            it.copy(
                                recognitionStatus = RecognitionStatusState.UncertainIdentity(
                                    closestStudentName = evaluation.closestCandidate.name,
                                    similarityPct = (evaluation.similarity * 100f).toInt(),
                                    requiredPct = (evaluation.threshold * 100f).toInt()
                                )
                            )
                        }
                    }

                    is MatchEvaluation.VerifiedMatch -> {
                        val matchedStudent = evaluation.candidate
                        val simPct = (evaluation.similarity * 100f).toInt().coerceIn(1, 99)

                        // Apply 4.5-second post-recognition cooldown to prevent repeated scanning
                        cooldownUntilEpochMs = System.currentTimeMillis() + COOLDOWN_DURATION_MS
                        lastRecognizedName = matchedStudent.name

                        when (
                            val attOutcome = repository.markStudentPresentTransaction(
                                student = matchedStudent,
                                confidenceScore = evaluation.similarity,
                                verificationMethod = "AI Face Recognition"
                            )
                        ) {
                            is MarkAttendanceOutcome.AlreadyMarkedToday -> {
                                _uiState.update {
                                    it.copy(
                                        recognitionStatus = RecognitionStatusState.VerifiedStudent(
                                            student = matchedStudent,
                                            record = attOutcome.existing,
                                            similarityPct = simPct,
                                            euclideanDistance = evaluation.euclideanDistance,
                                            wasAlreadyMarkedToday = true
                                        ),
                                        snackbarMessage = "${matchedStudent.name} (${matchedStudent.rollNumber}) is already marked Present today at ${attOutcome.existing.time}."
                                    )
                                }
                            }

                            is MarkAttendanceOutcome.MarkedSuccess -> {
                                val payload = AttendanceSuccessPayload(
                                    student = matchedStudent,
                                    record = attOutcome.record,
                                    similarityPct = simPct
                                )
                                _uiState.update {
                                    it.copy(
                                        recognitionStatus = RecognitionStatusState.VerifiedStudent(
                                            student = matchedStudent,
                                            record = attOutcome.record,
                                            similarityPct = simPct,
                                            euclideanDistance = evaluation.euclideanDistance,
                                            wasAlreadyMarkedToday = false
                                        ),
                                        successPayload = payload,
                                        currentScreen = AppScreen.AttendanceSuccess
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during live face recognition attendance", e)
                showMessage("Recognition error: ${e.localizedMessage ?: "Please try again"}")
            } finally {
                recognitionLock.unlock()
            }
        }
    }

    /**
     * Security requirement: Face recognition is not the sole verification path —
     * authorized teachers can explicitly mark a student Present manually from Student Directory.
     */
    fun markManualTeacherAttendance(student: StudentDoc) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            try {
                when (
                    val outcome = repository.markStudentPresentTransaction(
                        student = student,
                        confidenceScore = 1.0f,
                        verificationMethod = "Teacher Manual Verification"
                    )
                ) {
                    is MarkAttendanceOutcome.AlreadyMarkedToday -> {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                snackbarMessage = "${student.name} is already marked Present today (${outcome.existing.time})."
                            )
                        }
                    }

                    is MarkAttendanceOutcome.MarkedSuccess -> {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                successPayload = AttendanceSuccessPayload(student, outcome.record, 100),
                                currentScreen = AppScreen.AttendanceSuccess
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isBusy = false, snackbarMessage = "Failed to mark manual attendance: ${e.localizedMessage}")
                }
            }
        }
    }

    // Student Directory Management
    fun setStudentSearchQuery(query: String) {
        _uiState.update { it.copy(studentSearchQuery = query) }
    }

    fun setStudentClassFilter(classFilter: String) {
        _uiState.update { it.copy(studentClassFilter = classFilter) }
    }

    fun editStudent(
        original: StudentDoc,
        newName: String,
        newRoll: String,
        newClass: String,
        onComplete: (Boolean) -> Unit
    ) {
        if (newName.isBlank() || newRoll.isBlank()) {
            showMessage("Name and Roll Number cannot be empty.")
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val res = repository.updateStudentMetadata(original, newName, newRoll, newClass)
            res.fold(
                onSuccess = {
                    showMessage("Updated ${newName.trim()} (#${newRoll.trim()}) in Firestore.")
                    onComplete(true)
                },
                onFailure = { err ->
                    showMessage(err.localizedMessage ?: "Failed to update student.")
                    onComplete(false)
                }
            )
        }
    }

    fun removeStudentBiometrics(student: StudentDoc) {
        viewModelScope.launch {
            val res = repository.clearStudentBiometrics(student)
            res.fold(
                onSuccess = {
                    showMessage("Removed stored facial biometric vectors for ${student.name}.")
                },
                onFailure = { err ->
                    showMessage("Failed to remove biometrics: ${err.localizedMessage}")
                }
            )
        }
    }

    fun deleteStudentConfirmed(student: StudentDoc, retainHistoricalAttendance: Boolean) {
        viewModelScope.launch {
            val res = repository.deleteStudent(student, retainHistoricalAttendance)
            res.fold(
                onSuccess = {
                    val suffix = if (retainHistoricalAttendance) {
                        "(historical attendance retained)"
                    } else {
                        "(including attendance history)"
                    }
                    showMessage("Deleted ${student.name} $suffix.")
                },
                onFailure = { err ->
                    showMessage("Failed to delete student: ${err.localizedMessage}")
                }
            )
        }
    }

    // Reports & CSV Export
    fun setReportTab(tabIndex: Int) {
        _uiState.update { it.copy(reportSelectedTab = tabIndex) }
    }

    fun setReportDate(dateIso: String) {
        _uiState.update { it.copy(reportSelectedDate = dateIso) }
    }

    fun setReportClass(classFilter: String) {
        _uiState.update { it.copy(reportSelectedClass = classFilter) }
    }

    fun generateCurrentReportCsv(includeAllHistory: Boolean = _uiState.value.reportSelectedTab == 2): Pair<String, String> {
        val state = _uiState.value
        val date = state.reportSelectedDate.ifBlank { repository.todayDateString() }
        val classFilter = state.reportSelectedClass
        val csv = repository.buildCsvContent(
            date = date,
            classFilter = classFilter,
            students = students.value,
            attendanceRecords = allAttendance.value,
            includeHistoricalAllDates = includeAllHistory
        )
        val safeClassSlug = classFilter.lowercase().replace(" ", "_")
        val prefix = if (includeAllHistory) "historical" else date
        val fileName = "abc_high_school_attendance_${prefix}_${safeClassSlug}.csv"
        return fileName to csv
    }

    fun writeCsvToUserSelectedUri(context: Context, uri: Uri, csvContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.applicationContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                showMessage("CSV attendance report saved successfully!")
            } catch (e: Exception) {
                showMessage("Failed to write CSV file: ${e.localizedMessage}")
            }
        }
    }

    fun exportAndShareReport(context: Context, onShareIntentReady: (Intent) -> Unit) {
        viewModelScope.launch {
            try {
                val (fileName, csv) = generateCurrentReportCsv()
                val shareIntent = repository.writeCsvToCacheAndGetShareIntent(
                    context = context.applicationContext,
                    csvContent = csv,
                    fileName = fileName
                )
                onShareIntentReady(shareIntent)
            } catch (e: Exception) {
                showMessage("Could not export report: ${e.localizedMessage}")
            }
        }
    }

    companion object {
        private const val TAG = "AttendanceVM"
        private const val COOLDOWN_DURATION_MS = 4500L
    }
}
