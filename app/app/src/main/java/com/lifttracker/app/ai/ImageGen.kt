package com.lifttracker.app.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64

/** The saved picture-generation model (Settings → AI Coach). Separate from the chat providers. */
data class ImageModel(
    /** [Protocol.OPENAI] (Images API) or [Protocol.GEMINI] (image-output models). */
    val protocol: Protocol,
    val baseUrl: String,
    /** API key encrypted with the Android Keystore (see [KeyCipher]); empty if none. */
    val encryptedKey: String,
    val model: String,
) {
    val label: String get() = "${protocol.label} · $model"

    fun toJson(): String = JSONObject()
        .put("protocol", protocol.name)
        .put("baseUrl", baseUrl)
        .put("encryptedKey", encryptedKey)
        .put("model", model)
        .toString()

    companion object {
        val PROTOCOLS = listOf(Protocol.OPENAI, Protocol.GEMINI)

        fun defaultBaseUrl(protocol: Protocol): String =
            if (protocol == Protocol.GEMINI) "https://generativelanguage.googleapis.com" else "https://api.openai.com/v1"

        fun defaultModel(protocol: Protocol): String =
            if (protocol == Protocol.GEMINI) "gemini-2.5-flash-image" else "gpt-image-1"

        fun fromJson(json: String?): ImageModel? {
            if (json.isNullOrBlank()) return null
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val model = o.optString("model").ifBlank { return null }
            return ImageModel(
                protocol = PROTOCOLS.find { it.name == o.optString("protocol") } ?: Protocol.OPENAI,
                baseUrl = o.optString("baseUrl"),
                encryptedKey = o.optString("encryptedKey"),
                model = model,
            )
        }
    }
}

/** Values of the picture-model form. [apiKey] is plain text and only lives while the form is open. */
data class ImageModelDraft(
    val protocol: Protocol = Protocol.OPENAI,
    val baseUrl: String = ImageModel.defaultBaseUrl(Protocol.OPENAI),
    /** Blank means "keep the saved key", or the key of [keyFromProviderId] when that is set. */
    val apiKey: String = "",
    val hasSavedKey: Boolean = false,
    /** Chat provider whose endpoint and key were copied into this form. */
    val keyFromProviderId: String? = null,
    val model: String = ImageModel.defaultModel(Protocol.OPENAI),
)

/** Resolved, decrypted settings for one picture request. */
data class ImageConfig(val protocol: Protocol, val baseUrl: String, val apiKey: String, val model: String)

/** A generated picture. [usedTemplate] is false when the model refused the reference picture and drew from text only. */
class GeneratedImage(val bytes: ByteArray, val usedTemplate: Boolean)

/** Picture generation over the OpenAI Images API (and compatible relays) or a Gemini image model. */
object ImageClients {
    private val JSON_TYPE = "application/json".toMediaType()

    /**
     * Draws [prompt]. With [template], the picture is passed along as the style reference
     * (OpenAI: /images/edits; Gemini: an inline image part).
     */
    suspend fun generate(config: ImageConfig, prompt: String, template: ByteArray?, templateMime: String): GeneratedImage =
        withContext(Dispatchers.IO) {
            try {
                when (config.protocol) {
                    Protocol.GEMINI -> GeneratedImage(gemini(config, prompt, template, templateMime), template != null)
                    else -> openAi(config, prompt, template, templateMime)
                }
            } catch (e: LlmException) {
                throw e
            } catch (e: java.io.IOException) {
                throw NetworkException("Couldn't reach the picture model: ${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun Request.Builder.bearer(key: String) = apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }

    private fun openAi(config: ImageConfig, prompt: String, template: ByteArray?, templateMime: String): GeneratedImage {
        val base = config.baseUrl.trimSlash()
        if (template != null) {
            val ext = if (templateMime.endsWith("png")) "png" else "jpg"
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", config.model)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("size", "1024x1024")
                .addFormDataPart("image", "template.$ext", template.toRequestBody(templateMime.toMediaType()))
                .build()
            val request = Request.Builder().url("$base/images/edits").bearer(config.apiKey).post(form).build()
            LlmClients.http.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) return GeneratedImage(readOpenAiImage(resp.body?.string().orEmpty()), true)
                // Models and relays without an edit endpoint (400/404/405/422) still get a text-only try below.
                if (resp.code !in listOf(400, 404, 405, 415, 422)) throw resp.failure()
            }
        }
        val body = JsonObject().apply {
            addProperty("model", config.model)
            addProperty("prompt", prompt)
            addProperty("size", "1024x1024")
            addProperty("n", 1)
        }
        val request = Request.Builder().url("$base/images/generations").bearer(config.apiKey)
            .post(body.toString().toRequestBody(JSON_TYPE)).build()
        LlmClients.http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw resp.failure()
            return GeneratedImage(readOpenAiImage(resp.body?.string().orEmpty()), false)
        }
    }

    /** {"data":[{"b64_json":…}]} or {"data":[{"url":…}]}. */
    private fun readOpenAiImage(body: String): ByteArray {
        val root = parseJson(body)?.objOrNull() ?: throw LlmException("The picture model didn't return JSON. Check the base URL.")
        val first = root.arr("data")?.firstOrNull()?.objOrNull()
            ?: throw LlmException(root.errorMessage() ?: "The picture model returned no picture.")
        first.str("b64_json")?.takeIf { it.isNotBlank() }?.let { return decodeBase64(it) }
        val url = first.str("url")?.takeIf { it.startsWith("http") }
            ?: throw LlmException("The picture model returned no picture.")
        LlmClients.http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw LlmException("Couldn't download the generated picture (HTTP ${resp.code}).")
            return resp.body?.bytes() ?: throw LlmException("The generated picture was empty.")
        }
    }

    private fun gemini(config: ImageConfig, prompt: String, template: ByteArray?, templateMime: String): ByteArray {
        val parts = JsonArray().apply {
            add(JsonObject().apply { addProperty("text", prompt) })
            if (template != null) {
                add(JsonObject().apply {
                    add("inlineData", JsonObject().apply {
                        addProperty("mimeType", templateMime)
                        addProperty("data", Base64.getEncoder().encodeToString(template))
                    })
                })
            }
        }
        val body = JsonObject().apply {
            add("contents", JsonArray().apply { add(JsonObject().apply { addProperty("role", "user"); add("parts", parts) }) })
            add("generationConfig", JsonObject().apply {
                add("responseModalities", JsonArray().apply { add("TEXT"); add("IMAGE") })
            })
        }
        val model = config.model.removePrefix("models/")
        val request = Request.Builder()
            .url(config.baseUrl.trimSlash() + "/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        LlmClients.http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw resp.failure()
            val root = parseJson(resp.body?.string().orEmpty())?.objOrNull()
                ?: throw LlmException("The picture model didn't return JSON. Check the base URL.")
            val responseParts = root.arr("candidates")?.firstOrNull()?.objOrNull()?.obj("content")?.arr("parts")
            responseParts?.forEach { part ->
                val p = part.objOrNull() ?: return@forEach
                val data = (p.obj("inlineData") ?: p.obj("inline_data"))?.str("data")
                if (!data.isNullOrBlank()) return decodeBase64(data)
            }
            val said = responseParts?.firstNotNullOfOrNull { it.objOrNull()?.str("text") }
            throw LlmException(
                root.errorMessage() ?: said?.take(300)
                    ?: "The model returned no picture. Check that it is an image-output model.",
            )
        }
    }

    private fun decodeBase64(data: String): ByteArray =
        runCatching { Base64.getMimeDecoder().decode(data.substringAfter("base64,")) }
            .getOrElse { throw LlmException("The picture model returned unreadable picture data.") }
}
