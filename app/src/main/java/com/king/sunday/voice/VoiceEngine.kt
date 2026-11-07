package com.king.sunday.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.king.sunday.data.Prefs
import java.util.Locale

/** Ears (SpeechRecognizer) and voice (TextToSpeech). Use Google's speech services on the phone for best quality. */
class VoiceEngine(private val ctx: Context, private val prefs: Prefs) {

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var pending = 0

    /** Called on the main thread when every queued sentence has been spoken. */
    var onSpeakDone: (() -> Unit)? = null

    /** Mic loudness 0..1 while listening, to animate the orb. */
    var onLevel: ((Float) -> Unit)? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) applyVoice()
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                finishOne()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishOne()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {}
        })
    }

    private fun finishOne() {
        main.post {
            pending = (pending - 1).coerceAtLeast(0)
            if (pending == 0) onSpeakDone?.invoke()
        }
    }

    // ---------- Speaking ----------

    fun applyVoice() {
        val t = tts ?: return
        try {
            val loc = Locale.forLanguageTag(prefs.ttsLang)
            if (t.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) t.language = loc
            t.setPitch(1.15f)
            t.setSpeechRate(1.0f)
            val chosen = t.voices?.firstOrNull { it.name == prefs.voiceName }
            if (chosen != null) t.voice = chosen
        } catch (e: Exception) {
        }
    }

    fun speak(text: String) {
        val t = tts ?: return
        if (text.isBlank()) return
        main.post {
            pending++
            t.speak(text, TextToSpeech.QUEUE_ADD, null, "u${System.nanoTime()}")
        }
    }

    fun stopSpeaking() {
        main.post {
            tts?.stop()
            pending = 0
        }
    }

    private fun voiceChoices(): List<android.speech.tts.Voice> {
        val lang = Locale.forLanguageTag(prefs.ttsLang).language
        return try {
            tts?.voices?.filter { it.locale.language == lang }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Switch to the next installed voice for the chosen language and say a sample line. Returns the voice name. */
    fun cycleVoice(): String {
        val list = voiceChoices()
        if (list.isEmpty()) return "Koi voice nahi mili. Google Text-to-speech install karo."
        val idx = list.indexOfFirst { it.name == prefs.voiceName }
        val next = list[(idx + 1) % list.size]
        prefs.voiceName = next.name
        applyVoice()
        stopSpeaking()
        speak("Hi King, kaisi lag rahi hai meri awaaz?")
        return next.name
    }

    // ---------- Listening ----------

    fun listen(onPartialCb: (String) -> Unit, onFinalCb: (String) -> Unit, onErrorCb: (Int) -> Unit) {
        main.post {
            stopListeningInternal()
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                onErrorCb(-1)
                return@post
            }
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    onLevel?.invoke(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    onLevel?.invoke(0f)
                }

                override fun onError(error: Int) {
                    onLevel?.invoke(0f)
                    onErrorCb(error)
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) onErrorCb(SpeechRecognizer.ERROR_NO_MATCH) else onFinalCb(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.let(onPartialCb)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.sttLang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }
            r.startListening(i)
        }
    }

    fun stopListening() {
        main.post { stopListeningInternal() }
    }

    private fun stopListeningInternal() {
        recognizer?.destroy()
        recognizer = null
        onLevel?.invoke(0f)
    }

    fun shutdown() {
        main.post {
            stopListeningInternal()
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}
