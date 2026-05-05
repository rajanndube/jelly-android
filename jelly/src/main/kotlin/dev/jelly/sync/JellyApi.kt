package dev.jelly.sync

import dev.jelly.model.Annotation
import dev.jelly.model.Session
import dev.jelly.model.SessionWithAnnotations
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ktor client mirroring package/src/utils/sync.ts.
 *
 * All endpoints share the same wire format as the web version since Annotation
 * is serialized with the same field names. Failures throw [JellyApiException]
 * — callers should catch and fall back to local-only behavior so a network blip
 * never breaks annotate-mode.
 */
class JellyApi(
    private val endpoint: String,
    private val client: HttpClient = defaultClient(),
) {
    suspend fun listSessions(): List<Session> =
        client.get("$endpoint/sessions").requireOk().body()

    suspend fun createSession(url: String): Session =
        client.post("$endpoint/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(url))
        }.requireOk().body()

    suspend fun getSession(sessionId: String): SessionWithAnnotations =
        client.get("$endpoint/sessions/$sessionId").requireOk().body()

    suspend fun syncAnnotation(sessionId: String, annotation: Annotation): Annotation =
        client.post("$endpoint/sessions/$sessionId/annotations") {
            contentType(ContentType.Application.Json)
            setBody(annotation)
        }.requireOk().body()

    suspend fun updateAnnotation(annotationId: String, patch: Annotation): Annotation =
        client.patch("$endpoint/annotations/$annotationId") {
            contentType(ContentType.Application.Json)
            setBody(patch)
        }.requireOk().body()

    suspend fun deleteAnnotation(annotationId: String) {
        client.delete("$endpoint/annotations/$annotationId").requireOk()
    }

    suspend fun requestAction(sessionId: String, output: String): ActionResponse =
        client.post("$endpoint/sessions/$sessionId/action") {
            contentType(ContentType.Application.Json)
            setBody(ActionRequest(output))
        }.requireOk().body()

    fun close() = client.close()

    @Serializable
    private data class CreateSessionRequest(val url: String)

    @Serializable
    private data class ActionRequest(val output: String)

    @Serializable
    data class ActionResponse(
        val success: Boolean,
        val annotationCount: Int,
        val delivered: Delivered,
    ) {
        @Serializable
        data class Delivered(
            val sseListeners: Int,
            val webhooks: Int,
            val total: Int,
        )
    }

    private fun HttpResponse.requireOk(): HttpResponse {
        if (!status.isSuccess()) {
            throw JellyApiException(status.value, "HTTP ${status.value} ${status.description}")
        }
        return this
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = false
                })
            }
        }
    }
}

class JellyApiException(val statusCode: Int, message: String) : RuntimeException(message)
