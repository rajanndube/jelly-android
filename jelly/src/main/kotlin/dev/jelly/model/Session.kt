package dev.jelly.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Tolerant decoder — only [id] is required. Minimal MCP servers
 * (jelly-local-sync, custom in-house implementations) often return just
 * `{id, url, createdAt}`; kotlinx.serialization will throw MissingFieldException
 * on any required field not present in the response. Treating everything else
 * as optional mirrors the iOS SDK's contract (see jelly-swift Session.swift)
 * and keeps the SDK forgiving of partial server responses. Real-time and
 * catch-up sync only need `id` to function.
 */
@Serializable
data class Session(
    val id: String,
    val url: String? = null,
    val status: SessionStatus? = null,
    val createdAt: String? = null,
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
    val url: String? = null,
    val status: SessionStatus? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val projectId: String? = null,
    val annotations: List<Annotation> = emptyList(),
)
