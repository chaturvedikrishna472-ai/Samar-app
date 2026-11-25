package com.samar.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/** Online-first chat; falls back to a local LLM, then to lightweight local rules. */
object SamarChatRouter {
    // Configure a secure HTTPS backend later. Never put provider API keys in the APK.
    private const val SAMAR_CHAT_ENDPOINT = ""
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 12000
    private val executor = Executors.newSingleThreadExecutor()

    enum class Mode { ONLINE, LOCAL_AI, OFFLINE }
    data class ChatResult(val reply: String, val mode: Mode, val detail: String)

    fun hasInternet(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        if (network == null) false else {
            val caps = cm.getNetworkCapabilities(network)
            caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    } catch (_: Exception) { false }

    fun currentMode(context: Context): Mode = when {
        hasInternet(context) && SAMAR_CHAT_ENDPOINT.isNotBlank() -> Mode.ONLINE
        LocalModelRegistry.isModelReady(context) -> Mode.LOCAL_AI
        else -> Mode.OFFLINE
    }

    fun send(context: Context, message: String, callback: (ChatResult) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val onlineAvailable = hasInternet(appContext) && SAMAR_CHAT_ENDPOINT.isNotBlank()
            val result = if (onlineAvailable) {
                requestOnline(message) ?: localOrRules(appContext, message,
                    "Cloud request failed; used on-device fallback.")
            } else {
                localOrRules(appContext, message,
                    if (!hasInternet(appContext)) "No internet; on-device mode." else
                        "Cloud backend not configured; on-device mode.")
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
        }
    }

    private fun localOrRules(context: Context, message: String, reason: String): ChatResult {
        val localReply = LocalModelRegistry.generate(context, message)
        return if (!localReply.isNullOrBlank()) {
            ChatResult(localReply, Mode.LOCAL_AI, "$reason Local generative model responded on this device.")
        } else {
            ChatResult(OfflineChatEngine.reply(context, message), Mode.OFFLINE,
                "$reason Local model unavailable or failed; rule-based fallback used.")
        }
    }

    private fun requestOnline(message: String): ChatResult? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(SAMAR_CHAT_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val body = JSONObject().put("message", message).toString()
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val reply = JSONObject(response).optString("reply").trim()
            if (reply.isBlank()) null else ChatResult(reply, Mode.ONLINE, "Cloud AI connected.")
        } catch (_: Exception) { null } finally { conn?.disconnect() }
    }
}
