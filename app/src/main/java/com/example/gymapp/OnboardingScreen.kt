package com.example.gymapp

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.example.gymapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OnboardingScreen(
    manager: HealthConnectManager,
    onComplete: (name: String, email: String, gender: String?, heightCm: Int?, weightKg: Double?, birthdayMillis: Long?) -> Unit,
    onLogin: () -> Unit,
) {
    if (UserStore.hasAccount) {
        WelcomeBackScreen(onLogin = onLogin, onUseDifferent = { UserStore.deleteAccount() })
        return
    }

    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf<Long?>(null) }

    val hcAvailable = manager.isAvailable

    fun finish() = onComplete(
        name, email, gender,
        height.toIntOrNull(), weight.toDoubleOrNull(), birthday,
    )

    val hcLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { finish() }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        if (hcAvailable) hcLauncher.launch(manager.permissions) else finish()
    }

    fun requestAll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (hcAvailable) {
            hcLauncher.launch(manager.permissions)
        } else {
            finish()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = 36.dp, bottom = 28.dp),
    ) {
        // Step indicator (3 steps)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (i <= step) AccentColor else CardElevColor),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        when (step) {
            0 -> DetailsStep(
                name = name, onName = { name = it },
                email = email, onEmail = { email = it },
                gender = gender, onGender = { gender = it },
                onNext = { step = 1 },
            )
            1 -> BodyMetricsStep(
                weight = weight, onWeight = { weight = it },
                height = height, onHeight = { height = it },
                birthday = birthday, onBirthday = { birthday = it },
                onBack = { step = 0 },
                onNext = { step = 2 },
            )
            else -> PermissionsStep(
                hcAvailable = hcAvailable,
                onBack = { step = 1 },
                onAllow = { requestAll() },
                onSkip = { finish() },
            )
        }
    }
}

@Composable
private fun WelcomeBackScreen(onLogin: () -> Unit, onUseDifferent: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = 80.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(size = 80.dp, fontSize = 34.sp)
        Spacer(Modifier.height(20.dp))
        Text("Welcome back", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Spacer(Modifier.height(6.dp))
        Text(UserStore.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
        if (UserStore.email.isNotBlank()) {
            Text(UserStore.email, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedColor)
        }
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Text("Log in as ${UserStore.displayName}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onUseDifferent, modifier = Modifier.fillMaxWidth()) {
            Text("Use a different account", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
        }
    }
}

@Composable
private fun ColumnScope.DetailsStep(
    name: String, onName: (String) -> Unit,
    email: String, onEmail: (String) -> Unit,
    gender: String?, onGender: (String?) -> Unit,
    onNext: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(Brush.linearGradient(AccentGradientColors), RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.FitnessCenter, null, tint = Color.White, modifier = Modifier.size(32.dp))
    }

    Spacer(Modifier.height(20.dp))
    Text("Welcome to GymLog", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextColor)
    Spacer(Modifier.height(8.dp))
    Text(
        "Let's set up your profile. Steps, calories and bodyweight will sync automatically from Health Connect.",
        fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = SubTextColor, lineHeight = 21.sp,
    )

    Spacer(Modifier.height(28.dp))

    Text("Your name", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
    Spacer(Modifier.height(8.dp))
    AppTextField(value = name, onValueChange = onName, placeholder = "e.g. Alex", modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(18.dp))

    Text("Email", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
    Spacer(Modifier.height(8.dp))
    AppTextField(value = email, onValueChange = onEmail, placeholder = "you@email.com", keyboardType = KeyboardType.Email, modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(18.dp))

    Text("Gender", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
    Spacer(Modifier.height(8.dp))
    GenderSelector(selected = gender, onSelect = onGender)

    Spacer(Modifier.height(32.dp))

    val canContinue = name.isNotBlank() && email.isNotBlank() && gender != null
    Button(
        onClick = onNext,
        enabled = canContinue,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentColor,
            disabledContainerColor = CardElevColor,
        ),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        Text(
            "Continue",
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = if (canContinue) Color.White else MutedColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.BodyMetricsStep(
    weight: String, onWeight: (String) -> Unit,
    height: String, onHeight: (String) -> Unit,
    birthday: Long?, onBirthday: (Long?) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    val weightD = weight.toDoubleOrNull()
    val heightI = height.toIntOrNull()
    val bmi = if (weightD != null && heightI != null && heightI > 0) {
        val m = heightI / 100.0
        weightD / (m * m)
    } else null

    Text("Body metrics", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextColor)
    Spacer(Modifier.height(8.dp))
    Text(
        "Help us personalise your experience. You can always update these later in your profile.",
        fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = SubTextColor, lineHeight = 21.sp,
    )

    Spacer(Modifier.height(28.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Weight (kg)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
            Spacer(Modifier.height(8.dp))
            AppTextField(
                value = weight,
                onValueChange = { onWeight(it.filter { c -> c.isDigit() || c == '.' }) },
                placeholder = "e.g. 75",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Height (cm)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
            Spacer(Modifier.height(8.dp))
            AppTextField(
                value = height,
                onValueChange = { onHeight(it.filter { c -> c.isDigit() }) },
                placeholder = "e.g. 175",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Live BMI card
    if (bmi != null) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(AccentColor.copy(alpha = 0.08f))
                .border(1.dp, AccentColor.copy(alpha = 0.22f), RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Speed, null, tint = AccentColor, modifier = Modifier.size(18.dp))
            Text(
                "BMI  ${String.format(Locale.US, "%.1f", bmi)}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentColor,
            )
            Text(
                "· ${bmiCategory(bmi)}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor,
            )
        }
    }

    Spacer(Modifier.height(18.dp))

    Text("Birthday", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
    Spacer(Modifier.height(8.dp))
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
            birthday?.let { dateFmt.format(Date(it)) } ?: "Select your birthday",
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = if (birthday != null) TextColor else MutedColor,
            modifier = Modifier.weight(1f),
        )
        if (birthday != null) {
            Icon(
                Icons.Rounded.Close, null, tint = MutedColor,
                modifier = Modifier.size(16.dp).clickable { onBirthday(null) },
            )
        }
    }

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onBack) {
        Text("Back", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = birthday)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { onBirthday(state.selectedDateMillis); showDatePicker = false }) {
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
private fun ColumnScope.PermissionsStep(
    hcAvailable: Boolean,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
) {
    Text("Connect your data", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextColor)
    Spacer(Modifier.height(8.dp))
    Text(
        "Grant a couple of permissions so GymLog can power your dashboard and rest timers.",
        fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = SubTextColor, lineHeight = 21.sp,
    )

    Spacer(Modifier.height(24.dp))

    PermissionRow(
        icon = Icons.Rounded.Favorite, tint = MuscleChest, title = "Health Connect",
        desc = if (hcAvailable) "Read steps, active calories and bodyweight to power your dashboard."
        else "Not installed on this device — install Health Connect later to sync health data.",
    )
    Spacer(Modifier.height(12.dp))
    PermissionRow(
        icon = Icons.Rounded.Notifications, tint = AccentColor, title = "Notifications",
        desc = "Get notified when your rest timer finishes between sets.",
    )

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onAllow,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        Text("Allow access", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
    Spacer(Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) {
            Text("Back", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
        }
        TextButton(onClick = onSkip) {
            Text("Maybe later", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
        }
    }
}

@Composable
internal fun GenderSelector(selected: String?, onSelect: (String?) -> Unit) {
    val options = listOf("Male", "Female", "Other")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val on = selected == option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) AccentColor.copy(alpha = 0.13f) else CardColor)
                    .border(1.dp, if (on) AccentColor else LineColor, RoundedCornerShape(12.dp))
                    .clickable { onSelect(if (on) null else option) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) AccentColor else SubTextColor,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, tint: Color, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LineColor, RoundedCornerShape(16.dp))
            .background(CardColor)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(tint.copy(alpha = 0.13f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Spacer(Modifier.height(3.dp))
            Text(desc, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SubTextColor, lineHeight = 18.sp)
        }
    }
}
