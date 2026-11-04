package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.PhotoStorageHelper
import com.example.ui.AppScreen
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.AbsentRedDark
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.PresentGreen
import com.example.ui.theme.PresentGreenBg
import com.example.ui.theme.PresentGreenDark
import com.example.ui.theme.RoyalBlue
import com.example.ui.theme.RoyalBlueDark
import java.io.File

data class BottomNavItem(
    val screen: AppScreen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val MAIN_NAV_ITEMS = listOf(
    BottomNavItem(
        screen = AppScreen.Dashboard,
        label = "Dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_dashboard_tab"
    ),
    BottomNavItem(
        screen = AppScreen.Register,
        label = "Register",
        selectedIcon = Icons.Filled.PersonAdd,
        unselectedIcon = Icons.Outlined.PersonAdd,
        testTag = "nav_register_tab"
    ),
    BottomNavItem(
        screen = AppScreen.Recognition,
        label = "Recognition",
        selectedIcon = Icons.Filled.CenterFocusStrong,
        unselectedIcon = Icons.Outlined.CenterFocusWeak,
        testTag = "nav_recognition_tab"
    ),
    BottomNavItem(
        screen = AppScreen.Students,
        label = "Students",
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups,
        testTag = "nav_students_tab"
    ),
    BottomNavItem(
        screen = AppScreen.Reports,
        label = "Reports",
        selectedIcon = Icons.Filled.Assessment,
        unselectedIcon = Icons.Outlined.Assessment,
        testTag = "nav_reports_tab"
    )
)

@Composable
fun SchoolCrestLogo(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.12f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.38f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = "ABC High School Graduation Cap",
                tint = Color.White,
                modifier = Modifier.size(size * 0.48f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = Color(0xFF93C5FD),
                modifier = Modifier.size(size * 0.28f)
            )
        }
    }
}

@Composable
fun SchoolTopAppBar(
    title: String,
    subtitle: String = "ABC High School",
    presentCountToday: Int = 0,
    isGuestMode: Boolean = false,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onQuickNavigateRecognition: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var notifExpanded by remember { mutableStateOf(false) }

    Surface(
        color = RoyalBlue,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(RoyalBlueDark, RoyalBlue, Color(0xFF1D6BF3))
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_bar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = Color.White
                        )
                    }
                } else {
                    SchoolCrestLogo(size = 42.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.84f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Notification Bell
                Box {
                    IconButton(
                        onClick = { notifExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("top_bar_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (presentCountToday > 0) {
                                    Badge(
                                        containerColor = PresentGreen,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = presentCountToday.toString(),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Attendance Notifications",
                                tint = Color.White
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = notifExpanded,
                        onDismissRequest = { notifExpanded = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .width(240.dp)
                        ) {
                            Text(
                                text = if (isGuestMode) "Guest Local Session" else "Firestore Live Sync",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isGuestMode) {
                                    "$presentCountToday student(s) marked Present today on this device."
                                } else {
                                    "$presentCountToday student(s) marked Present today in Cloud Firestore."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Open Live Face Scanner") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.CenterFocusStrong,
                                    contentDescription = null,
                                    tint = RoyalBlue
                                )
                            },
                            onClick = {
                                notifExpanded = false
                                onQuickNavigateRecognition()
                            }
                        )
                    }
                }

                // Menu Button -> Settings & Sign Out
                if (!showBackButton) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("top_bar_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open Menu and Settings",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Settings & Firebase Status") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = null,
                                        tint = RoyalBlue
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings()
                                },
                                modifier = Modifier.testTag("menu_open_settings_item")
                            )
                            DropdownMenuItem(
                                text = { Text(if (isGuestMode) "Exit Guest / Sign In" else "Sign Out Teacher") },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = if (isGuestMode) RoyalBlue else AbsentRed
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSignOut()
                                },
                                modifier = Modifier.testTag("menu_sign_out_item")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SchoolBottomNavigationBar(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("bottom_navigation_bar")
    ) {
        MAIN_NAV_ITEMS.forEach { item ->
            val selected = currentScreen == item.screen
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectScreen(item.screen) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RoyalBlue,
                    selectedTextColor = RoyalBlue,
                    indicatorColor = RoyalBlue.copy(alpha = 0.14f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag(item.testTag)
            )
        }
    }
}

@Composable
fun SchoolSideNavigationRail(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit,
    onOpenSettings: () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = RoyalBlue,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = "ABC High School",
                        tint = Color.White
                    )
                }
            }
        }
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        MAIN_NAV_ITEMS.forEach { item ->
            val selected = currentScreen == item.screen
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectScreen(item.screen) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("rail_${item.testTag}")
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        NavigationRailItem(
            selected = currentScreen == AppScreen.Settings,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.testTag("rail_settings_button")
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Renders a student's circular avatar from `data:image/jpeg;base64,...`, file/content URI, or initials.
 */
@Composable
fun StudentAvatar(
    photoPath: String?,
    studentName: String,
    size: Dp = 52.dp,
    borderColor: Color = RoyalBlue.copy(alpha = 0.2f),
    modifier: Modifier = Modifier
) {
    val initials = remember(studentName) {
        studentName.trim()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "ST" }
    }

    val decodedBase64Bitmap = remember(photoPath) {
        if (!photoPath.isNullOrBlank() && photoPath.startsWith("data:image")) {
            PhotoStorageHelper.decodeDataUriToBitmap(photoPath)
        } else {
            null
        }
    }

    val uriOrFileModel: Any? = remember(photoPath) {
        when {
            photoPath.isNullOrBlank() || photoPath.startsWith("data:image") -> null
            photoPath.startsWith("content://") || photoPath.startsWith("file://") || photoPath.startsWith("http") -> photoPath
            else -> {
                val f = File(photoPath)
                if (f.exists()) f else null
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(RoyalBlue, AccentPurple)
                )
            )
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when {
            decodedBase64Bitmap != null -> {
                Image(
                    bitmap = decodedBase64Bitmap.asImageBitmap(),
                    contentDescription = "Photo of $studentName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            uriOrFileModel != null -> {
                AsyncImage(
                    model = uriOrFileModel,
                    contentDescription = "Photo of $studentName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Text(
                    text = initials,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun StatusPillBadge(
    isPresent: Boolean,
    label: String = if (isPresent) "Present" else "Absent",
    modifier: Modifier = Modifier
) {
    val bg = if (isPresent) PresentGreenBg else AbsentRedBg
    val fg = if (isPresent) PresentGreenDark else AbsentRedDark

    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (isPresent) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
