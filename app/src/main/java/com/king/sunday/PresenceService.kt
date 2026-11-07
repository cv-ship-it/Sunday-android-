package com.king.sunday

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.king.sunday.ui.MiniOrbView
import com.king.sunday.ui.Mode

/**
 * Sunday's always-on presence:
 *  - the floating mini orb over every app (if enabled)
 *  - the "Hey Sunday" wake phrase (if enabled)
 *
 * The wake phrase uses Android's built-in speech recogniser in a loop, so it needs no extra key,
 * but it is not a real low-power hotword engine: it uses more battery and some phones may make a tiny
 * recogniser sound. It pauses while Sunday is talking, thinking or already listening.
 */
class PresenceService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var orb: MiniOrbView? = null
    private var running = true
    private var recognizerActive = false
    private var restartAt = 0L

    private val wakeWords = listOf(
        "sunday", "sun day", "sandey", "sanday", "sandy", "सनडे", "संडे", "सन्डे", "सण्डे"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = "sunday_presence"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "Sunday hamesha saath", NotificationManager.IMPORTANCE_MIN)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Sunday saath hai")
            .setContentText("\"Hey Sunday\" bolo ya orb dabao")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(8, n)
        main.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    /** Runs once a second: keeps the orb and the wake listener in line with the settings and with what Sunday is doing. */
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val core = sunday
            val p = core.prefs
            core.refreshSetup()

            if (p.floatingOrb && Settings.canDrawOverlays(this@PresenceService)) showOrb() else hideOrb()

            val micOk = core.setup.mic
            val shouldListen = p.hotword && micOk && core.mode == Mode.IDLE && !core.appInForeground
            if (shouldListen) {
                if (!recognizerActive && System.currentTimeMillis() >= restartAt) startWake()
            } else if (recognizerActive) {
                stopWake()
            }

            if (!p.hotword && !p.floatingOrb) {
                stopSelf()
                return
            }
            main.postDelayed(this, 1000)
        }
    }

    // ---------- Wake phrase ----------

    private fun muteBeep(mute: Boolean) {
        // Some phones beep every time the recogniser starts; keep the system stream quiet while we listen.
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(
                AudioManager.STREAM_NOTIFICATION,
                if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
        } catch (e: Exception) {
        }
    }

    private fun startWake() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            restartAt = System.currentTimeMillis() + 30_000
            return
        }
        recognizerActive = true
        muteBeep(true)
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                finishWake(delayMs = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 3000 else 400)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                matchWake(partialResults)
            }

            override fun onResults(results: Bundle?) {
                if (!matchWake(results)) finishWake(delayMs = 300)
            }

            private fun matchWake(b: Bundle?): Boolean {
                val heard = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.joinToString(" ")?.lowercase() ?: return false
                if (wakeWords.none { heard.contains(it) }) return false
                finishWake(delayMs = 1500)
                sunday.onWake()
                return true
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sunday.prefs.sttLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        r.startListening(i)
    }

    private fun finishWake(delayMs: Long) {
        stopWake()
        restartAt = System.currentTimeMillis() + delayMs
    }

    private fun stopWake() {
        recognizerActive = false
        recognizer?.destroy()
        recognizer = null
        muteBeep(false)
    }

    // ---------- Floating orb ----------

    private fun showOrb() {
        if (orb != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = (resources.displayMetrics.density * 72).toInt()
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - 16
            y = (resources.displayMetrics.heightPixels * 0.35f).toInt()
        }
        val v = MiniOrbView(this, sunday, lp, wm) {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        try {
            wm.addView(v, lp)
            orb = v
        } catch (e: Exception) {
        }
    }

    private fun hideOrb() {
        val v = orb ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
        }
        orb = null
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        stopWake()
        hideOrb()
        super.onDestroy()
    }

    companion object {
        /** Start or stop the service to match the current settings. Safe to call any time. */
        fun sync(ctx: Context) {
            val p = ctx.sunday.prefs
            val i = Intent(ctx, PresenceService::class.java)
            if (p.hotword || p.floatingOrb) {
                try {
                    ContextCompat.startForegroundService(ctx, i)
                } catch (e: Exception) {
                }
            } else {
                ctx.stopService(i)
            }
        }
    }
}
