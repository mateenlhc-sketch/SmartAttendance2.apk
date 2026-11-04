package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AttendanceDoc
import com.example.data.SCHOOL_CLASSES
import com.example.data.StudentDoc
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.StudentAvatar
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.PresentGreenDark
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueContainer
import com.example.ui.theme.WarningAmberBg
import com.example.ui.theme.WarningAmberDark

@Composable
fun StudentListScreen(
    students: List<StudentDoc>,
    todayAttendanceMap: Map<String, AttendanceDoc>,
    searchQuery: String,
    selectedClassFilter: String,
    onSearchQueryChange: (String) -> Unit,
    onClassFilterChange: (String) -> Unit,
    onEditStudent: (StudentDoc, String, String, String, (Boolean) -> Unit) -> Unit,
    onDeleteStudent: (StudentDoc, Boolean) -> Unit,
    onRemoveBiometrics: (StudentDoc) -> Unit,
    onMarkManualPresent: (StudentDoc) -> Unit,
    onOpenRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    val classTabs = remember { listOf("All Classes") + SCHOOL_CLASSES }

    val filteredStudents by remember(students, searchQuery, selectedClassFilter) {
        derivedStateOf {
            val q = searchQuery.trim().lowercase()
            students.filter { stu ->
                val matchesClass = selectedClassFilter == "All Classes" || stu.className == selectedClassFilter
                val matchesQuery = q.isEmpty() ||
                    stu.name.lowercase().contains(q) ||
                    stu.rollNumber.lowercase().contains(q)
                matchesClass && matchesQuery
            }
        }
    }

    var viewingStudent by remember { mutableStateOf<StudentDoc?>(null) }
    var editingStudent by remember { mutableStateOf<StudentDoc?>(null) }
    var deletingStudent by remember { mutableStateOf<StudentDoc?>(null) }
    var retainHistoricalAttendance by remember { mutableStateOf(true) }

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
            // Search & Class Filter Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search by student name or roll number…") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    classTabs.forEach { classLabel ->
                        val selected = selectedClassFilter == classLabel
                        FilterChip(
                            selected = selected,
                            onClick = { onClassFilterChange(classLabel) },
                            label = { Text(classLabel) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalBlue,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("filter_chip_${classLabel.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }

            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = null,
                                tint = RoyalBlue,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (students.isEmpty()) "No Students Registered in Firestore" else "No Matching Students",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (students.isEmpty()) {
                                    "Enroll students using live 5-angle camera facial registration to populate the directory."
                                } else {
                                    "Try clearing your search query or selecting All Classes."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onOpenRegister,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Register Student")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("student_directory_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Showing ${filteredStudents.size} of ${students.size} students in Cloud Firestore",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(
                        items = filteredStudents,
                        key = { it.id }
                    ) { student ->
                        val attToday = todayAttendanceMap[student.id]
                        StudentDirectoryCard(
                            student = student,
                            todayAttendance = attToday,
                            onViewDetails = { viewingStudent = student },
                            onEdit = { editingStudent = student },
                            onDelete = {
                                retainHistoricalAttendance = true
                                deletingStudent = student
                            }
                        )
                    }
                }
            }
        }
    }

    // View Student Details + Biometric Privacy + Manual Attendance Dialog
    viewingStudent?.let { stu ->
        // Keep live reference if updated in Firestore stream
        val latestStu = students.firstOrNull { it.id == stu.id } ?: stu
        val todayAtt = todayAttendanceMap[latestStu.id]

        AlertDialog(
            onDismissRequest = { viewingStudent = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudentAvatar(
                        photoPath = latestStu.photoBase64,
                        studentName = latestStu.name,
                        size = 56.dp,
                        borderColor = RoyalBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = latestStu.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "ABC High School • Cloud Record",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Roll Number", "#${latestStu.rollNumber}")
                    DetailRow("Class", latestStu.className)
                    DetailRow("Registration Date", latestStu.registrationDate.ifBlank { "Recorded" })
                    DetailRow(
                        "Today's Status",
                        if (todayAtt != null) "Present (${todayAtt.time})" else "Absent / Not Marked Yet"
                    )
                    DetailRow(
                        "Biometric Vectors",
                        if (latestStu.hasValidBiometrics) {
                            "${latestStu.faceEmbedding.size}D MobileFaceNet (${latestStu.sampleCount} samples)"
                        } else {
                            "Biometrics Removed"
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Manual Teacher Verification Fallback (ensures face recognition is not sole verification method)
                    if (todayAtt == null) {
                        Button(
                            onClick = {
                                viewingStudent = null
                                onMarkManualPresent(latestStu)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PresentGreenDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_mark_present_btn")
                        ) {
                            Icon(Icons.Filled.HowToReg, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mark Present Manually (Teacher)")
                        }
                    }

                    // Biometric Data Removal Control
                    if (latestStu.hasValidBiometrics) {
                        OutlinedButton(
                            onClick = {
                                onRemoveBiometrics(latestStu)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AbsentRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remove_biometrics_btn")
                        ) {
                            Icon(Icons.Filled.FaceRetouchingOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Stored Facial Biometrics")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewingStudent = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Edit Student Dialog
    editingStudent?.let { stu ->
        EditStudentDialog(
            student = stu,
            onDismiss = { editingStudent = null },
            onSave = { newName, newRoll, newClass ->
                onEditStudent(stu, newName, newRoll, newClass) { success ->
                    if (success) editingStudent = null
                }
            }
        )
    }

    // Delete Confirmation Dialog with Historical Attendance Retention Option
    deletingStudent?.let { stu ->
        AlertDialog(
            onDismissRequest = { deletingStudent = null },
            title = { Text("Delete Student Record?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Are you sure you want to delete ${stu.name} (Roll #${stu.rollNumber}, ${stu.className}) and their biometric profile from Cloud Firestore?"
                    )
                    Surface(
                        color = RoyalBlueContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { retainHistoricalAttendance = !retainHistoricalAttendance }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Checkbox(
                                checked = retainHistoricalAttendance,
                                onCheckedChange = { retainHistoricalAttendance = it },
                                modifier = Modifier.testTag("retain_history_checkbox")
                            )
                            Text(
                                text = "Retain historical attendance records in Firebase",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStudent(stu, retainHistoricalAttendance)
                        deletingStudent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed),
                    modifier = Modifier.testTag("confirm_delete_student_btn")
                ) {
                    Text("Delete Student")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingStudent = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StudentDirectoryCard(
    student: StudentDoc,
    todayAttendance: AttendanceDoc?,
    onViewDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isPresent = todayAttendance != null

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewDetails)
            .testTag("student_card_${student.rollNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StudentAvatar(
                    photoPath = student.photoBase64,
                    studentName = student.name,
                    size = 54.dp,
                    borderColor = if (isPresent) PresentGreen else RoyalBlue.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Roll #${student.rollNumber} • ${student.className}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Registered: ${student.registrationDate.ifBlank { "Cloud Synced" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (todayAttendance != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = PresentGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Checked in at ${todayAttendance.time}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PresentGreenDark
                            )
                        }
                    }
                }
                StatusPillBadge(isPresent = isPresent)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (student.hasValidBiometrics) PresentGreenBg else WarningAmberBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (student.hasValidBiometrics) Icons.Filled.VerifiedUser else Icons.Filled.FaceRetouchingOff,
                            contentDescription = null,
                            tint = if (student.hasValidBiometrics) PresentGreenDark else WarningAmberDark,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (student.hasValidBiometrics) "192D Enrolled" else "No Biometrics",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (student.hasValidBiometrics) PresentGreenDark else WarningAmberDark
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onViewDetails,
                        modifier = Modifier.testTag("view_student_btn_${student.rollNumber}")
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Details")
                    }
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_student_btn_${student.rollNumber}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = AbsentRed),
                        modifier = Modifier.testTag("delete_student_btn_${student.rollNumber}")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStudentDialog(
    student: StudentDoc,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(student.name) }
    var roll by remember { mutableStateOf(student.rollNumber) }
    var className by remember { mutableStateOf(student.className) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Student Information") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = roll,
                    onValueChange = { roll = it },
                    label = { Text("Roll Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = className,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Class") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        SCHOOL_CLASSES.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    className = c
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, roll, className) }) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
