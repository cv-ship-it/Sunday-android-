package com.king.sunday.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

/** Settings + the small "things I remember about King" list. API key is stored encrypted when the device allows it. */
class Prefs(ctx: Context) {

    private val sp: SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx,
            "sunday_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Some phones have flaky Keystores; fall back to private plain storage.
        ctx.getSharedPreferences("sunday_plain", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
        set(v) = sp.edit().putString("model", v.trim()).apply()

    var name: String
        get() = sp.getString("name", "Sunday") ?: "Sunday"
        set(v) = sp.edit().putString("name", v.trim().ifBlank { "Sunday" }).apply()

    /** If false (default), Play Store "Install/Update" taps ask King first. */
    var trustedInstalls: Boolean
        get() = sp.getBoolean("trusted_installs", false)
        set(v) = sp.edit().putBoolean("trusted_installs", v).apply()

    var conversationMode: Boolean
        get() = sp.getBoolean("conversation_mode", true)
        set(v) = sp.edit().putBoolean("conversation_mode", v).apply()

    var greetOnOpen: Boolean
        get() = sp.getBoolean("greet_on_open", true)
        set(v) = sp.edit().putBoolean("greet_on_open", v).apply()

    /** "Hey Sunday" always-listening mode (uses battery; foreground service). */
    var hotword: Boolean
        get() = sp.getBoolean("hotword", false)
        set(v) = sp.edit().putBoolean("hotword", v).apply()

    /** Small draggable orb shown over every app. */
    var floatingOrb: Boolean
        get() = sp.getBoolean("floating_orb", false)
        set(v) = sp.edit().putBoolean("floating_orb", v).apply()

    /** Agni server on your VPS, e.g. https://agni.yourdomain.com */
    var agniUrl: String
        get() = sp.getString("agni_url", "") ?: ""
        set(v) = sp.edit().putString("agni_url", v.trim().trimEnd('/')).apply()

    var agniToken: String
        get() = sp.getString("agni_token", "") ?: ""
        set(v) = sp.edit().putString("agni_token", v.trim()).apply()

    /** Speech recognition language, e.g. hi-IN (understands Hinglish well) or en-IN. */
    var sttLang: String
        get() = sp.getString("stt_lang", "hi-IN") ?: "hi-IN"
        set(v) = sp.edit().putString("stt_lang", v).apply()

    /** Voice language, e.g. en-IN (reads Roman Hinglish naturally) or hi-IN. */
    var ttsLang: String
        get() = sp.getString("tts_lang", "en-IN") ?: "en-IN"
        set(v) = sp.edit().putString("tts_lang", v).apply()

    var voiceName: String
        get() = sp.getString("voice_name", "") ?: ""
        set(v) = sp.edit().putString("voice_name", v).apply()

    fun facts(): List<String> {
        val raw = sp.getString("facts", "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            List(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addFact(fact: String) {
        val list = facts().toMutableList()
        val f = fact.trim().take(200)
        if (f.isBlank() || list.contains(f)) return
        list.add(f)
        while (list.size > 60) list.removeAt(0)
        sp.edit().putString("facts", JSONArray(list).toString()).apply()
    }

    fun clearFacts() {
        sp.edit().putString("facts", "[]").apply()
    }
}
