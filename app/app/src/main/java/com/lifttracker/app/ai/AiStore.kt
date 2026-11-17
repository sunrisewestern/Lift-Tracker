package com.lifttracker.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Wire protocol an endpoint speaks. Most vendors are OpenAI-compatible with their own base URL. */
enum class Protocol(val label: String) {
    OPENAI("OpenAI-compatible"),
    ANTHROPIC("Anthropic (Claude)"),
    GEMINI("Google Gemini"),
}

/** Ready-made endpoint settings; everything stays editable after picking one. */
data class ProviderPreset(
    val name: String,
    val protocol: Protocol,
    val baseUrl: String,
    val defaultModel: String = "",
    val needsKey: Boolean = true,
)

val PROVIDER_PRESETS = listOf(
    ProviderPreset("Anthropic", Protocol.ANTHROPIC, "https://api.anthropic.com", "claude-opus-5-5"),
    ProviderPreset("OpenAI", Protocol.OPENAI, "https://api.openai.com/v1"),
    ProviderPreset("Google Gemini", Protocol.GEMINI, "https://generativelanguage.googleapis.com", "gemini-2.5-flash"),
    ProviderPreset("OpenRouter", Protocol.OPENAI, "https://openrouter.ai/api/v1"),
    ProviderPreset("DeepSeek", Protocol.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat"),
    ProviderPreset("Qwen (DashScope)", Protocol.OPENAI, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ProviderPreset("Moonshot / Kimi", Protocol.OPENAI, "https://api.moonshot.cn/v1"),
    ProviderPreset("Zhipu GLM", Protocol.OPENAI, "https://open.bigmodel.cn/api/paas/v4"),
    ProviderPreset("SiliconFlow", Protocol.OPENAI, "https://api.siliconflow.cn/v1"),
    ProviderPreset("Groq", Protocol.OPENAI, "https://api.groq.com/openai/v1"),
    ProviderPreset("xAI", Protocol.OPENAI, "https://api.x.ai/v1"),
    ProviderPreset("Mistral", Protocol.OPENAI, "https://api.mistral.ai/v1"),
    ProviderPreset("Ollama (LAN)", Protocol.OPENAI, "http://192.168.1.10:11434/v1", needsKey = false),
    ProviderPreset("LM Studio (LAN)", Protocol.OPENAI, "http://192.168.1.10:1234/v1", needsKey = false),
    ProviderPreset("Custom", Protocol.OPENAI, "https://"),
)

data class AiProvider(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val baseUrl: String,
    /** API key encrypted with the Android Keystore (see [KeyCipher]); empty if none. */
    val encryptedKey: String,
    val model: String,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    /** Extra system-prompt text sent with every request through this provider (optional). */
    val extraSystem: String = "",
    val thinkingLevel: ThinkingLevel = ThinkingLevel.DEFAULT,
) {
    val label: String get() = if (model.isBlank()) name else "$name · $model"

    companion object {
        const val DEFAULT_MAX_TOKENS = 16000
    }
}

data class AiChatMessage(
    val id: String,
    /** "user" or "assistant" (see [ChatTurn]). */
    val role: String,
    val text: String,
    val createdEpochMs: Long,
    /** Set on an assistant message whose reply failed or was stopped. */
    val error: String? = null,
    /** "Provider · model" that wrote an assistant message. */
    val model: String? = null,
) {
    val isUser: Boolean get() = role == ChatTurn.USER
}

/** Something the athlete picked for the coach to focus on in a chat. [title] is kept for display. */
data class AiContextItem(val kind: String, val id: String, val title: String) {
    val kindLabel: String
        get() = when (kind) {
            ROUTINE -> "Routine"
            EXERCISE -> "Exercise"
            else -> "Workout"
        }

    companion object {
        const val ROUTINE = "routine"
        const val EXERCISE = "exercise"
        const val WORKOUT = "workout"
    }
}

data class AiChat(
    val id: String,
    val title: String,
    val createdEpochMs: Long,
    val updatedEpochMs: Long,
    /** Adds the training summary to the system prompt of every message in this chat. */
    val includeTraining: Boolean = true,
    /** Extra context fixed to this chat, e.g. the workout an analysis was about. */
    val pinnedContext: String = "",
    /** Routines, exercises and workouts selected as context; read fresh from the app on every message. */
    val contextItems: List<AiContextItem> = emptyList(),
    val messages: List<AiChatMessage> = emptyList(),
)

/** A saved AI analysis of one logged workout, keyed by the workout's id. */
data class AiAnalysis(
    val sessionId: String,
    val createdEpochMs: Long,
    val model: String,
    val output: String,
    val error: String? = null,
)

/** JSON encoding of the AI settings and history; pure so it can be unit-tested. */
object AiJson {
    fun encodeProviders(providers: List<AiProvider>): String = JSONArray().apply {
        providers.forEach { p ->
            put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("protocol", p.protocol.name)
                    .put("baseUrl", p.baseUrl)
                    .put("encryptedKey", p.encryptedKey)
                    .put("model", p.model)
                    .put("maxTokens", p.maxTokens)
                    .put("extraSystem", p.extraSystem)
                    .put("thinkingLevel", p.thinkingLevel.name),
            )
        }
    }.toString()

    fun decodeProviders(json: String?): List<AiProvider> = objects(json).mapNotNull { o ->
        val id = o.optString("id").ifBlank { return@mapNotNull null }
        AiProvider(
            id = id,
            name = o.optString("name"),
            protocol = Protocol.entries.find { it.name == o.optString("protocol") } ?: Protocol.OPENAI,
            baseUrl = o.optString("baseUrl"),
            encryptedKey = o.optString("encryptedKey"),
            model = o.optString("model"),
            maxTokens = o.optInt("maxTokens", AiProvider.DEFAULT_MAX_TOKENS),
            extraSystem = o.optString("extraSystem"),
            thinkingLevel = ThinkingLevel.entries.find { it.name == o.optString("thinkingLevel") } ?: ThinkingLevel.DEFAULT,
        )
    }

    fun encodeChats(chats: List<AiChat>): String = JSONArray().apply {
        chats.forEach { c ->
            put(
                JSONObject()
                    .put("id", c.id)
                    .put("title", c.title)
                    .put("created", c.createdEpochMs)
                    .put("updated", c.updatedEpochMs)
                    .put("includeTraining", c.includeTraining)
                    .put("pinnedContext", c.pinnedContext)
                    .put("context", JSONArray().apply {
                        c.contextItems.forEach { put(JSONObject().put("kind", it.kind).put("id", it.id).put("title", it.title)) }
                    })
                    .put("messages", JSONArray().apply {
                        c.messages.forEach { m ->
                            put(
                                JSONObject()
                                    .put("id", m.id)
                                    .put("role", m.role)
                                    .put("text", m.text)
                                    .put("created", m.createdEpochMs)
                                    .putOpt("error", m.error)
                                    .putOpt("model", m.model),
                            )
                        }
                    }),
            )
        }
    }.toString()

    fun decodeChats(json: String?): List<AiChat> = objects(json).mapNotNull { o ->
        val id = o.optString("id").ifBlank { return@mapNotNull null }
        val msgs = o.optJSONArray("messages") ?: JSONArray()
        AiChat(
            id = id,
            title = o.optString("title"),
            createdEpochMs = o.optLong("created"),
            updatedEpochMs = o.optLong("updated"),
            includeTraining = o.optBoolean("includeTraining", true),
            pinnedContext = o.optString("pinnedContext"),
            contextItems = (o.optJSONArray("context") ?: JSONArray()).let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val c = arr.optJSONObject(i) ?: return@mapNotNull null
                    AiContextItem(c.optString("kind"), c.optString("id"), c.optString("title"))
                }
            },
            messages = (0 until msgs.length()).mapNotNull { i ->
                val m = msgs.optJSONObject(i) ?: return@mapNotNull null
                AiChatMessage(
                    id = m.optString("id"),
                    role = m.optString("role", ChatTurn.USER),
                    text = m.optString("text"),
                    createdEpochMs = m.optLong("created"),
                    error = m.stringOrNull("error"),
                    model = m.stringOrNull("model"),
                )
            },
        )
    }

    fun encodeAnalyses(analyses: Collection<AiAnalysis>): String = JSONArray().apply {
        analyses.forEach { a ->
            put(
                JSONObject()
                    .put("sessionId", a.sessionId)
                    .put("created", a.createdEpochMs)
                    .put("model", a.model)
                    .put("output", a.output)
                    .putOpt("error", a.error),
            )
        }
    }.toString()

    fun decodeAnalyses(json: String?): List<AiAnalysis> = objects(json).mapNotNull { o ->
        val sessionId = o.optString("sessionId").ifBlank { return@mapNotNull null }
        AiAnalysis(
            sessionId = sessionId,
            createdEpochMs = o.optLong("created"),
            model = o.optString("model"),
            output = o.optString("output"),
            error = o.stringOrNull("error"),
        )
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private fun objects(json: String?): List<JSONObject> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }
}

/**
 * AI settings and history, in their own preferences file so they never mix with workout data.
 * API keys are stored encrypted (see [KeyCipher]).
 */
class AiStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadProviders(): List<AiProvider> = AiJson.decodeProviders(prefs.getString("providers", null))
    fun saveProviders(providers: List<AiProvider>) =
        prefs.edit().putString("providers", AiJson.encodeProviders(providers)).apply()

    var activeProviderId: String?
        get() = prefs.getString("active_provider", null)
        set(value) = prefs.edit().putString("active_provider", value).apply()

    var imageModelJson: String?
        get() = prefs.getString("image_model", null)
        set(value) = prefs.edit().apply { if (value == null) remove("image_model") else putString("image_model", value) }.apply()

    fun loadChats(): List<AiChat> = AiJson.decodeChats(prefs.getString("chats", null))
    fun saveChats(chats: List<AiChat>) = prefs.edit().putString("chats", AiJson.encodeChats(chats)).apply()

    fun loadAnalyses(): List<AiAnalysis> = AiJson.decodeAnalyses(prefs.getString("analyses", null))
    fun saveAnalyses(analyses: Collection<AiAnalysis>) =
        prefs.edit().putString("analyses", AiJson.encodeAnalyses(analyses)).apply()

    companion object {
        const val PREFS_NAME = "lifttracker_ai"
    }
}
