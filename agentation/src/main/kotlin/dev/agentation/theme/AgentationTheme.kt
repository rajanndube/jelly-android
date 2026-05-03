package dev.agentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Internal theme for all SDK overlay UI (toolbar, popup, review screen,
 * settings sheet). Always renders dark — modern devtool aesthetic
 * (Linear / Vercel / Raycast) — regardless of the host app's theme.
 *
 * Why an opinionated theme: a QA / debug toolbar is a *tool*. Users learn its
 * surface once and recognize it across every host app. A consistent dark
 * palette also keeps the toolbar visually distinct from app content so QA
 * never confuses "is this part of the app or the SDK?".
 *
 * The host app's `content()` is rendered outside this theme wrapper, so its
 * own MaterialTheme is preserved.
 */
private val AgentationDarkColors = darkColorScheme(
    background = Color(0xFF09090B),       // zinc-950 — page background
    surface = Color(0xFF18181B),          // zinc-900 — primary surface (cards, sheets)
    surfaceVariant = Color(0xFF27272A),   // zinc-800 — chips, secondary buttons
    surfaceContainerHigh = Color(0xFF27272A),
    surfaceContainerHighest = Color(0xFF3F3F46),

    onBackground = Color(0xFFFAFAFA),     // zinc-50 — primary text
    onSurface = Color(0xFFFAFAFA),
    onSurfaceVariant = Color(0xFFA1A1AA), // zinc-400 — secondary text

    outline = Color(0xFF52525B),          // zinc-600 — strong borders
    outlineVariant = Color(0xFF2E2E32),   // between zinc-800 and 700 — subtle borders

    // Primary stays light so default Material components (Switch, focused
    // borders we haven't customized) read correctly against the dark surface.
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF09090B),
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = Color(0xFFFAFAFA),

    // Error stays a calibrated red that reads on dark.
    error = Color(0xFFF87171),
    onError = Color(0xFF09090B),
)

@Composable
fun AgentationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgentationDarkColors,
        // Typography intentionally inherits Material defaults — the type ramp
        // is solid. Refine here later if specific weights/letter-spacing need
        // tuning.
        content = content,
    )
}
