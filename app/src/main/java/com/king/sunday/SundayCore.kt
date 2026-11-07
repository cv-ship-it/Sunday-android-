package com.king.sunday

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.king.sunday.agent.Agent
import com.king.sunday.agent.ClaudeClient
import com.king.sunday.agent.ToolExecutor
import com.king.sunday.data.Prefs
import com.king.sunday.phone.ConfirmOverlay
import com.king.sunday.ui.Emotion
import com.king.sunday.ui.Mode
import com.king.sunday.voice.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class SetupState(
    val mic: Boolean = false,
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val apiKey: Boolean = false
) {
    val allGood get() = mic && accessibility && overlay && apiKey
}

/**
 * The one shared brain of the app. The screen, the floating orb and the "Hey Sunday" service all talk to this
 * same object, so they always agree on what Sunday is doing.
 */
class SundayCore(private val app: Application) {

    val prefs = Prefs(app)
    val voice = VoiceEngine(app, prefs)
    private val client = ClaudeClient(prefs)
    private val tools = ToolExecutor(app, prefs) { msg -> askConfirm(msg) }
    private val agent = Agent(prefs, client, tools)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var mode by mutableStateOf(Mode.IDLE); private set
    var emotion by mutableStateOf(Emotion.CALM); private set
    var level by mutableStateOf(0f); private set
    var heard by mutableStateOf(""); private set
    var reply by mutableStateOf(""); private set
    var status by mutableStateOf(""); private set
    var setup by mutableStateOf(SetupState()); private set

    /** True while the Sunday screen is in front. */
    var appInForeground = false

    /** True after "Hey Sunday": she keeps the conversation going hands-free until you go quiet. */
    private var wakeSession = false

    private var job: Job? = null
    private var greeted = false
    private val emoRegex = Regex("<emo>\\s*(\\w+)\\s*</emo>")

    init {
        voice.onSpeakDone = {
            if (job?.isActive == true) {
                mode = Mode.THINKING
            } else {
                mode = Mode.IDLE
                if (prefs.conversationMode && (appInForeground || wakeSession)) startListening()
            }
        }
        voice.onLevel = { level = it }
    }

    fun refreshSetup() {
        val enabled = Settings.Secure.getString(
            app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val mic = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val acc = enabled.contains(app.packageName) && enabled.contains("SundayAccessibilityService")
        setup = SetupState(mic, acc, Settings.canDrawOverlays(app), prefs.apiKey.isNotBlank())
    }

    /** One short proactive greeting per app launch. */
    fun onOpened() {
        if (greeted || !prefs.greetOnOpen || prefs.apiKey.isBlank()) return
        greeted = true
        submit(
            "[system: King just opened the app. Greet him in one short, playful line. " +
                "If it is late night or early morning, you may tease him gently about it.]",
            showUser = false
        )
    }

    /** Called by the hotword service when "Hey Sunday" is heard. */
    fun onWake() {
        if (mode != Mode.IDLE) return
        refreshSetup()
        wakeSession = true
        emotion = Emotion.HAPPY
        reply = "Haan King?"
        mode = Mode.SPEAKING
        voice.speak("Haan King?")
    }

    fun onOrbTap() {
        when (mode) {
            Mode.IDLE -> {
                wakeSession = false
                startListening()
            }
            Mode.LISTENING -> {
                voice.stopListening()
                wakeSession = false
                mode = Mode.IDLE
                level = 0f
            }
            Mode.SPEAKING -> {
                voice.stopSpeaking()
                wakeSession = false
                mode = if (job?.isActive == true) Mode.THINKING else Mode.IDLE
            }
            Mode.THINKING -> {
                job?.cancel()
                voice.stopSpeaking()
                wakeSession = false
                mode = Mode.IDLE
                status = "Ruk gayi."
            }
        }
    }

    fun startListening() {
        if (!setup.mic) {
            refreshSetup()
            if (!setup.mic) {
                status = "Mic permission do."
                return
            }
        }
        mode = Mode.LISTENING
        status = "Sun rahi hoon..."
        heard = ""
        voice.listen(
            onPartialCb = { heard = it },
            onFinalCb = {
                status = ""
                submit(it)
            },
            onErrorCb = { code ->
                status = when (code) {
                    -1 -> "Speech recognizer nahi mila. Google app enable/update karo."
                    9 -> "Mic permission do."
                    6, 7 -> ""
                    else -> "Mic error $code"
                }
                // You went quiet: end the hands-free session so the hotword can take over again.
                wakeSession = false
                mode = Mode.IDLE
                level = 0f
            }
        )
    }

    fun submit(text: String, showUser: Boolean = true) {
        if (text.isBlank()) return
        job?.cancel()
        voice.stopSpeaking()
        if (showUser) heard = text
        mode = Mode.THINKING
        emotion = Emotion.CURIOUS
        reply = ""
        KeepAliveService.start(app)

        job = scope.launch {
            try {
                val out = agent.run(text, onSay = { say(it) }, onStatus = { status = it })
                if (out.isNotBlank()) say(out)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emotion = Emotion.CONCERNED
                reply = "Error: ${e.message}"
                voice.speak("Kuch gadbad ho gayi. Details screen par hain.")
            } finally {
                status = ""
                KeepAliveService.stop(app)
                if (mode == Mode.THINKING) mode = Mode.IDLE
            }
        }
    }

    private fun say(raw: String) {
        emoRegex.find(raw)?.let { emotion = Emotion.from(it.groupValues[1]) }
        val clean = raw.replace(emoRegex, "").replace("*", "").replace("#", "").trim()
        if (clean.isBlank()) return
        reply = clean
        mode = Mode.SPEAKING
        voice.speak(clean)
    }

    /** Called by tools for risky steps: speaks the question and shows an Allow/Deny card over any app. */
    private suspend fun askConfirm(msg: String): Boolean {
        emotion = Emotion.CONCERNED
        voice.speak(msg)
        return ConfirmOverlay.ask(app, msg)
    }

    fun cycleVoice() {
        status = voice.cycleVoice()
    }
}
