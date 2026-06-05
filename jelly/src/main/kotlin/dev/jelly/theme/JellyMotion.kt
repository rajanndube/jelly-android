package dev.jelly.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Motion tokens — named easing curves and timing constants used across the
 * Jelly overlay UI. Centralized so the whole SDK has a coherent feel.
 *
 * These map onto the platform's standard Material easing curves rather than
 * hand-rolled beziers — same coherent feel, but the curves are the system
 * singletons (no per-token allocation, backed by the same interpolators the
 * framework uses everywhere else).
 *
 * Storyboard:
 *
 *    0ms   user presses something
 *  100ms   press feedback completes (scale 0.97)
 *  180ms   tooltip / chip entrance
 *  220ms   popup scale + fade in (anchored to trigger)
 *  320ms   bottom sheet slide up
 *
 * Stagger between cards in lists: 50ms.
 * Press feedback uses a slow press (160ms) and fast release (120ms) — slow
 * where the user is deciding, fast where the system is responding.
 */
object JellyMotion {

    /** Decelerate — for entering elements and feedback. Material's incoming curve. */
    val EaseOut: Easing = LinearOutSlowInEasing

    /** Standard ease-in-out — for elements moving across the screen. */
    val EaseInOut: Easing = FastOutSlowInEasing

    /** Drawer curve — for bottom sheets and full-screen panels. */
    val EaseDrawer: Easing = FastOutSlowInEasing

    /** Press feedback durations — slow press, fast release. */
    const val PressDownMs = 160
    const val PressUpMs = 120

    /** Common UI durations. */
    const val ChipMs = 180
    const val PopupMs = 220
    const val SheetMs = 320

    /** Stagger gap between successive list items entering. */
    const val StaggerStepMs = 50

    /** Quick spec for press-down scale. */
    fun pressDown() = tween<Float>(durationMillis = PressDownMs, easing = EaseOut)
    fun pressUp() = tween<Float>(durationMillis = PressUpMs, easing = EaseOut)
}
