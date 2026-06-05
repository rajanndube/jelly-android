package dev.jelly.sync

import android.util.Log
import dev.jelly.storage.AnnotationStore
import java.io.File

private const val TAG = "JellySync"

/**
 * Push every locally-stored annotation to the configured endpoint, regardless
 * of whether it was already synced before. Triggered from the settings sheet's
 * "manual sync" button.
 *
 * Two real-world flows make this useful:
 *
 *  1. The user annotates before the USB cable / endpoint is connected. Those
 *     annotations have `syncedTo = null` and a manual push lands them.
 *  2. The user refreshes the jelly-local-sync page, which rotates the token
 *     to a fresh empty room. The previously-pushed annotations are still on
 *     the device with `syncedTo = <old-token-session>` set, but the new room
 *     has nothing. The user wants them on the new browser session — a manual
 *     push re-uploads everything to the new endpoint.
 *
 * The server keys annotations by `id` and idempotently overwrites, so
 * re-pushing already-synced annotations is harmless. The local `syncedTo`
 * field is rewritten to point at whatever session-id the current endpoint
 * returns.
 *
 * One [JellyApi] session is created per screen key encountered, so the
 * server-side grouping mirrors what real-time sync would have produced.
 * Each annotation's baked screenshot is uploaded best-effort after the JSON
 * sync succeeds; missing files are skipped silently (the cache may have been
 * evicted in between). Failures on individual annotations don't abort the
 * batch — the caller gets aggregate counts via [BulkSyncResult].
 */
internal suspend fun pushAllAnnotations(
    store: AnnotationStore,
    api: JellyApi,
): BulkSyncResult {
    val all = store.enumerateAll()
    var attempted = 0
    var synced = 0
    var failed = 0

    for ((screenKey, anns) in all) {
        if (anns.isEmpty()) continue

        val sid = runCatching { api.createSession(url = "screen:$screenKey").id }
            .onFailure { Log.w(TAG, "createSession failed for screen=$screenKey", it) }
            .getOrNull()
        if (sid == null) {
            // Couldn't reach the server at all — count the whole batch as failed
            // so the user sees a clear "0 of N" rather than silent skip.
            attempted += anns.size
            failed += anns.size
            continue
        }

        val updated = anns.toMutableList()
        for ((idx, a) in anns.withIndex()) {
            attempted++
            val result = runCatching {
                api.syncAnnotation(sid, a.copy(sessionId = sid)).copy(syncedTo = sid)
            }
                .onFailure { Log.w(TAG, "syncAnnotation failed for id=${a.id}", it) }
                .getOrNull()
            if (result == null) {
                failed++
                continue
            }
            updated[idx] = result
            a.screenshotPath?.let { path ->
                runCatching {
                    val f = File(path)
                    if (f.exists()) {
                        val ct = if (path.endsWith(".webp", true)) "image/webp" else "image/png"
                        api.uploadAnnotationImage(result.id, f.readBytes(), ct)
                    }
                }.onFailure { Log.w(TAG, "uploadAnnotationImage failed for id=${result.id}", it) }
            }
            synced++
        }
        store.save(screenKey, updated)
    }
    return BulkSyncResult(attempted = attempted, synced = synced, failed = failed)
}

internal data class BulkSyncResult(
    val attempted: Int,
    val synced: Int,
    val failed: Int,
) {
    val isEmpty: Boolean get() = attempted == 0
    val allSucceeded: Boolean get() = attempted > 0 && failed == 0
}
