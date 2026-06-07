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

@Composable
fun OnboardingScreen(
    manager: HealthConnectManager,
    onComplete: (name: String, email: String) -> Unit,
    onLogin: () -> Unit,
) {
    // Returning user who logged out: offer to sign back into the saved account.
    if (UserStore.hasAccount) {
        WelcomeBackScreen(
            onLogin = onLogin,
            onUseDifferent = { UserStore.deleteAccount() },
        )
        return
    }

    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    val hcAvailable = manager.isAvailable

    fun finish() = onComplete(name, email)

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
        // Step indicator
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(2) { i ->
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

        if (step == 0) {
            DetailsStep(
                name = name, onName = { name = it },
                email = email, onEmail = { email = it },
                onNext = { step = 1 },
            )
        } else {
            PermissionsStep(
                hcAvailable = hcAvailable,
                onBack = { step = 0 },
                onAllow = { requestAll() },
                onSkip = { finish() },
            )
        }
    }
}

@Composable
private fun WelcomeBackScreen(
    onLogin: () -> Unit,
    onUseDifferent: () -> Unit,
) {
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

    Text("Email (optional)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
    Spacer(Modifier.height(8.dp))
    AppTextField(value = email, onValueChange = onEmail, placeholder = "you@email.com", keyboardType = KeyboardType.Email, modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(32.dp))

    val canContinue = name.isNotBlank()
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
