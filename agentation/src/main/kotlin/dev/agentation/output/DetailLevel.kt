package dev.agentation.output

/**
 * Mirrors OutputDetailLevel in package/src/utils/generate-output.ts:7-15.
 */
enum class DetailLevel {
    /** Element + source file + comment only. */
    Compact,

    /** Adds elementPath + composable hierarchy. */
    Standard,

    /** Adds classes/testTag + position + nearby context. */
    Detailed,

    /** Adds full path, accessibility, computed styles, environment. */
    Forensic,
}
