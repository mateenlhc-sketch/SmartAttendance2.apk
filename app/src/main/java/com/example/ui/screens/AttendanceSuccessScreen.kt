package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.AttendanceSuccessPayload
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.PresentGreenDark
import com.example.ui.theme.RoyalBlue
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val xFraction: Float,
    val initialYFraction: Float,
    val speed: Float,
    val drift: Float,
    val sizeDp: Float,
    val rotationSpeed: Float,
    val color: Color
)

@Composable
fun AttendanceSuccessScreen(
    payload: AttendanceSuccessPayload?,
    onOkClicked: () -> Unit
) {
    BackHandler { onOkClicked() }

    if (payload == null) {
        LaunchedEffect(Unit) { onOkClicked() }
        return
    }

    val checkScale = remember { Animatable(0.3f) }
    val confettiProgress = remember { Animatable(0f) }

    val confettiColors = remember {
        listOf(
            RoyalBlue,
            PresentGreen,
            AccentPurple,
            Color(0xFFF59E0B),
            Color(0xFFEC4899),
            Color(0xFF06B6D4)
        )
    }

    val particles = remember {
        val rng = Random(42)
        List(56) { idx ->
            ConfettiParticle(
                xFraction = rng.nextFloat(),
                initialYFraction = -rng.nextFloat() * 0.45f,
                speed = 0.65f + rng.nextFloat() * 0.85f,
                drift = (rng.nextFloat() - 0.5f) * 0.18f,
                sizeDp = 7f + rng.nextFloat() * 7f,
                rotationSpeed = 140f + rng.nextFloat() * 240f,
                color = confettiColors[idx % confettiColors.size]
            )
        }
    }

    LaunchedEffect(payload.record.id) {
        launch {
            checkScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            confettiProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2800, easing = LinearEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = confettiProgress.value
            if (t < 1f) {
                val w = size.width
                val h = size.height
                particles.forEachIndexed { idx, p ->
                    val y = (p.initialYFraction + t * p.speed * 1.4f) * h
                    if (y in -40f..(h + 40f)) {
                        val wobble = sin((t * 9f + idx).toDouble()).toFloat() * 18.dp.toPx()
                        val x = (p.xFraction + t * p.drift) * w + wobble
                        val alpha = (1f - (t - 0.7f).coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
                        val pxSize = p.sizeDp.dp.toPx()
                        rotate(degrees = t * p.rotationSpeed, pivot = Offset(x, y)) {
                            drawRect(
                                color = p.color.copy(alpha = alpha),
                                topLeft = Offset(x - pxSize / 2f, y - pxSize / 2f),
                                size = Size(pxSize, pxSize * 0.6f)
                            )
                        }
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 500.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("attendance_success_screen")
        ) {
            Box(
                modifier = Modifier
                    .scale(checkScale.value)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(PresentGreenBg),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(PresentGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Success Checkmark",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.attendance_success_msg),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = PresentGreenDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${payload.record.verificationMethod} (${payload.similarityPct}% Match) • Saved to Cloud Firestore",
                style = MaterialTheme.typography.bodySmall,
                color = PresentGreenDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    StudentAvatar(
                        photoPath = payload.student.photoBase64,
                        studentName = payload.student.name,
                        size = 86.dp,
                        borderColor = PresentGreen
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = payload.student.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    StatusPillBadge(isPresent = true, label = "Present")

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(14.dp))

                    ConfirmationInfoRow("School", "ABC High School")
                    Spacer(modifier = Modifier.height(8.dp))
                    ConfirmationInfoRow("Roll Number", "#${payload.student.rollNumber}")
                    Spacer(modifier = Modifier.height(8.dp))
                    ConfirmationInfoRow("Class", payload.student.className)
                    Spacer(modifier = Modifier.height(8.dp))
                    ConfirmationInfoRow("Date", payload.record.date)
                    Spacer(modifier = Modifier.height(8.dp))
                    ConfirmationInfoRow("Time", payload.record.time)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onOkClicked,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("attendance_success_ok_button")
            ) {
                Text(
                    text = stringResource(R.string.btn_ok),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun ConfirmationInfoRow(
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
