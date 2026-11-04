package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.SCHOOL_CLASSES
import com.example.data.StudentDoc
import com.example.domain.FaceDescriptorEngine
import com.example.domain.ModelLoadState
import com.example.ui.AppUiState
import com.example.ui.AttendanceViewModel
import com.example.ui.RecognitionStatusState
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.AbsentRedDark
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.PresentGreenDark
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueContainer
import com.example.ui.theme.WarningAmberBg
import com.example.ui.theme.WarningAmberDark
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRecognitionScreen(
    uiState: AppUiState,
    students: List<StudentDoc>,
    viewModel: AttendanceViewModel,
    onOpenRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.ensureFaceModelLoaded(context)
    }

    var isCameraRunning by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Live ML Kit face detection tracking state (Stage 1: Face Detection)
    var liveFaceBox by remember { mutableStateOf<Rect?>(null) }
    var liveFaceCount by remember { mutableIntStateOf(0) }
    var analysisFrameWidth by remember { mutableIntStateOf(1) }
    var analysisFrameHeight by remember { mutableIntStateOf(1) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isCameraRunning = true
        } else {
            viewModel.showMessage("Camera permission is required for live AI facial recognition.")
        }
    }

    // Bind camera only while active, and stop cleanly when leaving the screen
    DisposableEffect(isCameraRunning, useFrontCamera, lifecycleOwner) {
        if (isCameraRunning) {
            cameraController.cameraSelector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            cameraController.setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
                trackFacesRealtime(
                    imageProxy = imageProxy,
                    onFacesTracked = { primaryRect, count, imgW, imgH ->
                        liveFaceBox = primaryRect
                        liveFaceCount = count
                        analysisFrameWidth = imgW
                        analysisFrameHeight = imgH
                    }
                )
            }
            try {
                cameraController.bindToLifecycle(lifecycleOwner)
            } catch (e: Exception) {
                isCameraRunning = false
                viewModel.showMessage("Unable to open camera: ${e.localizedMessage ?: "Device busy"}")
            }
        } else {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            liveFaceBox = null
            liveFaceCount = 0
        }

        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            liveFaceBox = null
            liveFaceCount = 0
        }
    }

    // Stage 2: Continuous Real-Time MobileFaceNet 192D Embedding Extraction & Firestore Matching
    LaunchedEffect(isCameraRunning, liveFaceCount, uiState.modelLoadState) {
        if (!isCameraRunning || uiState.modelLoadState !is ModelLoadState.Ready) return@LaunchedEffect
        while (isCameraRunning) {
            delay(950L)
            if (liveFaceCount == 1) {
                val frameBmp = previewViewRef?.bitmap
                if (frameBmp != null) {
                    viewModel.processLiveRecognitionFrame(frameBmp)
                }
            }
        }
    }

    val classOptions = remember { listOf("All Classes") + SCHOOL_CLASSES }
    var classDropdownExpanded by remember { mutableStateOf(false) }

    val enrolledPoolCount = remember(students, uiState.recognitionClassFilter) {
        students.count { stu ->
            stu.hasValidBiometrics &&
                (uiState.recognitionClassFilter == "All Classes" || stu.className == uiState.recognitionClassFilter)
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
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Neural Model Loading & Readiness Card
            RecognitionModelBanner(
                modelState = uiState.modelLoadState,
                enrolledCount = enrolledPoolCount,
                onRetryLoad = { viewModel.ensureFaceModelLoaded(context) }
            )

            // Class Selection & Similarity Threshold Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = classDropdownExpanded,
                        onExpandedChange = { classDropdownExpanded = !classDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = "${uiState.recognitionClassFilter} ($enrolledPoolCount enrolled)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Class") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("recognition_class_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = classDropdownExpanded,
                            onDismissRequest = { classDropdownExpanded = false }
                        ) {
                            classOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        viewModel.setRecognitionClassFilter(option)
                                        classDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Configurable Cosine Similarity Threshold
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cosine Similarity Threshold",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Surface(
                                color = RoyalBlueContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "${(uiState.similarityThreshold * 100f).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RoyalBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Slider(
                            value = uiState.similarityThreshold,
                            onValueChange = { viewModel.setSimilarityThreshold(it) },
                            valueRange = 0.60f..0.90f,
                            colors = SliderDefaults.colors(
                                thumbColor = RoyalBlue,
                                activeTrackColor = RoyalBlue
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("similarity_threshold_slider")
                        )
                    }
                }
            }

            // Live Camera Preview + Face Detection Indicator + Controls
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF0F172A))
                            .testTag("camera_preview_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCameraRunning) {
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
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VideocamOff,
                                        contentDescription = "Camera Stopped",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Live Camera Inactive",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap Start Camera to launch continuous MobileFaceNet 192D facial embedding verification against Cloud Firestore.",
                                    color = Color.White.copy(alpha = 0.72f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        GreenFaceDetectionOverlay(
                            isCameraRunning = isCameraRunning,
                            detectedFaceRect = liveFaceBox,
                            frameWidth = analysisFrameWidth,
                            frameHeight = analysisFrameHeight,
                            isFrontCamera = useFrontCamera,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top-left Face Detection Indicator + Top-right Camera Switch
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                        ) {
                            val badgeColor = when {
                                !isCameraRunning -> Color.Black.copy(alpha = 0.58f)
                                liveFaceCount > 1 -> Color(0xFFD97706)
                                liveFaceCount == 1 -> PresentGreen.copy(alpha = 0.92f)
                                else -> RoyalBlue.copy(alpha = 0.86f)
                            }
                            val badgeLabel = when {
                                !isCameraRunning -> "CAMERA STOPPED"
                                liveFaceCount > 1 -> "MULTIPLE FACES ($liveFaceCount) • STEP SOLO"
                                liveFaceCount == 1 -> "FACE DETECTED • MATCHING 192D…"
                                else -> "NO FACE DETECTED"
                            }

                            Surface(
                                color = badgeColor,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("face_detection_indicator")
                            ) {
                                Text(
                                    text = badgeLabel,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            if (isCameraRunning) {
                                IconButton(
                                    onClick = { useFrontCamera = !useFrontCamera },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .testTag("switch_camera_lens_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Cameraswitch,
                                        contentDescription = "Switch between front and rear camera",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Start Camera / Stop Camera Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    isCameraRunning = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = !isCameraRunning && uiState.modelLoadState is ModelLoadState.Ready,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("start_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_start_camera))
                        }

                        OutlinedButton(
                            onClick = {
                                isCameraRunning = false
                                viewModel.resetRecognitionStatus()
                            },
                            enabled = isCameraRunning,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.StopCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_stop_camera))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Immediate On-Demand Verification Trigger (alongside continuous scan)
                    Button(
                        onClick = {
                            if (!isCameraRunning) {
                                viewModel.showMessage("Tap Start Camera first to verify a live face.")
                                return@Button
                            }
                            val frameBmp = previewViewRef?.bitmap
                            if (frameBmp != null) {
                                viewModel.processLiveRecognitionFrame(frameBmp)
                            } else {
                                viewModel.showMessage("Waiting for live camera frame…")
                            }
                        },
                        enabled = isCameraRunning && uiState.modelLoadState is ModelLoadState.Ready,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PresentGreenDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("scan_face_button")
                    ) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_scan_face),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Recognition Status & Verified / Unknown / Uncertain Student Panel
            RecognitionStatusAndIdCard(
                status = uiState.recognitionStatus,
                enrolledPoolCount = enrolledPoolCount,
                onOpenRegister = {
                    isCameraRunning = false
                    onOpenRegister()
                }
            )
        }
    }
}

@Composable
private fun RecognitionModelBanner(
    modelState: ModelLoadState,
    enrolledCount: Int,
    onRetryLoad: () -> Unit
) {
    when (modelState) {
        is ModelLoadState.Loading -> {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RoyalBlueContainer.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Loading AI Face Recognition Model (${modelState.progressPct}%)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = RoyalBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = modelState.stage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (modelState.progressPct / 100f).coerceIn(0.05f, 1f) },
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
                color = PresentGreenBg.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = PresentGreenDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${modelState.modelName} Ready",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = PresentGreenDark
                            )
                            Text(
                                text = "On-device 192D inference • $enrolledCount student profile(s) synced from Firestore",
                                style = MaterialTheme.typography.bodySmall,
                                color = PresentGreenDark
                            )
                        }
                    }
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
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = AbsentRedDark
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Face Model Load Failed",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AbsentRedDark
                        )
                        Text(
                            text = modelState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = AbsentRedDark
                        )
                    }
                    IconButton(onClick = onRetryLoad) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry loading model", tint = AbsentRedDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun GreenFaceDetectionOverlay(
    isCameraRunning: Boolean,
    detectedFaceRect: Rect?,
    frameWidth: Int,
    frameHeight: Int,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height

        val rectLeft: Float
        val rectTop: Float
        val rectW: Float
        val rectH: Float

        if (isCameraRunning && detectedFaceRect != null && frameWidth > 1 && frameHeight > 1) {
            val scaleX = canvasW / frameWidth.toFloat()
            val scaleY = canvasH / frameHeight.toFloat()
            val rawLeft = detectedFaceRect.left * scaleX
            val rawRight = detectedFaceRect.right * scaleX
            rectW = (rawRight - rawLeft).coerceIn(canvasW * 0.22f, canvasW * 0.82f)
            rectH = (detectedFaceRect.height() * scaleY).coerceIn(canvasH * 0.24f, canvasH * 0.82f)
            rectLeft = if (isFrontCamera) {
                (canvasW - rawRight).coerceIn(16f, canvasW - rectW - 16f)
            } else {
                rawLeft.coerceIn(16f, canvasW - rectW - 16f)
            }
            rectTop = (detectedFaceRect.top * scaleY).coerceIn(16f, canvasH - rectH - 16f)
        } else {
            rectW = canvasW * 0.56f
            rectH = canvasH * 0.64f
            rectLeft = (canvasW - rectW) / 2f
            rectTop = (canvasH - rectH) / 2f
        }

        val frameColor = if (detectedFaceRect != null && isCameraRunning) {
            Color(0xFF10B981)
        } else {
            Color(0xFF34D399).copy(alpha = 0.8f)
        }

        drawRoundRect(
            color = frameColor.copy(alpha = 0.35f),
            topLeft = Offset(rectLeft, rectTop),
            size = Size(rectW, rectH),
            cornerRadius = CornerRadius(28f, 28f),
            style = Stroke(width = 2.dp.toPx())
        )

        val bracketLen = 30.dp.toPx()
        val strokePx = 4.dp.toPx()
        val right = rectLeft + rectW
        val bottom = rectTop + rectH

        drawLine(frameColor, Offset(rectLeft, rectTop + bracketLen), Offset(rectLeft, rectTop), strokePx, StrokeCap.Round)
        drawLine(frameColor, Offset(rectLeft, rectTop), Offset(rectLeft + bracketLen, rectTop), strokePx, StrokeCap.Round)
        drawLine(frameColor, Offset(right - bracketLen, rectTop), Offset(right, rectTop), strokePx, StrokeCap.Round)
        drawLine(frameColor, Offset(right, rectTop), Offset(right, rectTop + bracketLen), strokePx, StrokeCap.Round)
        drawLine(frameColor, Offset(rectLeft, bottom - bracketLen), Offset(rectLeft, bottom), strokePx, StrokeCap.Round)
        drawLine(frameColor, Offset(rectLeft, bottom), Offset(rectLeft + bracketLen, bottom), strokePx, StrokeCap.Round)
        drawLine(frameColor, Offset(right - bracketLen, bottom), Offset(right, bottom), strokePx, StrokeCap.Round)
        drawLine(frameColor, Offset(right, bottom - bracketLen), Offset(right, bottom), strokePx, StrokeCap.Round)
    }
}

@Composable
private fun RecognitionStatusAndIdCard(
    status: RecognitionStatusState,
    enrolledPoolCount: Int,
    onOpenRegister: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recognition_status_panel")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Recognition Status & Student Verification",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            when (status) {
                is RecognitionStatusState.Idle -> {
                    Text(
                        text = if (enrolledPoolCount == 0) {
                            "No students with biometric face embeddings are registered in the selected class yet. Enroll a student first to begin recognition."
                        } else {
                            "Start the camera and align a student's face in the green frame. Face detection alone does not mark attendance — MobileFaceNet 192D must verify identity above the threshold."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (enrolledPoolCount == 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenRegister,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_open_registration))
                        }
                    }
                }

                is RecognitionStatusState.AnalyzingFace -> {
                    Text(
                        text = "Extracting 192D MobileFaceNet embedding and comparing against Firestore records…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = RoyalBlue
                    )
                }

                is RecognitionStatusState.CooldownActive -> {
                    Surface(
                        color = RoyalBlueContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HourglassTop,
                                contentDescription = null,
                                tint = RoyalBlue
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Cooldown Active (${status.remainingSeconds}s remaining)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RoyalBlue
                                )
                                Text(
                                    text = "Recently verified ${status.lastStudentName}. Pausing briefly before scanning the next student.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is RecognitionStatusState.NoUsableFace -> {
                    Surface(
                        color = WarningAmberBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = WarningAmberDark
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = status.guidance,
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmberDark
                            )
                        }
                    }
                }

                is RecognitionStatusState.UncertainIdentity -> {
                    Surface(
                        color = WarningAmberBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("uncertain_match_banner")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = WarningAmberDark,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "UNCERTAIN MATCH — NOT MARKED",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = WarningAmberDark
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Similarity (${status.similarityPct}%) is close to ${status.closestStudentName} but below the required ${status.requiredPct}% verification threshold. Improve lighting and look directly at the lens.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmberDark
                            )
                        }
                    }
                }

                is RecognitionStatusState.UnknownStudent -> {
                    Surface(
                        color = AbsentRedBg,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unknown_student_banner")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = AbsentRedDark,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.unknown_student_title),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AbsentRedDark
                                    )
                                    Text(
                                        text = stringResource(R.string.unknown_student_msg),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = AbsentRedDark
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Highest similarity score: ${status.bestSimilarityPct}% (Threshold: ${status.requiredPct}%). Attendance was NOT marked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AbsentRedDark
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenRegister,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("unknown_open_register_btn")
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_open_registration))
                            }
                        }
                    }
                }

                is RecognitionStatusState.VerifiedStudent -> {
                    AnimatedVisibility(visible = true) {
                        Surface(
                            color = PresentGreenBg.copy(alpha = 0.68f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PresentGreen, RoundedCornerShape(16.dp))
                                .testTag("student_identification_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    StudentAvatar(
                                        photoPath = status.student.photoBase64,
                                        studentName = status.student.name,
                                        size = 62.dp,
                                        borderColor = PresentGreen
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = status.student.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Roll #${status.student.rollNumber} • ${status.student.className}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val distFormatted = String.format(Locale.US, "%.2f", status.euclideanDistance)
                                        Text(
                                            text = "Cosine Match: ${status.similarityPct}% • L2 Dist: $distFormatted",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = PresentGreenDark
                                        )
                                    }
                                    StatusPillBadge(isPresent = true, label = "Present")
                                }

                                if (status.wasAlreadyMarkedToday) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = PresentGreenDark,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Duplicate prevented: Already marked Present today (${status.record.date}) at ${status.record.time}.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PresentGreenDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun trackFacesRealtime(
    imageProxy: ImageProxy,
    onFacesTracked: (Rect?, Int, Int, Int) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val rotation = imageProxy.imageInfo.rotationDegrees
    val effectiveW = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
    val effectiveH = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
    FaceDescriptorEngine.realtimeDetector.process(inputImage)
        .addOnSuccessListener { faces ->
            val largest = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            onFacesTracked(largest?.boundingBox, faces.size, effectiveW, effectiveH)
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
