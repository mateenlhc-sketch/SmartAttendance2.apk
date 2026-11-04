package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AttendanceRepository
import com.example.data.GuestLocalAttendanceRepository
import com.example.ui.AppScreen
import com.example.ui.AttendanceViewModel
import com.example.ui.components.SchoolBottomNavigationBar
import com.example.ui.components.SchoolSideNavigationRail
import com.example.ui.components.SchoolTopAppBar
import com.example.ui.screens.AttendanceReportsScreen
import com.example.ui.screens.AttendanceSuccessScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FaceRecognitionScreen
import com.example.ui.screens.RegisterStudentScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StudentListScreen
import com.example.ui.screens.TeacherAuthScreen
import com.example.ui.screens.signOutTeacher
import com.example.ui.theme.RoyalBlueDark
import com.example.ui.theme.SmartAttendanceTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(RoyalBlueDark.toArgb())
        )
        setContent {
            SmartAttendanceTheme {
                AuthGatedSmartAttendanceRoot()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AuthGatedSmartAttendanceRoot() {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var splashDismissed by rememberSaveable { mutableStateOf(false) }
    var guestModeActive by rememberSaveable {
        mutableStateOf(GuestLocalAttendanceRepository.isGuestSessionSaved(appContext))
    }

    val authFlow = remember {
        runCatching { Firebase.auth.authStateFlow() }.getOrNull()
    }
    val initialUser = remember {
        runCatching { Firebase.auth.currentUser }.getOrNull()
    }
    val currentUser by if (authFlow != null) {
        authFlow.collectAsStateWithLifecycle(initialValue = initialUser)
    } else {
        remember { mutableStateOf<FirebaseUser?>(null) }
    }

    // If a teacher signs in with Google, automatically promote out of Guest Mode
    LaunchedEffect(currentUser) {
        if (currentUser != null && guestModeActive) {
            GuestLocalAttendanceRepository.setGuestSessionSaved(appContext, false)
            guestModeActive = false
        }
    }

    when {
        !splashDismissed && currentUser == null && !guestModeActive -> {
            SplashScreen(
                onGetStarted = { splashDismissed = true }
            )
        }

        currentUser != null -> {
            val activeTeacher = checkNotNull(currentUser)
            val factory = remember(activeTeacher.uid) {
                viewModelFactory {
                    initializer {
                        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                            ?: appContext
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        AttendanceViewModel(
                            repository = AttendanceRepository(db),
                            currentTeacherId = activeTeacher.uid,
                            teacherDisplayName = activeTeacher.displayName ?: "Authorized Teacher",
                            teacherEmail = activeTeacher.email ?: "",
                            isGuestMode = false
                        )
                    }
                }
            }
            val viewModel: AttendanceViewModel = viewModel(
                key = "attendance_vm_${activeTeacher.uid}",
                factory = factory
            )
            AuthenticatedSmartAttendanceApp(
                viewModel = viewModel,
                onExitGuestMode = {}
            )
        }

        guestModeActive -> {
            val guestFactory = remember {
                viewModelFactory {
                    initializer {
                        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                            ?: appContext
                        AttendanceViewModel(
                            repository = GuestLocalAttendanceRepository(app),
                            currentTeacherId = GuestLocalAttendanceRepository.GUEST_TEACHER_ID,
                            teacherDisplayName = "Guest Teacher",
                            teacherEmail = "Guest Session (Local Device Storage)",
                            isGuestMode = true
                        )
                    }
                }
            }
            val guestViewModel: AttendanceViewModel = viewModel(
                key = "attendance_vm_guest_local",
                factory = guestFactory
            )
            AuthenticatedSmartAttendanceApp(
                viewModel = guestViewModel,
                onExitGuestMode = {
                    GuestLocalAttendanceRepository.setGuestSessionSaved(appContext, false)
                    guestModeActive = false
                    splashDismissed = true
                }
            )
        }

        else -> {
            TeacherAuthScreen(
                onAuthSuccess = { splashDismissed = true },
                onContinueAsGuest = {
                    GuestLocalAttendanceRepository.setGuestSessionSaved(appContext, true)
                    guestModeActive = true
                    splashDismissed = true
                }
            )
        }
    }
}

@Composable
fun AuthenticatedSmartAttendanceApp(
    viewModel: AttendanceViewModel,
    onExitGuestMode: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendance.collectAsStateWithLifecycle()
    val metrics by viewModel.dashboardMetrics.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.ensureFaceModelLoaded(context)
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val todayIso = metrics.todayDateIso
    val todayAttendanceMap = remember(allAttendance, todayIso) {
        allAttendance
            .filter { it.date == todayIso }
            .associateBy { it.studentId }
    }

    val handleSignOutOrExitGuest: () -> Unit = {
        if (viewModel.isGuestMode) {
            onExitGuestMode()
        } else {
            signOutTeacher(
                context = context,
                scope = coroutineScope,
                onSignOutComplete = {}
            )
        }
    }

    if (uiState.currentScreen != AppScreen.Dashboard &&
        uiState.currentScreen != AppScreen.Settings &&
        uiState.currentScreen != AppScreen.AttendanceSuccess
    ) {
        BackHandler {
            viewModel.navigateTo(AppScreen.Dashboard)
        }
    }

    when (uiState.currentScreen) {
        AppScreen.AttendanceSuccess -> {
            AttendanceSuccessScreen(
                payload = uiState.successPayload,
                onOkClicked = { viewModel.navigateTo(AppScreen.Dashboard) }
            )
        }

        else -> {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp
                val topBarTitle = when (uiState.currentScreen) {
                    AppScreen.Dashboard -> "AI Smart School Attendance"
                    AppScreen.Register -> "Register Student"
                    AppScreen.Recognition -> "Face Recognition Attendance"
                    AppScreen.Students -> "Student Directory"
                    AppScreen.Reports -> "Attendance Reports"
                    AppScreen.Settings -> "Settings"
                    else -> "AI Smart School Attendance"
                }
                val isSettings = uiState.currentScreen == AppScreen.Settings

                Row(modifier = Modifier.fillMaxSize()) {
                    if (isWideScreen) {
                        SchoolSideNavigationRail(
                            currentScreen = uiState.currentScreen,
                            onSelectScreen = { viewModel.navigateTo(it) },
                            onOpenSettings = { viewModel.navigateTo(AppScreen.Settings) }
                        )
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        topBar = {
                            SchoolTopAppBar(
                                title = topBarTitle,
                                subtitle = if (viewModel.isGuestMode) {
                                    "ABC High School • Guest Mode (Local)"
                                } else {
                                    "ABC High School • Cloud Sync"
                                },
                                presentCountToday = metrics.presentToday,
                                isGuestMode = viewModel.isGuestMode,
                                showBackButton = isSettings,
                                onBackClick = { viewModel.navigateBackFromSubScreen() },
                                onOpenSettings = { viewModel.navigateTo(AppScreen.Settings) },
                                onQuickNavigateRecognition = { viewModel.navigateTo(AppScreen.Recognition) },
                                onSignOut = handleSignOutOrExitGuest
                            )
                        },
                        bottomBar = {
                            if (!isWideScreen && !isSettings) {
                                SchoolBottomNavigationBar(
                                    currentScreen = uiState.currentScreen,
                                    onSelectScreen = { viewModel.navigateTo(it) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        AnimatedContent(
                            targetState = uiState.currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screen_transition",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) { targetScreen ->
                            when (targetScreen) {
                                AppScreen.Dashboard -> DashboardScreen(
                                    metrics = metrics,
                                    teacherDisplayName = viewModel.teacherDisplayName,
                                    isGuestMode = viewModel.isGuestMode,
                                    onOpenRecognition = { viewModel.navigateTo(AppScreen.Recognition) },
                                    onOpenRegister = { viewModel.navigateTo(AppScreen.Register) }
                                )

                                AppScreen.Register -> RegisterStudentScreen(
                                    viewModel = viewModel,
                                    modelLoadState = uiState.modelLoadState,
                                    isBusy = uiState.isBusy,
                                    onOpenStudentList = { viewModel.navigateTo(AppScreen.Students) }
                                )

                                AppScreen.Recognition -> FaceRecognitionScreen(
                                    uiState = uiState,
                                    students = students,
                                    viewModel = viewModel,
                                    onOpenRegister = { viewModel.navigateTo(AppScreen.Register) }
                                )

                                AppScreen.Students -> StudentListScreen(
                                    students = students,
                                    todayAttendanceMap = todayAttendanceMap,
                                    searchQuery = uiState.studentSearchQuery,
                                    selectedClassFilter = uiState.studentClassFilter,
                                    onSearchQueryChange = { viewModel.setStudentSearchQuery(it) },
                                    onClassFilterChange = { viewModel.setStudentClassFilter(it) },
                                    onEditStudent = { orig, name, roll, cls, cb ->
                                        viewModel.editStudent(orig, name, roll, cls, cb)
                                    },
                                    onDeleteStudent = { stu, retainHistory ->
                                        viewModel.deleteStudentConfirmed(stu, retainHistory)
                                    },
                                    onRemoveBiometrics = { stu ->
                                        viewModel.removeStudentBiometrics(stu)
                                    },
                                    onMarkManualPresent = { stu ->
                                        viewModel.markManualTeacherAttendance(stu)
                                    },
                                    onOpenRegister = { viewModel.navigateTo(AppScreen.Register) }
                                )

                                AppScreen.Reports -> AttendanceReportsScreen(
                                    uiState = uiState,
                                    students = students,
                                    allAttendance = allAttendance,
                                    viewModel = viewModel
                                )

                                AppScreen.Settings -> SettingsScreen(
                                    teacherDisplayName = viewModel.teacherDisplayName,
                                    teacherEmail = viewModel.teacherEmail,
                                    teacherUid = viewModel.currentTeacherId,
                                    isGuestMode = viewModel.isGuestMode,
                                    modelLoadState = uiState.modelLoadState,
                                    onBack = { viewModel.navigateBackFromSubScreen() },
                                    onSignOut = handleSignOutOrExitGuest
                                )

                                else -> DashboardScreen(
                                    metrics = metrics,
                                    teacherDisplayName = viewModel.teacherDisplayName,
                                    isGuestMode = viewModel.isGuestMode,
                                    onOpenRecognition = { viewModel.navigateTo(AppScreen.Recognition) },
                                    onOpenRegister = { viewModel.navigateTo(AppScreen.Register) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
