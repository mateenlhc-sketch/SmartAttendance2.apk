package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.PhotoStorageHelper
import com.example.data.SCHOOL_CLASSES
import com.example.data.StudentDoc
import com.example.domain.FaceDescriptorEngine
import com.example.domain.FacePoseTarget
import com.example.domain.LiveFaceAnalysis
import com.example.domain.ModelLoadState
import com.example.ui.AttendanceViewModel
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.AbsentRedDark
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.PresentGreenDark
import com.example.ui.theme.RoyalBlue
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterStudentScreen(
    viewModel: AttendanceViewModel,
    modelLoadState: ModelLoadState,
    isBusy: Boolean,
    onOpenStudentList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.ensureFaceModelLoaded(context)
    }

    var studentName by remember { mutableStateOf("") }
    var rollNumber by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(SCHOOL_CLASSES.first()) }
    var classDropdownExpanded by remember { mutableStateOf(false) }

    var optionalProfileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val capturedEmbeddings = remember { mutableStateListOf<FloatArray>() }
    var autoCapturedPortrait by remember { mutableStateOf<Bitmap?>(null) }
    var registrationError by remember { mutableStateOf<String?>(null) }

    var showMultiAngleModal by remember { mutableStateOf(false) }
    var confirmedFirestoreStudent by remember { mutableStateOf<StudentDoc?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            registrationError = null
            showMultiAngleModal = true
        } else {
            registrationError = "Camera permission is required for live 5-sample facial registration."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = PhotoStorageHelper.decodeUriToBitmap(context, uri)
            if (bmp != null) {
                optionalProfileBitmap = bmp
            } else {
                viewModel.showMessage("Could not decode selected profile image.")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Model Loading / Status Indicator Banner
            ModelStatusBanner(
                modelLoadState = modelLoadState,
                onRetry = { viewModel.ensureFaceModelLoaded(context) }
            )

            // Confirmed Firebase Firestore Registration Banner
            AnimatedVisibility(visible = confirmedFirestoreStudent != null) {
                confirmedFirestoreStudent?.let { saved ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = PresentGreenBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("registration_success_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            StudentAvatar(
                                photoPath = saved.photoBase64,
                                studentName = saved.name,
                                size = 60.dp,
                                borderColor = PresentGreen
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = PresentGreenDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Saved to Firebase Firestore!",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PresentGreenDark
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${saved.name} • Roll #${saved.rollNumber} (${saved.className})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PresentGreenDark
                                )
                                Text(
                                    text = "ID: ${saved.id} • ${saved.sampleCount} samples → ${saved.faceEmbedding.size}D MobileFaceNet vector",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PresentGreenDark.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onOpenStudentList,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("View in Student Directory")
                                }
                            }
                        }
                    }
                }
            }

            // Registration Form Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Live Student Face Registration",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enter student details, capture 5 live multi-angle facial samples (front, left, right) on-device using MobileFaceNet 192D, and save to Cloud Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Student Full Name
                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("Student Full Name") },
                        placeholder = { Text("e.g., Aarav Sharma") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_name_input")
                    )

                    // Roll Number
                    OutlinedTextField(
                        value = rollNumber,
                        onValueChange = { rollNumber = it },
                        label = { Text("Roll Number") },
                        placeholder = { Text("e.g., 601") },
                        leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_roll_input")
                    )

                    // Class Selector (Class 6, Class 7, Class 8, Class 9)
                    ExposedDropdownMenuBox(
                        expanded = classDropdownExpanded,
                        onExpandedChange = { classDropdownExpanded = !classDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedClass,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Class") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("register_class_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = classDropdownExpanded,
                            onDismissRequest = { classDropdownExpanded = false }
                        ) {
                            SCHOOL_CLASSES.forEach { schoolClass ->
                                DropdownMenuItem(
                                    text = { Text(schoolClass) },
                                    onClick = {
                                        selectedClass = schoolClass
                                        classDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Live 5-Sample Biometric Capture Box + Optional Profile Photo
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(16.dp)
                    ) {
                        val displayBmp = optionalProfileBitmap ?: autoCapturedPortrait
                        if (displayBmp != null) {
                            Image(
                                bitmap = displayBmp.asImageBitmap(),
                                contentDescription = "Student Portrait Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, PresentGreen, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(86.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBlue.copy(alpha = 0.1f))
                                    .border(2.dp, RoyalBlue.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Face,
                                    contentDescription = "Face Enrollment Icon",
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (capturedEmbeddings.size >= 5) {
                            Surface(
                                color = PresentGreenBg,
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = PresentGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "5/5 Multi-Angle Face Embeddings Ready (192D)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PresentGreenDark
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Step 1: Capture 5 live facial angles (front, slightly left, slightly right)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Required Button: START FACE REGISTRATION
                        Button(
                            onClick = {
                                if (modelLoadState !is ModelLoadState.Ready) {
                                    viewModel.showMessage("Please wait for the MobileFaceNet model to finish loading.")
                                    return@Button
                                }
                                confirmedFirestoreStudent = null
                                registrationError = null
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    showMultiAngleModal = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = modelLoadState is ModelLoadState.Ready && !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_face_registration_button")
                        ) {
                            Icon(Icons.Filled.CenterFocusStrong, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (capturedEmbeddings.size >= 5) {
                                    "RETAKE FACE REGISTRATION (5 SAMPLES)"
                                } else {
                                    "START FACE REGISTRATION"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Optional Profile Photo Picker
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("register_optional_photo_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Optional Profile Photo (Gallery)")
                        }
                    }

                    registrationError?.let { err ->
                        Surface(
                            color = AbsentRedBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = AbsentRedDark
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AbsentRedDark,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Final Save to Firebase Firestore Button
                    Button(
                        onClick = {
                            if (capturedEmbeddings.size < 5) {
                                registrationError = "Tap START FACE REGISTRATION to capture 5 live facial samples before saving."
                                return@Button
                            }
                            registrationError = null
                            viewModel.registerStudentWithLiveBiometrics(
                                name = studentName,
                                rollNumber = rollNumber,
                                className = selectedClass,
                                optionalProfileBitmap = optionalProfileBitmap,
                                capturedSampleEmbeddings = capturedEmbeddings.toList(),
                                autoCapturedFaceBitmap = autoCapturedPortrait
                            ) { savedDoc ->
                                confirmedFirestoreStudent = savedDoc
                                studentName = ""
                                rollNumber = ""
                                optionalProfileBitmap = null
                                autoCapturedPortrait = null
                                capturedEmbeddings.clear()
                            }
                        },
                        enabled = !isBusy && capturedEmbeddings.size >= 5,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PresentGreenDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("register_submit_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Saving Student & Embeddings to Firestore…")
                        } else {
                            Icon(Icons.Filled.HowToReg, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save Student to Firebase",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMultiAngleModal) {
        MultiAngleFaceCaptureModal(
            onDismiss = { showMultiAngleModal = false },
            onCompleted = { embeddings, portraitBmp ->
                capturedEmbeddings.clear()
                capturedEmbeddings.addAll(embeddings)
                autoCapturedPortrait = portraitBmp
                registrationError = null
                showMultiAngleModal = false
            },
            onFailed = { errorMsg ->
                registrationError = errorMsg
                showMultiAngleModal = false
            }
        )
    }
}

@Composable
fun ModelStatusBanner(
    modelLoadState: ModelLoadState,
    onRetry: () -> Unit
) {
    when (modelLoadState) {
        is ModelLoadState.Loading -> {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = RoyalBlue
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${modelLoadState.stage} (${modelLoadState.progressPct}%)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (modelLoadState.progressPct / 100f).coerceIn(0.05f, 1f) },
                        color = RoyalBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }

        is ModelLoadState.Ready -> {
            Surface(
                color = PresentGreenBg.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = PresentGreenDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Model Active: ${modelLoadState.modelName} • Local On-Device Inference",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PresentGreenDark
                    )
                }
            }
        }

        is ModelLoadState.Failed -> {
            Surface(
                color = AbsentRedBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = AbsentRedDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = modelLoadState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = AbsentRedDark,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry loading model", tint = AbsentRedDark)
                    }
                }
            }
        }
    }
}

/**
 * Interactive 5-Sample Multi-Angle Camera Capture Modal:
 * Guides student through Front (1/5), Steady Front (2/5), Slight Left (3/5), Slight Right (4/5),
 * and Final Center Check (5/5), generating and validating genuine 192D MobileFaceNet embeddings.
 */
@Composable
private fun MultiAngleFaceCaptureModal(
    onDismiss: () -> Unit,
    onCompleted: (List<FloatArray>, Bitmap?) -> Unit,
    onFailed: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val targets = remember { FacePoseTarget.entries }
    var currentStep by remember { mutableIntStateOf(0) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    var livePoseFeedback by remember { mutableStateOf("Position your face inside the oval guide…") }
    var poseAligned by remember { mutableStateOf(false) }
    var isExtractingSample by remember { mutableStateOf(false) }

    val collectedEmbeddings = remember { mutableStateListOf<FloatArray>() }
    var frontPortraitCrop by remember { mutableStateOf<Bitmap?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    DisposableEffect(lifecycleOwner, useFrontCamera) {
        cameraController.cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        cameraController.setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
            analyzeRegistrationPose(imageProxy) { liveFace ->
                if (currentStep < targets.size) {
                    if (liveFace == null) {
                        poseAligned = false
                        livePoseFeedback = "No face detected — look at the camera in good lighting."
                    } else {
                        val (ok, msg) = FaceDescriptorEngine.isPoseSatisfied(targets[currentStep], liveFace)
                        poseAligned = ok
                        livePoseFeedback = msg
                    }
                }
            }
        }
        try {
            cameraController.bindToLifecycle(lifecycleOwner)
        } catch (e: Exception) {
            onFailed("Unable to open Android camera: ${e.localizedMessage}")
        }
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
        }
    }

    // Automatically capture each of the 5 pose samples when pose is aligned & steady
    LaunchedEffect(poseAligned, currentStep) {
        if (poseAligned && !isExtractingSample && currentStep < targets.size) {
            delay(450L) // Brief hold to prevent motion blur
            val frameBmp = previewViewRef?.bitmap ?: return@LaunchedEffect
            isExtractingSample = true
            val extracted = FaceDescriptorEngine.detectAndExtractEmbedding(frameBmp)
            isExtractingSample = false

            if (extracted != null) {
                val (embedding, _, faceCrop) = extracted
                collectedEmbeddings.add(embedding)
                if (frontPortraitCrop == null) {
                    frontPortraitCrop = faceCrop
                }
                if (collectedEmbeddings.size >= targets.size) {
                    val validatedCentroid = FaceDescriptorEngine.aggregateMultiAngleEmbeddings(collectedEmbeddings)
                    if (validatedCentroid == null) {
                        onFailed("Captured samples did not pass consistency validation. Ensure only one student stays in frame and retry.")
                    } else {
                        onCompleted(collectedEmbeddings.toList(), frontPortraitCrop)
                    }
                } else {
                    currentStep = collectedEmbeddings.size
                    poseAligned = false
                    delay(500L) // Pause between angle steps
                }
            } else {
                livePoseFeedback = "Could not extract clear facial embedding — hold steady."
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .widthIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val activeTarget = targets.getOrElse(currentStep) { targets.last() }
                        Text(
                            text = activeTarget.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = RoyalBlue
                        )
                        Text(
                            text = activeTarget.instruction,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { useFrontCamera = !useFrontCamera }) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel face registration")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { collectedEmbeddings.size / targets.size.toFloat() },
                    color = PresentGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                controller = cameraController
                                previewViewRef = this
                            }
                        },
                        update = { pv ->
                            pv.controller = cameraController
                            previewViewRef = pv
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Face alignment oval guide
                    Box(
                        modifier = Modifier
                            .size(width = 200.dp, height = 255.dp)
                            .border(
                                width = 3.dp,
                                color = if (poseAligned) PresentGreen else Color.White.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(110.dp)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = if (poseAligned) PresentGreenBg else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = livePoseFeedback,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (poseAligned) PresentGreenDark else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Captured ${collectedEmbeddings.size} of ${targets.size} live MobileFaceNet samples locally on-device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeRegistrationPose(
    imageProxy: ImageProxy,
    onPoseResult: (LiveFaceAnalysis?) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val rotation = imageProxy.imageInfo.rotationDegrees
    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
    val effectiveW = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
    val effectiveH = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

    FaceDescriptorEngine.realtimeDetector.process(inputImage)
        .addOnSuccessListener { faces ->
            val primary = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            if (primary == null) {
                onPoseResult(null)
            } else {
                onPoseResult(
                    LiveFaceAnalysis(
                        boundingBox = primary.boundingBox,
                        frameWidth = effectiveW,
                        frameHeight = effectiveH,
                        headEulerAngleY = primary.headEulerAngleY,
                        headEulerAngleX = primary.headEulerAngleX,
                        headEulerAngleZ = primary.headEulerAngleZ,
                        faceCount = faces.size
                    )
                )
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
