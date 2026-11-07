package com.king.sunday.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.ui.graphics.toArgb
import com.king.sunday.SundayCore
import kotlin.math.abs
import kotlin.math.sin

/**
 * The small orb that floats above every app. It reads the same emotion/mode as the big orb.
 * Tap = talk / stop. Drag = move. Long-press = open Sunday.
 */
class MiniOrbView(
    ctx: Context,
    private val core: SundayCore,
    private val lp: WindowManager.LayoutParams,
    private val wm: WindowManager,
    private val onOpenApp: () -> Unit
) : View(ctx) {

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val longPress = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false
    private var longFired = false
    private val longRunnable = Runnable {
        longFired = true
        onOpenApp()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cx = w / 2f
        val cy = height / 2f
        val t = System.currentTimeMillis() / 1000f
        val e = core.emotion
        val energy = when (core.mode) {
            Mode.LISTENING -> 0.4f + core.level * 0.6f
            Mode.SPEAKING -> 0.8f
            Mode.THINKING -> 0.55f
            Mode.IDLE -> 0.1f
        }
        val pulse = 1f + 0.05f * sin(t * 2f * e.speed) + energy * 0.10f
        val r = w * 0.27f * pulse
        val c1 = e.c1.toArgb()
        val c2 = e.c2.toArgb()

        glow.shader = RadialGradient(
            cx, cy, w * 0.5f,
            intArrayOf(withAlpha(c1, 110), Color.TRANSPARENT), null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, w * 0.5f, glow)

        ring.color = withAlpha(c2, 170)
        canvas.drawCircle(cx, cy, r * (1.25f + 0.06f * sin(t * 3f * e.speed)), ring)

        body.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.3f, r * 1.2f,
            intArrayOf(Color.WHITE, c2, c1), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, body)

        postInvalidateOnAnimation()
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                startX = lp.x
                startY = lp.y
                moved = false
                longFired = false
                postDelayed(longRunnable, longPress)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    moved = true
                    removeCallbacks(longRunnable)
                }
                if (moved) {
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    try {
                        wm.updateViewLayout(this, lp)
                    } catch (e: Exception) {
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longRunnable)
                if (!moved && !longFired) core.onOrbTap()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longRunnable)
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
