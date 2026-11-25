package com.samar.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

object SamarCore {

    private val mainHandler = Handler(Looper.getMainLooper())

    const val CLEAR_LOG_SIGNAL = "__SAMAR_CLEAR_LOG__"

    private var lastRepeatableCommand: String? = null

    private val commonAppPackages: Map<String, String> = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "play store" to "com.android.vending",
        "phone" to "com.android.dialer",
        "dialer" to "com.android.dialer",
        "messages" to "com.google.android.apps.messaging",
        "camera" to "com.android.camera",
        "instagram" to "com.instagram.android",
        "maps" to "com.google.android.apps.maps",
        "clock" to "com.android.deskclock",
        "contacts" to "com.android.contacts",
        "gallery" to "com.miui.gallery",
        "photos" to "com.google.android.apps.photos"
    )

    // Ask only for permissions used by the current MVP. SMS is drafted in the
    // user's messaging app, so SAMAR does not need direct-send permission.
    val requiredRuntimePermissions: List<String> = listOf(
        Manifest.permission.RECORD_AUDIO
    )

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityEnabled(): Boolean {
        return SamarAccessibilityService.isRunning()
    }

    fun getStatusSummary(context: Context): String {
        val acc = if (isAccessibilityEnabled()) "ON" else "OFF"
        val mic = if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) "OK" else "MISSING"
        return "SAMAR status: Accessibility $acc, Mic $mic, SMS composer available (user confirms send). HUD ready."
    }

    fun getCommandsHelp(): String {
        return "Commands: open <app/settings/wifi/bluetooth/sound/battery/display/location/date>, call <number>, click <text>, find <text>, tap <x> <y>, type <text>, search <text>, sms <number> : <msg>, read screen, scroll down/up, back, home, recents, repeat, clear log, status. 'and' ya 'then' se chain bhi kar sakte ho."
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openDialer(context: Context, phoneNumber: String): String {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) {
            return "Kisko dial karna hai uska number toh batao, main telepath nahi hoon."
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "$clean ke liye dialer khol diya hai, sir."
    }

    fun tapScreen(x: Float, y: Float): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai boss. Pehle settings mein on karo."
        return if (service.performTap(x, y)) {
            "($x, $y) par tap kar diya hai—clean shot."
        } else {
            "Tap dispatch fail ho gaya."
        }
    }

    fun clickText(targetText: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val clean = targetText.trim()
        if (clean.isEmpty()) return "Kya click karna hai uska naam toh batao."
        return if (service.clickNodeByText(clean)) {
            "'$clean' par click kar diya hai."
        } else {
            "Screen par '$clean' nahi mila ya clickable nahi hai."
        }
    }

    fun findOrClickText(targetText: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val clean = targetText.trim()
        if (clean.isEmpty()) return "Screen par kya dhoondhna hai wo toh batao."

        if (service.clickNodeByText(clean)) {
            return "'$clean' mil gaya aur click kar diya hai."
        }

        val screenContent = service.readScreenText(40)
        if (screenContent.contains(clean, ignoreCase = true)) {
            return "'$clean' screen par dikh raha hai, par direct clickable node nahi mila."
        }

        if (service.performScroll(true)) {
            mainHandler.postDelayed({
                if (!service.clickNodeByText(clean) && service.performScroll(true)) {
                    mainHandler.postDelayed({
                        service.clickNodeByText(clean)
                    }, 650L)
                }
            }, 600L)
            return "'$clean' turant nahi mila—neeche scroll karke scan kar raha hoon."
        }

        return "'$clean' abhi is screen par nahi mila."
    }

    fun typeIntoField(text: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val clean = text.trim()
        if (clean.isEmpty()) return "Kya type karna hai wo toh batao."
        return if (service.typeText(clean)) {
            "'$clean' likh diya hai input box mein."
        } else {
            "Screen par koi editable text box nahi mila."
        }
    }

    fun searchOnScreen(query: String): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val clean = query.trim()
        if (clean.isEmpty()) return "Kya search karna hai wo toh bolo."
        service.clickNodeByText("Search")
        return if (service.typeText(clean)) {
            "'$clean' search field mein daal diya hai."
        } else {
            "Search box nahi mil paaya—pehle field tap karke 'type $clean' try karo."
        }
    }

    fun goBack(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemBack()) {
            "Ek kadam peeche—tactical retreat complete."
        } else {
            "Back action nahi ho paaya."
        }
    }

    fun goHome(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemHome()) {
            "Home screen par wapas."
        } else {
            "Home action nahi ho paaya."
        }
    }

    fun openRecents(): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return if (service.performSystemRecents()) {
            "Recent apps khol di hain."
        } else {
            "Recent apps nahi khul paayin."
        }
    }

    fun scrollScreen(down: Boolean = true): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        val dir = if (down) "neeche" else "upar"
        return if (service.performScroll(down)) {
            "Screen $dir scroll kar di hai."
        } else {
            "Screen scroll nahi ho paayi."
        }
    }

    fun readScreen(maxItems: Int = 25): String {
        val service = SamarAccessibilityService.instance
            ?: return "Accessibility service off hai bhai, pehle on karo."
        return service.readScreenText(maxItems)
    }

    fun sendSms(context: Context, phoneNumber: String, message: String): String {
        val cleanNumber = phoneNumber.trim()
        val cleanMessage = message.trim()
        if (cleanNumber.isEmpty() || cleanMessage.isEmpty()) {
            return "SMS ke liye number aur message dono chahiye."
        }
        return try {
            val intent = Intent(
                Intent.ACTION_SENDTO,
                Uri.parse("smsto:${Uri.encode(cleanNumber)}")
            ).apply {
                putExtra("sms_body", cleanMessage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Messaging app mein SMS draft khol diya hai. Number aur message check karke Send aap khud dabana."
        } catch (e: Exception) {
            "Messaging app nahi khul paayi: ${e.message ?: "unknown error"}"
        }
    }

    fun openAppByPackage(context: Context, packageName: String): String {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "$packageName khol diya hai, sir."
        } else {
            try {
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
                "$packageName install nahi mila, Play Store khol diya hai."
            } catch (_: Exception) {
                "$packageName install nahi mila aur Play Store bhi available nahi hai."
            }
        }
    }

    /** Recognizes explicit local phone-control commands. */
    fun isDeviceDirective(rawCommand: String): Boolean {
        val command = rawCommand.trim().lowercase()
        val starters = listOf(
            "open ", "launch ", "call ", "dial ", "sms ", "text ", "message ",
            "click ", "tap ", "find ", "type ", "search ", "scroll ",
            "read screen", "read the screen", "back", "go back", "home", "go home",
            "recents", "recent apps", "repeat", "clear log", "status", "help"
        )
        return starters.any { command == it.trim() || command.startsWith(it) }
    }

    fun handleCommand(
        context: Context,
        rawCommand: String,
        onAsyncStepResult: ((String) -> Unit)? = null
    ): String {
        val appContext = context.applicationContext
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) {
            return "Kuch sunai nahi diya bhai, thoda aur zor se bolo."
        }

        val steps = trimmed.split(Regex("(?i)\\s+(?:and|then)\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (steps.size > 1) {
            val results = mutableListOf<String>()
            steps.forEachIndexed { index, step ->
                if (index == 0) {
                    results.add(runStep(appContext, step, true))
                } else {
                    val delayMs = index * 1100L
                    mainHandler.postDelayed({
                        val stepReply = runStep(appContext, step, false)
                        onAsyncStepResult?.invoke(stepReply)
                    }, delayMs)
                    results.add("Queued: '$step'")
                }
            }
            return results.joinToString(" | ")
        }

        return runStep(appContext, trimmed, true)
    }

    private fun runStep(
        context: Context,
        rawCommand: String,
        saveRepeat: Boolean
    ): String {
        val command = rawCommand.trim().lowercase()
        if (command.isEmpty()) {
            return "Kuch sunai nahi diya bhai."
        }

        if (command == "repeat" || command == "phir se" || command == "dobara" || command == "wahi kar") {
            val prev = lastRepeatableCommand
                ?: return "Abhi tak koi pichla action command record nahi hua hai."
            return runStep(context, prev, false)
        }

        val repeatable = command.startsWith("open ") ||
            command.startsWith("call ") ||
            command.startsWith("dial ") ||
            command.startsWith("tap ") ||
            command.startsWith("click ") ||
            command.startsWith("find ") ||
            command.startsWith("type ") ||
            command.startsWith("write ") ||
            command.startsWith("search ") ||
            command.contains("scroll down") ||
            command.contains("neeche scroll") ||
            command.contains("scroll up") ||
            command.contains("upar scroll") ||
            command.contains("go back") ||
            command == "back" ||
            command.contains("peeche ja") ||
            command.contains("waapis")

        if (saveRepeat && repeatable) {
            lastRepeatableCommand = rawCommand.trim()
        }

        return when {
            command == "clear log" || command == "clear chat" || command == "reset log" || command == "clear" -> {
                CLEAR_LOG_SIGNAL
            }
            command == "hello" || command == "hi" || command == "hey" || command.startsWith("namaste") -> {
                "Haan boss, SAMAR hazir hai—suit polished aur sarcasm loaded. Bol kya karna hai?"
            }
            command.contains("who are you") || command.contains("kaun hai tu") || command.contains("intro") -> {
                "Main SAMAR hoon—JARVIS ki classy precision aur Deadpool ka thoda sa attitude, ek hi Android body mein."
            }
            command == "help" || command == "commands" || command.contains("kya kar sakte") -> {
                getCommandsHelp()
            }
            command == "status" || command.contains("system check") -> {
                getStatusSummary(context)
            }
            command.startsWith("call ") || command.startsWith("dial ") -> {
                openDialer(context, rawCommand.trim().substringAfter(" ", "").trim())
            }
            command.startsWith("tap ") -> {
                val coords = command.removePrefix("tap ").trim().split(Regex("[,\\s]+"))
                val x = coords.getOrNull(0)?.toFloatOrNull()
                val y = coords.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null) tapScreen(x, y) else "Format: tap <x> <y>"
            }
            command.startsWith("sms ") || command.startsWith("send sms ") -> {
                val payload = rawCommand.trim()
                    .replaceFirst(Regex("(?i)^send\\s+sms\\s+|^sms\\s+"), "")
                    .trim()
                val sep = payload.indexOf(':')
                if (sep > 0) {
                    val num = payload.substring(0, sep).trim()
                    val msg = payload.substring(sep + 1).trim()
                    if (num.isNotEmpty() && msg.isNotEmpty()) sendSms(context, num, msg)
                    else "Format: sms <number> : <message>"
                } else {
                    val parts = payload.split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        sendSms(context, parts[0].trim(), parts[1].trim())
                    } else {
                        "Format: sms <number> : <message>"
                    }
                }
            }
            command.startsWith("search ") -> {
                searchOnScreen(rawCommand.trim().substringAfter(" ", "").trim())
            }
            command.startsWith("type ") || command.startsWith("write ") -> {
                typeIntoField(rawCommand.trim().substringAfter(" ", "").trim())
            }
            command.startsWith("find ") -> {
                findOrClickText(rawCommand.trim().substringAfter(" ", "").trim())
            }
            command.startsWith("click ") -> {
                clickText(rawCommand.trim().substringAfter(" ", "").trim())
            }
            command.contains("go back") || command == "back" || command.contains("peeche ja") || command.contains("waapis") -> {
                goBack()
            }
            command.contains("go home") || command == "home" || command.contains("home screen") || command.contains("ghar chal") -> {
                goHome()
            }
            command.contains("recent") || command.contains("recents") -> {
                openRecents()
            }
            command.contains("scroll down") || command.contains("neeche scroll") -> {
                scrollScreen(down = true)
            }
            command.contains("scroll up") || command.contains("upar scroll") -> {
                scrollScreen(down = false)
            }
            command.contains("read screen") || command.contains("screen padh") -> {
                readScreen()
            }
            command.contains("accessibility") -> {
                openAccessibilitySettings(context)
                "Accessibility settings khol di hain, ab SAMAR ko full power do."
            }
            command.startsWith("open ") -> {
                val target = command.removePrefix("open ").trim()
                when (target) {
                    "settings" -> {
                        val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Phone settings khol di hain."
                    }
                    "wifi" -> {
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Wi-Fi settings khol di hain."
                    }
                    "bluetooth" -> {
                        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Bluetooth settings khol di hain."
                    }
                    "sound" -> {
                        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Sound settings khol di hain."
                    }
                    "battery" -> {
                        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Battery settings khol di hain."
                    }
                    "display" -> {
                        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Display settings khol di hain."
                    }
                    "location" -> {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Location settings khol di hain."
                    }
                    "date" -> {
                        val intent = Intent(Settings.ACTION_DATE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        "Date settings khol di hain."
                    }
                    else -> {
                        if (commonAppPackages.containsKey(target)) {
                            openAppByPackage(context, commonAppPackages.getValue(target))
                        } else if (target.contains(".")) {
                            openAppByPackage(context, target)
                        } else {
                            "'$target' ka package nahi mila—package naam ya common app (whatsapp, youtube, chrome, camera, instagram, maps, clock, contacts, gallery, photos) bolo."
                        }
                    }
                }
            }
            else -> {
                OfflineChatEngine.reply(context, rawCommand)
            }
        }
    }
}
