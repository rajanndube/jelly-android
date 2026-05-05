package dev.jelly.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val url: String,
    val status: SessionStatus,
    val createdAt: String,
    val updatedAt: String? = null,
    val projectId: String? = null,
)

@Serializable
enum class SessionStatus {
    @SerialName("active") Active,
    @SerialName("approved") Approved,
    @SerialName("closed") Closed,
}

@Serializable
data class SessionWithAnnotations(
    val id: String,
    val url: String,
    val status: SessionStatus,
    val createdAt: String,
    val updatedAt: String? = null,
    val projectId: String? = null,
    val annotations: List<Annotation> = emptyList(),
)
