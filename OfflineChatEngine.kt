package com.samar.assistant

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight, fully on-device fallback conversation engine.
 * It uses local rules and a tiny preference store; it does not call the network.
 * This is not a generative LLM. A downloadable local model can be added later.
 */
object OfflineChatEngine {
    private const val PREFS = "samar_offline_memory"
    private const val KEY_NAME = "user_name"
    private const val KEY_LAST_TOPIC = "last_topic"
    private const val KEY_TURNS = "conversation_count"

    fun reply(context: Context, raw: String): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val input = raw.trim()
        val q = input.lowercase(Locale.ROOT)
        val turns = prefs.getInt(KEY_TURNS, 0) + 1
        prefs.edit().putInt(KEY_TURNS, turns).apply()

        val nameMatch = Regex("(?i)\\b(?:mera naam|my name is|i am)\\s+([\\p{L} ]{2,30})").find(input)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1].trim().trimEnd('.', '!', '?')
            prefs.edit().putString(KEY_NAME, name).apply()
            return "Noted. Is session mein main tumhe $name bulaunga. Offline memory mein save kar liya."
        }

        val savedName = prefs.getString(KEY_NAME, null)
        val prefix = if (!savedName.isNullOrBlank()) "$savedName, " else ""

        val response = when {
            q.matches(Regex("(hi|hii+|hello|hey|yo|namaste|नमस्ते)[.! ]*")) ->
                "Hey! SAMAR offline mode mein bhi present hai. Internet gaya hai, attitude nahi. Bolo, kya scene hai?"
            q.contains("how are you") || q.contains("kaise ho") || q.contains("kaisa hai") ->
                "Systems stable, ${if (savedName.isNullOrBlank()) "boss" else savedName}. Main offline hoon, lekin conversation ke liye ready. Tum kaise ho?"
            q.contains("who are you") || q.contains("kaun ho") || q.contains("apna intro") ->
                "Main SAMAR hoon—tumhara on-device assistant. Abhi offline mode mein lightweight conversational brain use kar raha hoon; full AI model abhi installed nahi hai."
            q.contains("bore") || q.contains("timepass") || q.contains("bored") ->
                "Timepass protocol activated. Ek choose karo: random fact, paheli, silly debate, ya roast session. Network ki zaroorat nahi—meri bakwaas locally rendered hai."
            q.contains("joke") || q.contains("chutkula") ->
                "Teacher: Homework kahan hai? Student: Sir, cloud mein save tha. Teacher: Toh? Student: Internet nahi tha. Ab aap bhi offline excuse mat dena."
            q.contains("roast") || q.contains("roast me") ->
                "Tumhara planning system NASA-level hai; execution abhi loading screen par atka hai. 😏 Chalo, ek chhota step abhi complete karo."
            q.contains("time") || q.contains("samay") || q.contains("kitne baje") -> {
                val now = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date())
                "Phone ke local clock ke hisaab se abhi $now hai. Iske liye internet nahi chahiye."
            }
            q.contains("thank") || q.contains("shukriya") || q.contains("thanks") ->
                "Anytime. Offline hoon, unavailable nahi. 😎"
            q.contains("good night") || q.contains("shubh ratri") ->
                "Good night. Systems low-power conversation mode mein ja rahe hain. Kal ke plans kal tackle karenge."
            q.contains("good morning") || q.contains("suprabhat") ->
                "Good morning! Naya din, naya mission. Pehle paani, phir plans—overengineering baad mein."
            q.contains("what can you do") || q.contains("kya kar sakte") ->
                "Offline mein main basic chat, jokes, paheliyan, simple replies, phone ka local time aur chhoti session-memory handle kar sakta hoon. Web search, live facts aur advanced AI reasoning ke liye internet/model chahiye. 'help' se phone commands bhi dekh sakte ho."
            q.contains("internet") || q.contains("net off") || q.contains("offline") ->
                "Haan, abhi ye reply poori tarah phone par local rules se bana hai. Koi server call nahi. Dhyaan rahe: ye lightweight offline brain hai, full language model nahi."
            q.contains("paheli") || q.contains("riddle") ->
                "Paheli: Aisi kaunsi cheez hai jo tumhari hai, lekin use tumse zyada doosre log karte hain? Socho zara."
            q.contains("answer") && prefs.getString(KEY_LAST_TOPIC, "") == "riddle" ->
                "Jawab: Tumhara naam! Aur haan, itni jaldi hint maang li—detective training pending hai."
            q.contains("yes") || q == "haan" || q == "ha" || q == "yep" ->
                "Understood. Thoda context aur do—main offline mode mein hoon, mind-reading module abhi beta mein bhi nahi."
            q.contains("no") || q == "nahi" || q == "nah" ->
                "Theek hai. No pressure. Topic badal dete hain—kya baat karni hai?"
            else -> {
                prefs.edit().putString(KEY_LAST_TOPIC, input.take(80)).apply()
                val replies = listOf(
                    "Interesting. Iske baare mein thoda aur batao—main sun raha hoon.",
                    "Samjha. Tumhara point note kar liya. Is situation mein tumhe sabse zyada kya bother kar raha hai?",
                    "Hmm. Mere paas abhi full offline language model nahi, par main conversation continue kar sakta hoon. Thoda context do.",
                    "Acha, phir aage? Main yahin hoon—network ko chhutti mili hai, conversation ko nahi."
                )
                prefix + replies[(turns - 1) % replies.size]
            }
        }
        if (q.contains("paheli") || q.contains("riddle")) {
            prefs.edit().putString(KEY_LAST_TOPIC, "riddle").apply()
        }
        return response
    }
}
