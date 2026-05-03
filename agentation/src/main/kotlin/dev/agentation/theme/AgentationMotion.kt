package dev.agentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * Motion tokens — named easing curves and timing constants used across the
 * Agentation overlay UI. Centralized so the whole SDK has a coherent feel.
 *
 * The default Compose easings are intentionally weak. We use stronger custom
 * curves here so every transition has the punch that makes it feel intentional
 * rather than incidental.
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
object AgentationMotion {

    /** Strong ease-out — for entering elements and feedback. */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Strong ease-in-out — for elements moving across the screen. */
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** iOS-style drawer curve — for bottom sheets and full-screen panels. */
    val EaseDrawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

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
