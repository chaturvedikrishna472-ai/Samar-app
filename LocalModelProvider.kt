package com.samar.assistant

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.Executors

/** On-device generative model provider powered by Google's LiteRT-LM runtime. */
object LocalModelRegistry {
    private const val PREFS = "samar_local_model"
    private const val KEY_MODEL_PATH = "model_path"
    private const val MODEL_FILE = "samar-local-model.litertlm"

    private val lock = Any()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedPath: String? = null

    fun modelFile(context: Context): File = File(context.filesDir, MODEL_FILE)

    fun isModelReady(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() > 0L
    }

    fun status(context: Context): String = if (isModelReady(context)) {
        "LOCAL AI: MODEL INSTALLED"
    } else {
        "LOCAL AI: MODEL NOT INSTALLED"
    }

    /** Copies a user-selected .litertlm file into SAMAR's private app storage. */
    fun importModel(context: Context, uri: Uri, callback: (Boolean, String) -> Unit) {
        val app = context.applicationContext
        ioExecutor.execute {
            val result = try {
                val destination = modelFile(app)
                val temp = File(app.filesDir, "$MODEL_FILE.tmp")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Selected file could not be opened")
                require(temp.length() > 0L) { "Selected model file is empty" }
                if (destination.exists()) destination.delete()
                check(temp.renameTo(destination)) { "Could not save model into app storage" }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_MODEL_PATH, destination.absolutePath).apply()
                synchronized(lock) { releaseEngineLocked() }
                true to "Local model copied into SAMAR storage. First load may take a little time."
            } catch (e: Exception) {
                false to "Model import failed: ${e.message ?: "unknown error"}"
            }
            mainHandler.post { callback(result.first, result.second) }
        }
    }

    /** Runs inference synchronously; caller must invoke from a background thread. */
    fun generate(context: Context, prompt: String): String? {
        val app = context.applicationContext
        val model = modelFile(app)
        if (!model.isFile || model.length() == 0L || prompt.isBlank()) return null

        return synchronized(lock) {
            try {
                ensureConversation(model, app)
                conversation?.sendMessage(prompt)?.text?.trim()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                releaseEngineLocked()
                null
            }
        }
    }

    private fun ensureConversation(model: File, context: Context) {
        if (loadedPath == model.absolutePath && engine != null && conversation != null) return
        releaseEngineLocked()

        val newEngine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        )
        // Engine initialization can take several seconds; generate() is called off the UI thread.
        newEngine.initialize()
        engine = newEngine
        conversation = newEngine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "You are SAMAR, a helpful personal AI assistant. Speak naturally in Hindi, English, or Hinglish matching the user's language. Be clear, composed, witty when appropriate, and serious for important topics. Never claim to be human. Keep answers concise unless detail is requested."
                ),
                samplerConfig = SamplerConfig(topK = 32, topP = 0.9, temperature = 0.7)
            )
        )
        loadedPath = model.absolutePath
    }

    private fun releaseEngineLocked() {
        try { conversation?.close() } catch (_: Exception) { }
        conversation = null
        try { engine?.close() } catch (_: Exception) { }
        engine = null
        loadedPath = null
    }
}
