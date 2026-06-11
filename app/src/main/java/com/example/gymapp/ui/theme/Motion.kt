package com.example.gymapp.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Shared motion vocabulary — physics springs only, no fixed-duration tweens,
 * so interrupted animations retarget smoothly instead of restarting.
 *
 * Two families plus one accent:
 *  - *spatial*: things that move or resize (slides, expansion, layout)
 *  - *effects*: non-spatial feedback (fade, tint) — critically damped and fast
 *  - *pop*: deliberate overshoot for celebratory moments (set done, PR)
 */
object Motion {
    private const val SPATIAL_DAMPING = 0.85f
    private const val SPATIAL_STIFFNESS = 400f

    val spatialOffset: SpringSpec<IntOffset> =
        spring(SPATIAL_DAMPING, SPATIAL_STIFFNESS, IntOffset.VisibilityThreshold)
    val spatialFloat: SpringSpec<Float> = spring(SPATIAL_DAMPING, SPATIAL_STIFFNESS)
    val spatialDp: SpringSpec<Dp> = spring(SPATIAL_DAMPING, SPATIAL_STIFFNESS, Dp.VisibilityThreshold)

    val effectsFloat: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 1400f)

    val popFloat: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 650f)

    // Generic factories for value types without a dedicated constant (Color, IntSize, …)
    fun <T> spatial(): SpringSpec<T> = spring(SPATIAL_DAMPING, SPATIAL_STIFFNESS)
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1400f)
    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 650f)

    /** Slow ambient drift for environmental color shifts (HR-zone tint). */
    fun <T> drift(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 40f)
}
