package com.example.gymapp

import androidx.compose.ui.graphics.Color
import com.example.gymapp.ui.theme.*

// Models, exercise library/templates, and formatters now live in the :shared
// module (same package) so the Wear OS companion can use the same shapes.

val muscleColors: Map<String, Color> = mapOf(
    "Chest"     to MuscleChest,
    "Back"      to MuscleBack,
    "Legs"      to MuscleLegs,
    "Shoulders" to MuscleShoulders,
    "Arms"      to MuscleArms,
    "Core"      to MuscleCore,
)

fun muscleColor(group: String) = muscleColors[group] ?: SubTextColor
