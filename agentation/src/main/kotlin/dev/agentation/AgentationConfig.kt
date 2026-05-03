package dev.agentation

import dev.agentation.output.DetailLevel
import dev.agentation.theme.AccentColor

data class AgentationConfig(
    val detailLevel: DetailLevel = DetailLevel.Standard,
    val accentColor: AccentColor = AccentColor.Indigo,
    val endpoint: String? = null,
    val sessionId: String? = null,
    val webhookUrl: String? = null,
    val copyToClipboard: Boolean = true,
    val captureScreenshots: Boolean = true,
    /**
     * Identifier for the current screen. Used as the storage key so each screen
     * has its own annotation list. Defaults to the host Activity class name.
     */
    val screenKey: String? = null,
)
