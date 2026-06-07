package com.example.gymapp

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    hcAvailable: Boolean,
    hcGranted: Boolean,
    onManageHealth: () -> Unit,
    onLogout: () -> Unit,
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) UserStore.setPhoto(copyProfileImage(context, uri))
    }
    fun pickPhoto() = photoPicker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
        DetailTopBar(
            title = "Profile",
            onBack = onBack,
            trailing = {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(CardColor).clickable { showEditProfile = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Edit, "Edit profile", tint = TextColor, modifier = Modifier.size(18.dp))
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 20.dp),
        ) {
            // Identity
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Box(modifier = Modifier.clickable { pickPhoto() }) {
                    ProfileAvatar(size = 64.dp, fontSize = 26.sp)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(AccentColor)
                            .border(2.dp, BgColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, "Change photo", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                Column {
                    Text(UserStore.displayName, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextColor)
                    Text(UserStore.email.ifBlank { "No email added" }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SubTextColor)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Activity stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(18.dp))
                    .background(CardColor),
            ) {
                ProfileStat("${WorkoutRepository.totalWorkouts}", "Workouts", Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(54.dp).background(LineColor).align(Alignment.CenterVertically))
                ProfileStat("${WorkoutRepository.currentStreak()}", "Day streak", Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(54.dp).background(LineColor).align(Alignment.CenterVertically))
                ProfileStat("${WorkoutRepository.workoutsThisWeek()}", "This week", Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Body metrics")
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .clickable { showEditProfile = true },
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricCell(
                        icon = Icons.Rounded.MonitorWeight,
                        value = UserStore.weightKg?.let { trimWeight(it) + " kg" } ?: "Add",
                        label = "Weight",
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(1.dp).height(56.dp).background(LineColor).align(Alignment.CenterVertically))
                    MetricCell(
                        icon = Icons.Rounded.Height,
                        value = UserStore.heightCm?.let { "$it cm" } ?: "Add",
                        label = "Height",
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.width(1.dp).height(56.dp).background(LineColor).align(Alignment.CenterVertically))
                    MetricCell(
                        icon = Icons.Rounded.Speed,
                        value = UserStore.bmi?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                        label = "BMI",
                        modifier = Modifier.weight(1f),
                    )
                }
                val bmiVal = UserStore.bmi
                val ageVal = UserStore.age
                if (bmiVal != null || ageVal != null) {
                    HorizontalDivider(color = LineColor, thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            bmiVal?.let { "BMI · ${bmiCategory(it)}" } ?: "Add weight & height for BMI",
                            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                            color = if (bmiVal != null) GoodColor else MutedColor,
                        )
                        ageVal?.let { Text("Age $it", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Integrations")
            Spacer(Modifier.height(10.dp))

            // Health Connect status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(14.dp))
                    .background(CardColor)
                    .clickable { onManageHealth() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(MuscleChest.copy(alpha = 0.13f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Favorite, null, tint = MuscleChest, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Health Connect", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
                    val status = when {
                        !hcAvailable -> "Not installed"
                        hcGranted -> "Connected"
                        else -> "Tap to connect"
                    }
                    Text(status, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (hcGranted) GoodColor else MutedColor)
                }
                if (hcGranted) Box(modifier = Modifier.size(9.dp).background(GoodColor, CircleShape))
                else Icon(Icons.Rounded.ChevronRight, null, tint = MutedColor, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Preferences")
            Spacer(Modifier.height(10.dp))

            SettingRow(Icons.Rounded.Straighten, "Units", "Kilograms (kg)")
            SettingRow(Icons.Rounded.Notifications, "Notifications", "Rest + workout timer")
            SettingRow(Icons.Rounded.DarkMode, "Appearance", ThemeStore.label, onClick = { showThemeDialog = true })
            SettingRow(Icons.Rounded.Info, "About GymLog", "Version 1.0")

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MuscleChest.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .background(MuscleChest.copy(alpha = 0.08f))
                    .clickable { showLogoutConfirm = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier.size(38.dp).background(MuscleChest.copy(alpha = 0.13f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MuscleChest, modifier = Modifier.size(19.dp))
                }
                Text("Log out", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MuscleChest, modifier = Modifier.weight(1f))
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = CardElevColor,
            titleContentColor = TextColor,
            textContentColor = SubTextColor,
            title = { Text("Log out?", fontWeight = FontWeight.Bold) },
            text = { Text("You can log back into this account later. Your saved workouts stay on this device.") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onLogout() }) {
                    Text("Log out", color = MuscleChest, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = SubTextColor, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    if (showThemeDialog) {
        ThemeDialog(onDismiss = { showThemeDialog = false })
    }

    if (showEditProfile) {
        EditProfileDialog(onDismiss = { showEditProfile = false })
    }
}

@Composable
private fun ThemeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardElevColor,
        titleContentColor = TextColor,
        title = { Text("Appearance", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ThemeStore.Mode.entries.forEach { mode ->
                    val selected = ThemeStore.mode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { ThemeStore.set(mode); onDismiss() }
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (mode) {
                                ThemeStore.Mode.LIGHT -> "Light"
                                ThemeStore.Mode.DARK -> "Dark"
                                ThemeStore.Mode.SYSTEM -> "System default"
                            },
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selected) AccentColor else TextColor,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Icon(Icons.Rounded.Check, null, tint = AccentColor, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = SubTextColor, fontWeight = FontWeight.SemiBold) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(UserStore.name) }
    var email by remember { mutableStateOf(UserStore.email) }
    var height by remember { mutableStateOf(UserStore.heightCm?.toString() ?: "") }
    var weight by remember { mutableStateOf(UserStore.weightKg?.let { trimWeight(it) } ?: "") }
    var birthday by remember { mutableStateOf(UserStore.birthdayMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardElevColor,
        titleContentColor = TextColor,
        title = { Text("Edit profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(name, { name = it }, "Name", Modifier.fillMaxWidth())
                AppTextField(email, { email = it }, "Email", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' } }, "Weight (kg)", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    AppTextField(height, { height = it.filter { c -> c.isDigit() } }, "Height (cm)", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(CardColor)
                        .border(1.dp, LineColor, RoundedCornerShape(13.dp))
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 14.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Cake, null, tint = SubTextColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        birthday?.let { dateFmt.format(Date(it)) } ?: "Set birthday",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (birthday != null) TextColor else MutedColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                UserStore.updateProfile(
                    name = name,
                    email = email,
                    heightCm = height.toIntOrNull(),
                    weightKg = weight.toDoubleOrNull(),
                    birthdayMillis = birthday,
                )
                onDismiss()
            }) { Text("Save", color = AccentColor, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SubTextColor, fontWeight = FontWeight.SemiBold) }
        },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = birthday)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { birthday = state.selectedDateMillis; showDatePicker = false }) {
                    Text("OK", color = AccentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = SubTextColor, fontWeight = FontWeight.SemiBold)
                }
            },
        ) {
            DatePicker(state = state, title = null)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.4.sp)
}

@Composable
private fun MetricCell(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 14.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = AccentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
    }
}

@Composable
private fun ProfileStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, LineColor, RoundedCornerShape(14.dp))
            .background(CardColor)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(SubtleFillColor, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = SubTextColor, modifier = Modifier.size(19.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = MutedColor, modifier = Modifier.size(16.dp))
        }
    }
}

private fun trimWeight(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)

/** Copy a picked image into app storage (timestamped so the avatar re-decodes). */
private fun copyProfileImage(context: Context, uri: Uri): String? = runCatching {
    UserStore.photoPath?.let { old -> runCatching { File(old).delete() } }
    val id = UserStore.accountId.ifBlank { "default" }
    val file = File(context.filesDir, "profile_${id}_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri).use { input ->
        file.outputStream().use { output -> input!!.copyTo(output) }
    }
    file.absolutePath
}.getOrNull()
