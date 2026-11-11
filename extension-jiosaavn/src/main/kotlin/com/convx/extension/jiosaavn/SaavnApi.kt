package com.convx.extension.jiosaavn

import com.convx.extension.api.ExtensionException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * Thin transport for `https://www.jiosaavn.com/api.php`.
 *
 * Every call is a GET of `?__call=<name>&<params>`, with the fixed parameters the
 * endpoint expects. Responses are either a JSON object or a bare JSON array; a bare
 * array is wrapped as `{"results": [...]}` so callers only ever see an object.
 */
class SaavnApi(
    private val client: HttpClient = defaultClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun call(
        call: String,
        params: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonObject {
        val query = (BASE_PARAMS + params).entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        val url = "$BASE_URL?__call=$call&$query"

        val response = client.get(url) {
            (DEFAULT_HEADERS + extraHeaders).forEach { (name, value) -> header(name, value) }
        }
        if (response.status.value !in 200..299) {
            throw ExtensionException("JioSaavn $call failed: HTTP ${response.status.value}")
        }

        val body = response.bodyAsText()
        val element = runCatching { json.parseToJsonElement(body) }.getOrElse {
            throw ExtensionException("JioSaavn $call returned non-JSON", it)
        }
        return when (element) {
            is JsonObject -> element
            is JsonArray -> buildJsonObject { put("results", element) }
            else -> throw ExtensionException("JioSaavn $call returned an unexpected JSON root")
        }
    }

    companion object {
        const val BASE_URL = "https://www.jiosaavn.com/api.php"

        private val BASE_PARAMS = mapOf(
            "_format" to "json",
            "_marker" to "0",
            "api_version" to "4",
            "ctx" to "web6dot0",
        )

        private val DEFAULT_HEADERS = mapOf(
            "Accept" to "application/json",
            "Accept-Language" to "en-IN,en;q=0.9",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Cookie" to "explicit_content=1",
        )

        private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }
}
