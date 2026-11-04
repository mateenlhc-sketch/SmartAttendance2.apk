package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AttendanceDoc
import com.example.data.StudentDoc
import com.example.ui.DashboardMetrics
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.AccentPurpleBg
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueContainer
import com.example.ui.theme.RoyalBlueDark

@Composable
fun DashboardScreen(
    metrics: DashboardMetrics,
    teacherDisplayName: String,
    isGuestMode: Boolean = false,
    onOpenRecognition: () -> Unit,
    onOpenRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("dashboard_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // School Welcome & Today's Date Banner
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = RoyalBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(RoyalBlueDark, RoyalBlue, AccentPurple)
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CalendarToday,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = metrics.todayReadable.ifBlank { "Today" },
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }

                                Surface(
                                    color = PresentGreen.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDone,
                                            contentDescription = null,
                                            tint = Color(0xFFA7F3D0),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isGuestMode) "GUEST MODE • LOCAL" else "FIRESTORE SYNC",
                                            color = Color(0xFFA7F3D0),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "ABC High School",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Welcome, ${teacherDisplayName.ifBlank { "Teacher" }} • Real-Time 192D AI Attendance",
                                color = Color.White.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onOpenRecognition,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = RoyalBlueDark
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("dashboard_scan_attendance_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CenterFocusStrong,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Live Recognition",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                OutlinedButton(
                                    onClick = onOpenRegister,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color.White.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("dashboard_register_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PersonAdd,
                                        contentDescription = "Register Student",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enroll Face")
                                }
                            }
                        }
                    }
                }
            }

            // 2x2 Statistics Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DashboardStatCard(
                            title = "Total Students",
                            value = metrics.totalStudents.toString(),
                            icon = Icons.Filled.Groups,
                            iconTint = RoyalBlue,
                            iconBg = RoyalBlueContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_card_total")
                        )
                        DashboardStatCard(
                            title = "Present Students",
                            value = metrics.presentToday.toString(),
                            icon = Icons.Filled.CheckCircle,
                            iconTint = PresentGreen,
                            iconBg = PresentGreenBg,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_card_present")
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DashboardStatCard(
                            title = "Absent Students",
                            value = metrics.absentToday.toString(),
                            icon = Icons.Filled.Cancel,
                            iconTint = AbsentRed,
                            iconBg = AbsentRedBg,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_card_absent")
                        )
                        DashboardStatCard(
                            title = "Attendance",
                            value = "${metrics.attendancePercentage}%",
                            icon = Icons.Filled.Insights,
                            iconTint = AccentPurple,
                            iconBg = AccentPurpleBg,
                            progress = (metrics.attendancePercentage / 100f).coerceIn(0f, 1f),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_card_percentage")
                        )
                    }
                }
            }

            // Recent Attendance Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column {
                        Text(
                            text = "Recent Attendance Records",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isGuestMode) {
                                "On-device Guest check-ins for ${metrics.todayDateIso}"
                            } else {
                                "Live Cloud Firestore check-ins for ${metrics.todayDateIso}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = RoyalBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${metrics.recentRecordsToday.size} Checked In",
                            color = RoyalBlue,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Recent Attendance List or Empty State
            if (metrics.recentRecordsToday.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = if (metrics.totalStudents == 0) Icons.Filled.PersonAdd else Icons.Filled.CenterFocusStrong,
                                contentDescription = null,
                                tint = RoyalBlue,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (metrics.totalStudents == 0) {
                                    "No Students Registered Yet"
                                } else {
                                    "No Attendance Marked Today"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (metrics.totalStudents == 0) {
                                    "Tap Register below to enroll students using live 5-angle camera facial capture."
                                } else {
                                    "Open Face Recognition Attendance to identify registered students in real time."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            if (metrics.totalStudents == 0) {
                                Button(
                                    onClick = onOpenRegister,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("dashboard_empty_register_btn")
                                ) {
                                    Text("Register First Student")
                                }
                            } else {
                                Button(
                                    onClick = onOpenRecognition,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Start Live Face Recognition")
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    items = metrics.recentRecordsToday,
                    key = { (record, _) -> record.id }
                ) { (record, student) ->
                    RecentAttendanceRowCard(
                        record = record,
                        student = student,
                        onClick = onOpenRecognition
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = iconTint,
                    trackColor = iconBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun RecentAttendanceRowCard(
    record: AttendanceDoc,
    student: StudentDoc?,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recent_attendance_card_${record.rollNumber}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            StudentAvatar(
                photoPath = student?.photoBase64,
                studentName = record.studentName,
                size = 50.dp,
                borderColor = PresentGreen.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.studentName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Roll #${record.rollNumber} • ${record.className} • ${(record.confidenceScore * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                StatusPillBadge(isPresent = true, label = "Present")
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = record.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
