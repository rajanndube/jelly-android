package dev.jelly

/**
 * Empty subclass of FileProvider used solely to give Jelly's content
 * provider a distinct `android:name` in the manifest. Without this, the
 * manifest merger fails when the host app already declares an
 * `androidx.core.content.FileProvider` (which most apps do for image picking
 * / sharing). Two providers with the same name in one APK is a hard error.
 *
 * Functionality identical to FileProvider — we don't override anything.
 */
class JellyFileProvider : androidx.core.content.FileProvider()
