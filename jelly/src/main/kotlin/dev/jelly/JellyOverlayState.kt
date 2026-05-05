package dev.jelly

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.jelly.capture.CapturedElement

/**
 * Shared mutable state between the **capture overlay** (markers / modal /
 * popup / settings / review — lives in the activity's decor view) and the
 * **toolbar overlay** (the draggable FAB — lives in a separate
 * `TYPE_APPLICATION_PANEL` window, above bottom sheets).
 *
 * Splitting the overlay into two windows is what makes the FAB stay
 * clickable when a `BottomSheetDialog` (or any other `Dialog`-based UI) is
 * showing: standard dialogs use `TYPE_APPLICATION` (z=2), while
 * `TYPE_APPLICATION_PANEL` is z=1000, so the FAB sits above. Both windows
 * read from / write to this single shared state instance, so a tap on the
 * FAB still flips `annotateMode` for the capture overlay, etc.
 *
 * State is `@Volatile` per-activity (instance held in
 * [ActivityOverlayController]); on activity teardown the controller drops
 * its reference and both ComposeViews dispose.
 */
internal class JellyOverlayState {
    var annotateMode by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var reviewOpen by mutableStateOf(false)
    var capturing by mutableStateOf(false)
    var pending by mutableStateOf<PendingCapture?>(null)
    var liveHover by mutableStateOf<CapturedElement?>(null)
    var activeSessionId by mutableStateOf<String?>(null)
}
