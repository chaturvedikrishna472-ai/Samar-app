package com.samar.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private lateinit var hudSubStatusText: TextView
    private lateinit var hudLastCommandText: TextView
    private lateinit var hudResponseText: TextView
    private lateinit var hudActionLogText: TextView
    private lateinit var hudLatencyText: TextView
    private lateinit var hudCommandInput: EditText
    private lateinit var btnAccessibility: Button
    private lateinit var btnClearHud: Button
    private lateinit var btnLocalModel: Button
    private lateinit var btnMicTrigger: Button
    private lateinit var btnSendCommand: Button

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val spokenText = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    .orEmpty()

                if (spokenText.isNotBlank()) {
                    hudCommandInput.setText(spokenText)
                    executeDirective(spokenText, source = "VOICE")
                } else {
                    updateLog("SYS.LOG: Voice input empty")
                }
            }
        }

    private val localModelPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                hudResponseText.text = "Importing local model... keep SAMAR open."
                updateLog("SYS.LOG: Copying selected local model")
                LocalModelRegistry.importModel(this, uri) { success, message ->
                    hudResponseText.text = message
                    updateLog(if (success) "SYS.LOG: Local model imported" else "SYS.LOG: Model import failed")
                    refreshHudStatus()
                }
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            refreshHudStatus()
            updateLog("SYS.LOG: Permissions updated")
            if (result[android.Manifest.permission.RECORD_AUDIO] == true) {
                launchVoiceInput()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindHudViews()
        wireHudControls()
        textToSpeech = TextToSpeech(this, this)
        refreshHudStatus()
        hudResponseText.text =
            "Systems wired, suit's sharp, no blue glow needed—now let's see if SAMAR actually runs the show or just roasts your typing."
    }

    override fun onResume() {
        super.onResume()
        refreshHudStatus()
    }

    private fun bindHudViews() {
        hudSubStatusText = findViewById(R.id.hudSubStatusText)
        hudLastCommandText = findViewById(R.id.hudLastCommandText)
        hudResponseText = findViewById(R.id.hudResponseText)
        hudActionLogText = findViewById(R.id.hudActionLogText)
        hudLatencyText = findViewById(R.id.hudLatencyText)
        hudCommandInput = findViewById(R.id.hudCommandInput)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnClearHud = findViewById(R.id.btnClearHud)
        btnLocalModel = findViewById(R.id.btnLocalModel)
        btnMicTrigger = findViewById(R.id.btnMicTrigger)
        btnSendCommand = findViewById(R.id.btnSendCommand)
    }

    private fun wireHudControls() {
        btnSendCommand.setOnClickListener {
            val raw = hudCommandInput.text?.toString()?.trim().orEmpty()
            if (raw.isNotEmpty()) {
                executeDirective(raw, source = "HUD")
            } else {
                hudResponseText.text = "Blank input? Bold move. Type an actual directive."
                updateLog("SYS.LOG: Ignored empty directive")
            }
        }

        hudCommandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnSendCommand.performClick()
                true
            } else {
                false
            }
        }

        btnMicTrigger.setOnClickListener {
            if (SamarCore.hasPermission(this, android.Manifest.permission.RECORD_AUDIO)) {
                launchVoiceInput()
            } else {
                permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
            }
        }

        btnAccessibility.setOnClickListener {
            updateLog("SYS.LOG: Opening Accessibility settings")
            SamarCore.openAccessibilitySettings(this)
        }

        btnClearHud.setOnClickListener {
            resetHudStream("HUD wiped clean. Try not to make a mess this time.")
        }

        btnLocalModel.setOnClickListener {
            hudResponseText.text = if (LocalModelRegistry.isModelReady(this)) {
                "A local model is installed. Select another .litertlm file to replace it."
            } else {
                "Choose a compatible .litertlm model file. The model is stored privately on this phone."
            }
            localModelPicker.launch(arrayOf("application/octet-stream", "*/*"))
        }
    }

    private fun executeDirective(command: String, source: String) {
        val startTime = System.currentTimeMillis()
        hudLastCommandText.text = "> [$source] $command"
        hudCommandInput.setText("")

        // Phone-control commands stay local. Conversation uses online-first
        // routing and automatically falls back to the local chat engine.
        if (!SamarCore.isDeviceDirective(command)) {
            hudResponseText.text = "SAMAR is thinking..."
            updateLog("SYS.LOG: Selecting online/offline chat mode")
            SamarChatRouter.send(this, command) { result ->
                hudResponseText.text = result.reply
                hudLatencyText.text = "${(System.currentTimeMillis() - startTime).coerceAtLeast(1L)}ms"
                speakReply(result.reply)
                updateLog("SYS.LOG: ${result.mode.name} CHAT | ${result.detail}")
                refreshHudStatus()
            }
            refreshHudStatus()
            return
        }

        val reply = SamarCore.handleCommand(
            context = this,
            rawCommand = command,
            onAsyncStepResult = { asyncReply ->
                runOnUiThread {
                    if (asyncReply == SamarCore.CLEAR_LOG_SIGNAL) {
                        resetHudStream("HUD wiped clean. Try not to make a mess this time.")
                    } else {
                        hudResponseText.text = asyncReply
                        speakReply(asyncReply)
                        refreshHudStatus()
                        updateLog("SYS.LOG: Async step complete")
                    }
                }
            }
        )

        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        hudLatencyText.text = "${elapsed}ms"

        if (reply == SamarCore.CLEAR_LOG_SIGNAL) {
            resetHudStream("HUD wiped clean. Try not to make a mess this time.")
            return
        }

        hudResponseText.text = reply
        speakReply(reply)
        refreshHudStatus()
        updateLog("SYS.LOG: Executed via $source")
    }

    private fun resetHudStream(message: String) {
        hudLastCommandText.text = "> AWAITING INPUT"
        hudResponseText.text = message
        hudLatencyText.text = "00ms"
        hudCommandInput.setText("")
        updateLog("SYS.LOG: Telemetry stream cleared")
    }

    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak directive for SAMAR...")
        }
        try {
            updateLog("SYS.LOG: Listening for voice directive")
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            hudResponseText.text = "Speech recognizer isn't available on this device. Manual typing still works."
            updateLog("SYS.LOG: Speech recognizer unavailable")
        }
    }

    private fun refreshHudStatus() {
        val accActive = SamarCore.isAccessibilityEnabled()
        val access = if (accActive) "ACCESSIBILITY: CONNECTED" else "ACCESSIBILITY: STANDBY"
        val mode = when (SamarChatRouter.currentMode(this)) {
            SamarChatRouter.Mode.ONLINE -> "CHAT: ONLINE"
            SamarChatRouter.Mode.LOCAL_AI -> "CHAT: LOCAL AI"
            SamarChatRouter.Mode.OFFLINE -> "CHAT: BASIC OFFLINE"
        }
        hudSubStatusText.text = "HUD READY  |  $mode  |  $access"
    }

    private fun updateLog(entry: String) {
        hudActionLogText.text = entry
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = textToSpeech ?: return
            val languageResult = engine.setLanguage(Locale.getDefault())
            ttsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) {
                ttsReady = engine.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
            }
            if (ttsReady) {
                engine.setSpeechRate(1.0f)
                updateLog("SYS.LOG: Voice output ready")
            } else {
                updateLog("SYS.LOG: No supported TTS language installed")
            }
        } else {
            ttsReady = false
            updateLog("SYS.LOG: Text-to-speech initialization failed")
        }
    }

    private fun speakReply(message: String) {
        if (!ttsReady || message.isBlank() || message == SamarCore.CLEAR_LOG_SIGNAL) return
        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "samar_reply")
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
        super.onDestroy()
    }
}
