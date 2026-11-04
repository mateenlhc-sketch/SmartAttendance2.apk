package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AttendanceDoc
import com.example.data.SCHOOL_CLASSES
import com.example.data.StudentDoc
import com.example.ui.AppUiState
import com.example.ui.AttendanceViewModel
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.RoyalBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportsScreen(
    uiState: AppUiState,
    students: List<StudentDoc>,
    allAttendance: List<AttendanceDoc>,
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reportTabs = listOf("Daily Attendance", "Class-wise", "Historical")
    val classFilterOptions = remember { listOf("All Classes") + SCHOOL_CLASSES }

    var showDatePicker by remember { mutableStateOf(false) }
    var classDropdownExpanded by remember { mutableStateOf(false) }
    var pendingCsvContent by remember { mutableStateOf("") }

    val saveCsvDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null && pendingCsvContent.isNotEmpty()) {
            viewModel.writeCsvToUserSelectedUri(context, uri, pendingCsvContent)
        }
    }

    val selectedDate = uiState.reportSelectedDate
    val selectedClass = uiState.reportSelectedClass

    val filteredStudents by remember(students, selectedClass) {
        derivedStateOf {
            if (selectedClass == "All Classes") students
            else students.filter { it.className == selectedClass }
        }
    }

    val dateAttendanceMap by remember(allAttendance, selectedDate) {
        derivedStateOf {
            allAttendance
                .filter { it.date == selectedDate }
                .associateBy { it.studentId }
        }
    }

    val historicalRecords by remember(allAttendance, selectedClass) {
        derivedStateOf {
            allAttendance
                .filter { selectedClass == "All Classes" || it.className == selectedClass }
                .sortedWith(compareByDescending<AttendanceDoc> { it.date }.thenByDescending { it.time })
        }
    }

    val totalCount = filteredStudents.size
    val presentCount = remember(filteredStudents, dateAttendanceMap) {
        filteredStudents.count { dateAttendanceMap.containsKey(it.id) }
    }
    val absentCount = (totalCount - presentCount).coerceAtLeast(0)
    val attendancePct = if (totalCount > 0) {
        ((presentCount * 100f) / totalCount).toInt()
    } else 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
        ) {
            PrimaryTabRow(
                selectedTabIndex = uiState.reportSelectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RoyalBlue
            ) {
                reportTabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = uiState.reportSelectedTab == idx,
                        onClick = { viewModel.setReportTab(idx) },
                        text = {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        modifier = Modifier.testTag("report_tab_$idx")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reports_lazy_column"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Date Selector + Class Selector + CSV / Export Buttons Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("report_date_selector_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarMonth,
                                        contentDescription = null,
                                        tint = RoyalBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = selectedDate,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                ExposedDropdownMenuBox(
                                    expanded = classDropdownExpanded,
                                    onExpandedChange = { classDropdownExpanded = !classDropdownExpanded },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = selectedClass,
                                        onValueChange = {},
                                        readOnly = true,
                                        singleLine = true,
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                            .testTag("report_class_selector")
                                    )
                                    ExposedDropdownMenu(
                                        expanded = classDropdownExpanded,
                                        onDismissRequest = { classDropdownExpanded = false }
                                    ) {
                                        classFilterOptions.forEach { c ->
                                            DropdownMenuItem(
                                                text = { Text(c) },
                                                onClick = {
                                                    viewModel.setReportClass(c)
                                                    classDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Download CSV & Export Report Action Row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        val (fileName, csv) = viewModel.generateCurrentReportCsv()
                                        pendingCsvContent = csv
                                        saveCsvDocumentLauncher.launch(fileName)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("download_csv_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download CSV")
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.exportAndShareReport(context) { shareIntent ->
                                            context.startActivity(
                                                Intent.createChooser(
                                                    shareIntent,
                                                    "Export Attendance CSV Report"
                                                )
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("export_report_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export Report")
                                }
                            }
                        }
                    }
                }

                // Summary Strip: Total, Present, Absent, Percentage
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SummaryMetricTile(
                            label = "Total",
                            value = totalCount.toString(),
                            accent = RoyalBlue,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricTile(
                            label = "Present",
                            value = presentCount.toString(),
                            accent = PresentGreen,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricTile(
                            label = "Absent",
                            value = absentCount.toString(),
                            accent = AbsentRed,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricTile(
                            label = "Rate",
                            value = "$attendancePct%",
                            accent = AccentPurple,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Class-Wise Breakdown Card on Tab 1
                if (uiState.reportSelectedTab == 1) {
                    item {
                        ClassBreakdownStatisticsCard(
                            students = students,
                            dateAttendanceMap = dateAttendanceMap,
                            selectedDate = selectedDate
                        )
                    }
                }

                // Historical Multi-Date Firestore Log on Tab 2 vs Daily/Class Roster Table on Tabs 0 & 1
                if (uiState.reportSelectedTab == 2) {
                    item {
                        HistoricalAttendanceCard(
                            records = historicalRecords,
                            selectedClass = selectedClass
                        )
                    }
                } else {
                    item {
                        DailyRosterTableCard(
                            filteredStudents = filteredStudents,
                            dateAttendanceMap = dateAttendanceMap,
                            selectedClass = selectedClass
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            viewModel.setReportDate(sdf.format(Date(millis)))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Apply Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SummaryMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DailyRosterTableCard(
    filteredStudents: List<StudentDoc>,
    dateAttendanceMap: Map<String, AttendanceDoc>,
    selectedClass: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RoyalBlue.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Roll",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = RoyalBlue,
                    modifier = Modifier.width(52.dp)
                )
                Text(
                    text = "Student Name",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = RoyalBlue,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Time",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = RoyalBlue,
                    modifier = Modifier.width(74.dp)
                )
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = RoyalBlue,
                    modifier = Modifier.width(74.dp)
                )
            }

            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No students enrolled for $selectedClass.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                filteredStudents.forEachIndexed { idx, student ->
                    val att = dateAttendanceMap[student.id]
                    val isPresent = att != null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Text(
                            text = "#${student.rollNumber}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.width(52.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = student.className,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = att?.time ?: "--",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(74.dp)
                        )
                        Box(modifier = Modifier.width(74.dp)) {
                            StatusPillBadge(isPresent = isPresent)
                        }
                    }
                    if (idx < filteredStudents.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoricalAttendanceCard(
    records: List<AttendanceDoc>,
    selectedClass: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RoyalBlue.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Historical Firestore Check-Ins ($selectedClass)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = RoyalBlue
                )
                Text(
                    text = "${records.size} Records",
                    style = MaterialTheme.typography.labelSmall,
                    color = RoyalBlue
                )
            }

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No historical attendance records found in Cloud Firestore yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                records.forEachIndexed { idx, rec ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${rec.studentName} (#${rec.rollNumber})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${rec.className} • ${rec.verificationMethod} (${(rec.confidenceScore * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPillBadge(isPresent = true, label = rec.status)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${rec.date} • ${rec.time}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (idx < records.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassBreakdownStatisticsCard(
    students: List<StudentDoc>,
    dateAttendanceMap: Map<String, AttendanceDoc>,
    selectedDate: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Class-Wise Attendance Breakdown ($selectedDate)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            SCHOOL_CLASSES.forEach { schoolClass ->
                val classStudents = students.filter { it.className == schoolClass }
                val present = classStudents.count { dateAttendanceMap.containsKey(it.id) }
                val total = classStudents.size
                val ratio = if (total > 0) present.toFloat() / total.toFloat() else 0f
                val pct = (ratio * 100f).toInt()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = schoolClass,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "$present / $total Present ($pct%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = if (pct >= 75) PresentGreen else RoyalBlue,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }
        }
    }
}
