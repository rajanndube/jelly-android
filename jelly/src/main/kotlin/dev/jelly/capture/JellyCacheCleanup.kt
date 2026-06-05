package dev.jelly.capture

import android.content.Context
import java.io.File

/**
 * One-shot cleanup of the screenshot cache directory. The SDK writes baked
 * WebPs (and raw PNGs that haven't been baked yet) under
 * `context.cacheDir/jelly/`. Nothing tidies these up at runtime — they
 * accumulate across sessions and can grow to hundreds of MB after a few
 * weeks of QA use, especially on low-storage devices where the cache
 * partition gets reclaimed by the OS eventually but only under pressure.
 *
 * Strategy: enforce a fixed byte ceiling. On each call, list files, sort by
 * lastModified ascending (oldest first), and delete from the front until
 * total size is under the cap. Aggressive but predictable — never grows
 * past [CACHE_CAP_BYTES] regardless of how many sessions ran.
 *
 * Called from `Jelly.install` so a freshly-launched debug build trims the
 * pile before any new annotations land. Cheap: a single directory listing
 * plus N deletes, which on cached file-systems is essentially free.
 * Annotations that reference an evicted file gracefully fall back to a
 * text-only card in `AnnotationsScreen` (the LazyColumn item still renders;
 * the `BakedThumbnail` composable shows nothing when the file's gone).
 */
internal object JellyCacheCleanup {

    /**
     * Hard cap on the cache directory. Sized to comfortably hold a few
     * hundred typical screenshots without ever becoming user-visible
     * storage pressure on a 64GB device.
     */
    private const val CACHE_CAP_BYTES: Long = 100L * 1024 * 1024 // 100 MB

    /**
     * Trims `context.cacheDir/jelly/` to be at most [CACHE_CAP_BYTES] big.
     * Safe to call on the main thread — typical directory has < 100 entries
     * so the listing + sort is microsecond-scale. No-op if the directory
     * doesn't exist (first run).
     */
    fun trim(context: Context) {
        runCatching {
            val dir = File(context.cacheDir, "jelly")
            if (!dir.isDirectory) return@runCatching
            val files = dir.listFiles()?.filter { it.isFile } ?: return@runCatching
            val total = files.sumOf { it.length() }
            if (total <= CACHE_CAP_BYTES) return@runCatching
            // Oldest first.
            val sorted = files.sortedBy { it.lastModified() }
            var remaining = total
            for (f in sorted) {
                if (remaining <= CACHE_CAP_BYTES) break
                val size = f.length()
                if (f.delete()) remaining -= size
            }
        }
    }
}
