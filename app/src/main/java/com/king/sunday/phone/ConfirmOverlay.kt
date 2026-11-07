package com.king.sunday.phone

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * A small "Allow / Deny" card drawn over whatever app is on screen, so King can approve risky steps
 * even while Sunday is working inside another app. Needs the "Display over other apps" permission.
 * No answer within 60 seconds counts as "no".
 */
object ConfirmOverlay {

    suspend fun ask(ctx: Context, message: String): Boolean {
        if (!Settings.canDrawOverlays(ctx)) return false
        val main = Handler(Looper.getMainLooper())

        return withTimeoutOrNull(60_000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                main.post {
                    val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val box = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(48, 40, 48, 32)
                        background = GradientDrawable().apply {
                            setColor(0xF2141428.toInt())
                            cornerRadius = 40f
                            setStroke(3, 0xFF7C5CFF.toInt())
                        }
                    }
                    val tv = TextView(ctx).apply {
                        text = message
                        setTextColor(Color.WHITE)
                        textSize = 16f
                    }
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.END
                        setPadding(0, 24, 0, 0)
                    }

                    var resolved = false
                    fun finish(answer: Boolean) {
                        if (resolved) return
                        resolved = true
                        try {
                            wm.removeView(box)
                        } catch (e: Exception) {
                        }
                        if (cont.isActive) cont.resume(answer)
                    }

                    row.addView(Button(ctx).apply {
                        text = "Nahi"
                        setOnClickListener { finish(false) }
                    })
                    row.addView(Button(ctx).apply {
                        text = "Haan, karo"
                        setOnClickListener { finish(true) }
                    })
                    box.addView(tv)
                    box.addView(row)

                    val lp = WindowManager.LayoutParams(
                        (ctx.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        y = 140
                    }

                    try {
                        wm.addView(box, lp)
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(false)
                        return@post
                    }

                    cont.invokeOnCancellation {
                        main.post {
                            try {
                                wm.removeView(box)
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
        } ?: false
    }
}
