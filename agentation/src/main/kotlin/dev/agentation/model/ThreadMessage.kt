package dev.agentation.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThreadMessage(
    val id: String,
    val role: ThreadRole,
    val content: String,
    val timestamp: Long,
)

@Serializable
enum class ThreadRole {
    @SerialName("human") Human,
    @SerialName("agent") Agent,
}
