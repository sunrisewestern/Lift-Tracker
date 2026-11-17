package com.lifttracker.app.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lifttracker.app.domain.WorkoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Values of the provider form. [apiKey] is plain text and only lives while the form is open. */
data class ProviderDraft(
    /** Null for a provider that hasn't been saved yet. */
    val id: String? = null,
    val name: String = "",
    val protocol: Protocol = Protocol.OPENAI,
    val baseUrl: String = "",
    /** Blank while editing means "keep the saved key". */
    val apiKey: String = "",
    val hasSavedKey: Boolean = false,
    val needsKey: Boolean = true,
    val model: String = "",
    val maxTokens: String = AiProvider.DEFAULT_MAX_TOKENS.toString(),
    val extraSystem: String = "",
    val thinkingLevel: ThinkingLevel = ThinkingLevel.DEFAULT,
)

/**
 * AI state and actions: providers, the coach chat and per-workout analyses. Owned by the view
 * model, which passes its scope and exposes its data through [AiDataSource].
 */
class AiController(private val scope: CoroutineScope, private val data: AiDataSource) {
    private var store: AiStore? = null

    /** The app data the AI reads, for callers that build their own prompts. */
    val dataSource: AiDataSource get() = data

    // ---- Providers ----
    val providers = mutableStateListOf<AiProvider>()
    var activeProviderId by mutableStateOf<String?>(null)
        private set

    val activeProvider: AiProvider?
        get() = providers.find { it.id == activeProviderId } ?: providers.firstOrNull()

    /** True when a request can be sent: there is a provider with a model. */
    val isConfigured: Boolean
        get() = activeProvider?.model?.isNotBlank() == true

    var availableModels by mutableStateOf<List<String>>(emptyList())
        private set
    var isLoadingModels by mutableStateOf(false)
        private set
    /** Result of the last "fetch models" call, shown under the form. */
    var modelsMessage by mutableStateOf<String?>(null)
        private set
    private var modelsJob: Job? = null

    /** Model that draws exercise pictures; null until one is set up. */
    var imageModel by mutableStateOf<ImageModel?>(null)
        private set

    // ---- Coach chat ----
    /** Full-screen layers on top of the Coach tab. */
    var isHistoryOpen by mutableStateOf(false)
    var isContextPickerOpen by mutableStateOf(false)
    val chats = mutableStateListOf<AiChat>()
    var currentChatId by mutableStateOf<String?>(null)
        private set
    /** Applies to the next new chat; an existing chat keeps its own setting. */
    private var newChatIncludesTraining by mutableStateOf(true)
    private var newChatContext by mutableStateOf<List<AiContextItem>>(emptyList())
    /** Id of the chat a reply is being written for, and the text received so far. */
    var streamingChatId by mutableStateOf<String?>(null)
        private set
    var chatStreamingText by mutableStateOf("")
        private set
    private var chatJob: Job? = null

    val currentChat: AiChat?
        get() = chats.find { it.id == currentChatId }

    val isChatStreaming: Boolean
        get() = streamingChatId != null

    val includeTrainingInChat: Boolean
        get() = currentChat?.includeTraining ?: newChatIncludesTraining

    /** Routines, exercises and workouts the current chat (or the next new one) is focused on. */
    val chatContext: List<AiContextItem>
        get() = currentChat?.contextItems ?: newChatContext

    // ---- Workout analyses ----
    val analyses = mutableStateMapOf<String, AiAnalysis>()
    var analyzingSessionId by mutableStateOf<String?>(null)
        private set
    var analysisStreamingText by mutableStateOf("")
        private set
    private var analysisJob: Job? = null

    fun attach(store: AiStore) {
        this.store = store
        imageModel = ImageModel.fromJson(store.imageModelJson)
        providers.clear()
        providers.addAll(store.loadProviders())
        activeProviderId = store.activeProviderId
        chats.clear()
        chats.addAll(store.loadChats().sortedByDescending { it.updatedEpochMs })
        analyses.clear()
        store.loadAnalyses().forEach { analyses[it.sessionId] = it }
    }

    private fun newId(prefix: String) = "${prefix}_${System.currentTimeMillis()}_${(0..9999).random()}"

    // ---------------------------------------------------------------- providers

    fun draftFromPreset(preset: ProviderPreset) = ProviderDraft(
        name = preset.name,
        protocol = preset.protocol,
        baseUrl = preset.baseUrl,
        model = preset.defaultModel,
        needsKey = preset.needsKey,
    )

    fun draftFrom(provider: AiProvider) = ProviderDraft(
        id = provider.id,
        name = provider.name,
        protocol = provider.protocol,
        baseUrl = provider.baseUrl,
        hasSavedKey = provider.encryptedKey.isNotEmpty(),
        model = provider.model,
        maxTokens = provider.maxTokens.toString(),
        extraSystem = provider.extraSystem,
        thinkingLevel = provider.thinkingLevel,
    )

    /** Saves the form. Returns an error message, or null when it was saved. */
    fun saveProvider(draft: ProviderDraft): String? {
        val baseUrl = draft.baseUrl.trim()
        if (draft.name.isBlank()) return "Give the provider a name."
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") || baseUrl.substringAfter("://").isBlank()) {
            return "The base URL must start with https:// (or http:// for a model on your network)."
        }
        if (draft.model.isBlank()) return "Enter a model name, or fetch the list and pick one."
        val existing = providers.find { it.id == draft.id }
        val encryptedKey = when {
            draft.apiKey.isNotBlank() -> runCatching { KeyCipher.encrypt(draft.apiKey.trim()) }
                .getOrElse { return "Couldn't encrypt the API key on this device: ${it.message}" }
            else -> existing?.encryptedKey.orEmpty()
        }
        val provider = AiProvider(
            id = existing?.id ?: newId("prov"),
            name = draft.name.trim(),
            protocol = draft.protocol,
            baseUrl = baseUrl,
            encryptedKey = encryptedKey,
            model = draft.model.trim(),
            maxTokens = draft.maxTokens.trim().toIntOrNull()?.coerceIn(256, 128_000) ?: AiProvider.DEFAULT_MAX_TOKENS,
            extraSystem = draft.extraSystem.trim(),
            thinkingLevel = Thinking.resolve(draft.protocol, draft.model.trim(), draft.thinkingLevel),
        )
        val idx = providers.indexOfFirst { it.id == provider.id }
        if (idx >= 0) providers[idx] = provider else providers.add(provider)
        if (providers.none { it.id == activeProviderId }) selectProvider(provider.id)
        store?.saveProviders(providers)
        return null
    }

    fun deleteProvider(id: String) {
        providers.removeAll { it.id == id }
        if (activeProviderId == id) selectProvider(providers.firstOrNull()?.id)
        store?.saveProviders(providers)
    }

    fun selectProvider(id: String?) {
        activeProviderId = id
        store?.activeProviderId = id
    }

    fun resetModelList() {
        modelsJob?.cancel()
        availableModels = emptyList()
        modelsMessage = null
        isLoadingModels = false
    }

    /** Asks the endpoint in [draft] for its models; doubles as a connection test. */
    fun fetchModels(draft: ProviderDraft) {
        modelsJob?.cancel()
        val key = when {
            draft.apiKey.isNotBlank() -> draft.apiKey.trim()
            else -> providers.find { it.id == draft.id }?.let { decryptKey(it) }.orEmpty()
        }
        val config = LlmConfig(draft.protocol, draft.baseUrl.trim(), key, draft.model.trim(), 1024)
        isLoadingModels = true
        modelsMessage = null
        modelsJob = scope.launch {
            try {
                val models = LlmClients.forProtocol(draft.protocol).listModels(config)
                availableModels = models
                modelsMessage = if (models.isEmpty()) "Connected, but the server listed no models." else "Connected · ${models.size} models"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                availableModels = emptyList()
                modelsMessage = "Couldn't list models: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                isLoadingModels = false
            }
        }
    }

    private fun decryptKey(p: AiProvider): String = runCatching { KeyCipher.decrypt(p.encryptedKey) }.getOrDefault("")

    private fun configFor(p: AiProvider) = LlmConfig(
        protocol = p.protocol,
        baseUrl = p.baseUrl,
        apiKey = decryptKey(p),
        model = p.model,
        maxTokens = p.maxTokens,
        thinkingLevel = p.thinkingLevel,
    )

    // ---------------------------------------------------------------- picture model

    fun imageDraft(): ImageModelDraft = imageModel?.let {
        ImageModelDraft(it.protocol, it.baseUrl, hasSavedKey = it.encryptedKey.isNotEmpty(), model = it.model)
    } ?: ImageModelDraft()

    /** Chat providers whose endpoint and key can be reused for pictures. */
    val imageCapableProviders: List<AiProvider>
        get() = providers.filter { it.protocol in ImageModel.PROTOCOLS }

    /** Saves the picture-model form. Returns an error message, or null when it was saved. */
    fun saveImageModel(draft: ImageModelDraft): String? {
        val baseUrl = draft.baseUrl.trim()
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") || baseUrl.substringAfter("://").isBlank()) {
            return "The base URL must start with https:// (or http:// for a model on your network)."
        }
        if (draft.model.isBlank()) return "Enter the name of a picture model."
        val encryptedKey = when {
            draft.apiKey.isNotBlank() -> runCatching { KeyCipher.encrypt(draft.apiKey.trim()) }
                .getOrElse { return "Couldn't encrypt the API key on this device: ${it.message}" }
            draft.keyFromProviderId != null -> providers.find { it.id == draft.keyFromProviderId }?.encryptedKey.orEmpty()
            else -> imageModel?.encryptedKey.orEmpty()
        }
        val saved = ImageModel(draft.protocol, baseUrl, encryptedKey, draft.model.trim())
        imageModel = saved
        store?.imageModelJson = saved.toJson()
        return null
    }

    fun removeImageModel() {
        imageModel = null
        store?.imageModelJson = null
    }

    /** Draws a picture with the saved picture model. Throws [LlmException] with a readable message. */
    suspend fun generateImage(prompt: String, template: ByteArray?, templateMime: String): GeneratedImage {
        val m = imageModel ?: throw LlmException("No picture model is set up. Add one in Settings → AI Coach.")
        val key = runCatching { KeyCipher.decrypt(m.encryptedKey) }.getOrDefault("")
        return ImageClients.generate(ImageConfig(m.protocol, m.baseUrl, key, m.model), prompt, template, templateMime)
    }

    // ---------------------------------------------------------------- assistants (JSON answers)

    /**
     * One question to the active provider, answered in full (used by the exercise and routine
     * assistants). [onProgress] gets the number of characters received so far.
     */
    suspend fun complete(user: String, onProgress: (Int) -> Unit = {}): String {
        val provider = activeProvider?.takeIf { it.model.isNotBlank() }
            ?: throw LlmException("No AI provider is set up. Add one in Settings → AI Coach.")
        val out = StringBuilder()
        LlmClients.forProtocol(provider.protocol)
            .stream(configFor(provider), AssistPrompts.system(provider.extraSystem), user)
            .collect {
                out.append(it)
                onProgress(out.length)
            }
        return out.toString()
    }

    // ---------------------------------------------------------------- chat

    fun startNewChat() {
        currentChatId = null
    }

    fun openChat(id: String) {
        if (chats.any { it.id == id }) currentChatId = id
    }

    fun deleteChat(id: String) {
        if (streamingChatId == id) chatJob?.cancel()
        chats.removeAll { it.id == id }
        if (currentChatId == id) currentChatId = null
        store?.saveChats(chats)
    }

    fun setIncludeTrainingInChat(include: Boolean) {
        val chat = currentChat
        if (chat == null) newChatIncludesTraining = include else updateChat(chat.id) { it.copy(includeTraining = include) }
    }

    /** Adds [item] to the chat's context, or removes it when it is already there. */
    fun toggleContextItem(item: AiContextItem) {
        val has = chatContext.any { it.kind == item.kind && it.id == item.id }
        val next = if (has) chatContext.filterNot { it.kind == item.kind && it.id == item.id } else chatContext + item
        val chat = currentChat
        if (chat == null) newChatContext = next else updateChat(chat.id) { it.copy(contextItems = next) }
    }

    private fun updateChat(id: String, transform: (AiChat) -> AiChat) {
        val idx = chats.indexOfFirst { it.id == id }
        if (idx < 0) return
        chats[idx] = transform(chats[idx])
        store?.saveChats(chats)
    }

    private fun createChat(title: String, pinnedContext: String = "", messages: List<AiChatMessage> = emptyList()): AiChat {
        val now = System.currentTimeMillis()
        val chat = AiChat(
            id = newId("chat"),
            title = title.replace('\n', ' ').trim().take(48).ifBlank { "New chat" },
            createdEpochMs = now,
            updatedEpochMs = now,
            includeTraining = newChatIncludesTraining,
            pinnedContext = pinnedContext,
            contextItems = newChatContext,
            messages = messages,
        )
        newChatContext = emptyList()
        chats.add(0, chat)
        currentChatId = chat.id
        store?.saveChats(chats)
        return chat
    }

    private fun message(role: String, text: String, error: String? = null, model: String? = null) =
        AiChatMessage(newId("msg"), role, text, System.currentTimeMillis(), error, model)

    /** [title] names a new chat when it should differ from the message (quick prompts). */
    fun sendChatMessage(text: String, title: String? = null) {
        val body = text.trim()
        if (body.isEmpty() || isChatStreaming) return
        val chat = currentChat ?: createChat(title ?: body)
        updateChat(chat.id) {
            it.copy(messages = it.messages + message(ChatTurn.USER, body), updatedEpochMs = System.currentTimeMillis())
        }
        startReply(chat.id)
    }

    /** Drops a failed or stopped reply at the end of the chat and asks again. */
    fun retryLastReply() {
        val chat = currentChat ?: return
        if (isChatStreaming) return
        val last = chat.messages.lastOrNull() ?: return
        if (!last.isUser) {
            if (last.error == null) return
            updateChat(chat.id) { it.copy(messages = it.messages.dropLast(1)) }
        }
        startReply(chat.id)
    }

    fun stopChatReply() {
        chatJob?.cancel()
    }

    private fun startReply(chatId: String) {
        val chat = chats.find { it.id == chatId } ?: return
        val provider = activeProvider
        if (provider == null || provider.model.isBlank()) {
            appendReply(chatId, "", "No AI provider is set up. Add one in Settings → AI Coach.", null)
            return
        }
        val system = CoachPrompts.system(
            provider.extraSystem,
            if (chat.includeTraining) TrainingContext.trainingBlock(data, System.currentTimeMillis() / 1000) else "",
            chat.pinnedContext,
            TrainingContext.focusBlock(data, chat.contextItems),
        )
        val turns = toTurns(chat.messages)
        streamingChatId = chatId
        chatStreamingText = ""
        chatJob = scope.launch {
            val out = StringBuilder()
            var error: String? = null
            try {
                LlmClients.forProtocol(provider.protocol).chat(configFor(provider), system, turns).collect {
                    out.append(it)
                    chatStreamingText = out.toString()
                }
                if (out.isBlank()) error = "The model returned an empty answer."
            } catch (e: CancellationException) {
                error = "Stopped."
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                streamingChatId = null
                chatStreamingText = ""
                appendReply(chatId, out.toString(), error, provider.label)
            }
        }
    }

    private fun appendReply(chatId: String, text: String, error: String?, model: String?) {
        updateChat(chatId) {
            it.copy(
                messages = it.messages + message(ChatTurn.ASSISTANT, text, error, model),
                updatedEpochMs = System.currentTimeMillis(),
            )
        }
        // Most recently used chat first.
        val idx = chats.indexOfFirst { it.id == chatId }
        if (idx > 0) {
            chats.add(0, chats.removeAt(idx))
            store?.saveChats(chats)
        }
    }

    // ---------------------------------------------------------------- workout analysis

    fun analyzeWorkout(session: WorkoutSession) {
        if (analyzingSessionId != null) return
        val provider = activeProvider
        if (provider == null || provider.model.isBlank()) return
        val unit = data.unit
        val now = System.currentTimeMillis() / 1000
        val user = CoachPrompts.analyzeWorkout(
            sessionDetail = TrainingContext.sessionDetail(session, unit),
            previous = TrainingContext.previousPerformances(session, data.history, unit),
            weekly = TrainingContext.weeklySummary(data.history, 4, now, unit, data::muscleOf),
            profile = TrainingContext.profile(unit, data.bodyWeightKg, data.bodyFatPercent),
        )
        val system = CoachPrompts.system(provider.extraSystem)
        analyzingSessionId = session.id
        analysisStreamingText = ""
        analysisJob = scope.launch {
            val out = StringBuilder()
            var error: String? = null
            try {
                LlmClients.forProtocol(provider.protocol).stream(configFor(provider), system, user).collect {
                    out.append(it)
                    analysisStreamingText = out.toString()
                }
                if (out.isBlank()) error = "The model returned an empty answer."
            } catch (e: CancellationException) {
                error = "Stopped."
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                analyzingSessionId = null
                analysisStreamingText = ""
                // A stopped run with nothing written keeps the previous analysis.
                if (out.isNotBlank() || error != "Stopped.") {
                    analyses[session.id] = AiAnalysis(session.id, System.currentTimeMillis(), provider.label, out.toString(), error)
                    store?.saveAnalyses(analyses.values)
                }
            }
        }
    }

    fun stopAnalysis() {
        analysisJob?.cancel()
    }

    fun deleteAnalysis(sessionId: String) {
        if (analyses.remove(sessionId) != null) store?.saveAnalyses(analyses.values)
    }

    fun clearAnalyses() {
        analyses.clear()
        store?.saveAnalyses(emptyList())
    }

    /** Starts a new coach chat from the saved analysis of [session]. False when there is nothing to discuss. */
    fun discussAnalysis(session: WorkoutSession): Boolean {
        val analysis = analyses[session.id]?.takeIf { it.output.isNotBlank() } ?: return false
        if (isChatStreaming) return false
        newChatContext = emptyList()
        createChat(
            title = "About: ${session.title}",
            pinnedContext = "# Workout under discussion\n" + TrainingContext.sessionDetail(session, data.unit),
            messages = listOf(
                message(ChatTurn.USER, "Analyze my workout \"${session.title}\"."),
                message(ChatTurn.ASSISTANT, analysis.output, model = analysis.model),
            ),
        )
        return true
    }

    companion object {
        /**
         * The conversation as the API needs it: failed replies with no text are dropped, and user
         * messages left next to each other by that are merged, so turns alternate and end with the user.
         */
        internal fun toTurns(messages: List<AiChatMessage>): List<ChatTurn> {
            val turns = mutableListOf<ChatTurn>()
            messages.filter { it.text.isNotBlank() }.forEach { m ->
                val last = turns.lastOrNull()
                if (last != null && last.role == m.role) {
                    turns[turns.lastIndex] = ChatTurn(m.role, last.text + "\n\n" + m.text)
                } else {
                    turns.add(ChatTurn(m.role, m.text))
                }
            }
            while (turns.isNotEmpty() && !turns.last().isUser) turns.removeAt(turns.lastIndex)
            return turns
        }
    }
}
