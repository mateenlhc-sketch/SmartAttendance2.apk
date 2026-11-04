package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.domain.ModelLoadState
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentPurpleBg
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.PresentGreenDark
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueContainer

@Composable
fun SettingsScreen(
    teacherDisplayName: String,
    teacherEmail: String,
    teacherUid: String,
    isGuestMode: Boolean = false,
    modelLoadState: ModelLoadState,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val firebaseDiag = remember(context) { inspectFirebaseConfiguration(context) }
    var activeInfoDialog by remember { mutableStateOf<String?>(null) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Authorized Teacher Session Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(RoyalBlueContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = RoyalBlue,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = teacherDisplayName.ifBlank { if (isGuestMode) "Guest Teacher" else "Authorized Teacher" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isGuestMode) {
                                    "Guest Session • On-Device Local Storage (No Login ID)"
                                } else {
                                    teacherEmail.ifBlank { "Signed in via Google Identity" }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isGuestMode) "Mode: Local Device Storage" else "UID: ${teacherUid.take(14)}…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isGuestMode) RoyalBlue else AbsentRed
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_sign_out_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isGuestMode) "Exit Guest / Sign in with Google" else "Sign Out Teacher Account")
                    }
                }
            }

            // Firebase Cloud & AI Neural Model Configuration Status Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = if (firebaseDiag.isConfigured) PresentGreenDark else AbsentRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Firebase & AI Configuration",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Surface(
                            color = if (firebaseDiag.isConfigured) PresentGreenBg else AbsentRedBg,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (firebaseDiag.isConfigured) "CONNECTED" else "ACTION NEEDED",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (firebaseDiag.isConfigured) PresentGreenDark else AbsentRed,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    ConfigRow("Firebase Project ID", firebaseDiag.projectId)
                    ConfigRow("Firestore Database ID", firebaseDiag.databaseId.take(28) + "…")
                    ConfigRow("App ID", firebaseDiag.appId.takeLast(24))
                    ConfigRow("API Key", firebaseDiag.apiKeyMasked)
                    ConfigRow("Storage Bucket", firebaseDiag.storageBucket)
                    ConfigRow(
                        "AI Face Recognition Model",
                        when (modelLoadState) {
                            is ModelLoadState.Ready -> modelLoadState.modelName
                            is ModelLoadState.Loading -> "Loading (${modelLoadState.progressPct}%)"
                            is ModelLoadState.Failed -> "Load Error"
                        }
                    )
                }
            }

            // System & Policies Section
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "System & Biometric Governance",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp)
                    )

                    SettingsRowItem(
                        title = "Installable Android APK Guide",
                        subtitle = "How to download SmartAttendance.apk onto a physical phone",
                        icon = Icons.Filled.Android,
                        iconTint = PresentGreenDark,
                        iconBg = PresentGreenBg,
                        onClick = { activeInfoDialog = "apk" },
                        testTag = "settings_apk_guide_btn"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    SettingsRowItem(
                        title = "About AI Face Architecture",
                        subtitle = "MobileFaceNet 192D embeddings + ML Kit + Cloud Firestore",
                        icon = Icons.Filled.Memory,
                        iconTint = RoyalBlue,
                        iconBg = RoyalBlueContainer,
                        onClick = { activeInfoDialog = "about" },
                        testTag = "settings_about_btn"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    SettingsRowItem(
                        title = "Help & Teacher Walkthrough",
                        subtitle = "5-angle live enrollment, verification thresholds & manual fallback",
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        iconTint = AccentPurple,
                        iconBg = AccentPurpleBg,
                        onClick = { activeInfoDialog = "help" },
                        testTag = "settings_help_btn"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    SettingsRowItem(
                        title = "Biometric Security & Privacy Policy",
                        subtitle = "Zero live video uploads, teacher-scoped Firestore rules & vector deletion",
                        icon = Icons.Filled.PrivacyTip,
                        iconTint = PresentGreen,
                        iconBg = PresentGreenBg,
                        onClick = { activeInfoDialog = "privacy" },
                        testTag = "settings_privacy_btn"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    SettingsRowItem(
                        title = "App Version",
                        subtitle = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • ABC High School",
                        icon = Icons.Filled.Verified,
                        iconTint = RoyalBlue,
                        iconBg = RoyalBlueContainer,
                        onClick = { activeInfoDialog = "version" },
                        testTag = "settings_version_btn"
                    )
                }
            }
        }
    }

    when (activeInfoDialog) {
        "apk" -> InfoModalDialog(
            title = "How to Download SmartAttendance.apk",
            body = "Because AI Studio's Download icon exports source code as a .zip, use either of these methods to get the installable .apk:\n\n" +
                "• Method A (Phone / Browser — No PC Needed): Click the GitHub icon in AI Studio's top bar to push this project to your GitHub account. Wait ~2 minutes for the built-in 'Build Installable Android APK' workflow to finish, then open your repo's 'Releases' page (v1.0-apk) and tap SmartAttendance.apk.\n\n" +
                "• Method B (Android Studio on PC): Extract the downloaded .zip in Android Studio and select Build → Build Bundle(s) / APK(s) → Build APK(s).",
            onDismiss = { activeInfoDialog = null }
        )

        "about" -> InfoModalDialog(
            title = "AI Facial Recognition Architecture",
            body = "AI Smart School Attendance System ('Smart Attendance, Smart School') uses a genuine two-stage biometric pipeline:\n\n" +
                "1. Google ML Kit Face Detection locates faces in real time and measures 3D Euler head pose angles (Front, Slightly Left, Slightly Right).\n" +
                "2. Google LiteRT executes bundled MobileFaceNet neural weights (`mobilefacenet.tflite`) locally on-device to extract L2-normalized 192-dimensional float embeddings.\n" +
                "3. Cosine similarity and L2 Euclidean distance verify registered students against Cloud Firestore collections (`students`, `attendance`, `classes`, `teachers`).",
            onDismiss = { activeInfoDialog = null }
        )

        "help" -> InfoModalDialog(
            title = "Teacher Verification Guide",
            body = "1. Open 'Register' -> fill in Student Name, Roll Number & Class -> tap 'START FACE REGISTRATION' to capture 5 live angles.\n" +
                "2. Tap 'Save Student to Firebase' to store the 192D embedding in Cloud Firestore.\n" +
                "3. Open 'Recognition' -> tap 'Start Camera'. Registered students above the similarity threshold are automatically marked Present once per day.\n" +
                "4. Unregistered faces are rejected as 'UNKNOWN STUDENT'.\n" +
                "5. Teachers can also mark attendance manually or clear stored biometrics anytime from 'Students'.",
            onDismiss = { activeInfoDialog = null }
        )

        "privacy" -> InfoModalDialog(
            title = "Biometric Security & Privacy",
            body = "• Local Camera Processing: Live camera frames are processed strictly in memory on this Android device and are never uploaded.\n" +
                "• Restricted Access: Zero-trust Firestore security rules restrict student embeddings and attendance logs exclusively to the authenticated teacher account.\n" +
                "• Right to Erasure: Teachers can clear a student's stored 192D facial embedding at any time or delete the student while retaining historical attendance summaries.",
            onDismiss = { activeInfoDialog = null }
        )

        "version" -> InfoModalDialog(
            title = "App Version",
            body = "AI Smart School Attendance System\nABC High School\nVersion ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})\nPowered by Jetpack Compose, CameraX, ML Kit, LiteRT MobileFaceNet 192D & Firebase Cloud Firestore.",
            onDismiss = { activeInfoDialog = null }
        )
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoModalDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}
