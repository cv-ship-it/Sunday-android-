package com.king.sunday.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Sunday's hands: reads the screen as a numbered list and performs taps, typing, scrolling and system buttons.
 * Every call is initiated by ToolExecutor, which applies the safety rules (blocked apps, confirmations).
 */
class SundayAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SundayAccessibilityService? = null
    }

    private val nodes = mutableListOf<AccessibilityNodeInfo>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

    // ---------- Reading ----------

    fun dumpScreen(): String {
        nodes.clear()
        val root = rootInActiveWindow ?: return "No active window (screen off, or this screen is protected)."
        val sb = StringBuilder("package=${root.packageName}\n")
        walk(root, sb)
        if (nodes.isEmpty()) sb.append("(no readable elements; try screenshot)")
        return sb.toString().take(9000)
    }

    private fun walk(n: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (n == null || nodes.size >= 150) return
        if (n.isVisibleToUser) {
            val text = n.text?.toString()?.take(80)
            val desc = n.contentDescription?.toString()?.take(80)
            val interesting = n.isClickable || n.isEditable || n.isScrollable || n.isCheckable ||
                !text.isNullOrBlank() || !desc.isNullOrBlank()
            if (interesting) {
                val idx = nodes.size
                nodes.add(n)
                sb.append("[$idx] ").append(n.className?.toString()?.substringAfterLast('.') ?: "View")
                if (!text.isNullOrBlank()) sb.append(" \"").append(text).append('"')
                if (!desc.isNullOrBlank() && desc != text) sb.append(" desc=\"").append(desc).append('"')
                val flags = mutableListOf<String>()
                if (n.isClickable) flags.add("click")
                if (n.isEditable) flags.add("edit")
                if (n.isScrollable) flags.add("scroll")
                if (n.isChecked) flags.add("checked")
                if (flags.isNotEmpty()) sb.append(' ').append(flags.joinToString(","))
                sb.append('\n')
            }
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), sb)
    }

    fun nodeLabel(index: Int): String? {
        val n = nodes.getOrNull(index) ?: return null
        return n.text?.toString() ?: n.contentDescription?.toString() ?: n.className?.toString()
    }

    // ---------- Acting ----------

    suspend fun clickNode(index: Int): Boolean {
        val n = nodes.getOrNull(index) ?: return false
        return clickInfo(n)
    }

    suspend fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val match = root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isVisibleToUser } ?: return false
        return clickInfo(match)
    }

    private suspend fun clickInfo(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable && cur.isEnabled && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            hops++
        }
        val r = Rect()
        n.getBoundsInScreen(r)
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    suspend fun tap(x: Float, y: Float): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onCancelled(g: GestureDescription?) {
                if (cont.isActive) cont.resume(false)
            }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }

    suspend fun scroll(down: Boolean): Boolean = suspendCancellableCoroutine { cont ->
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val from = dm.heightPixels * (if (down) 0.72f else 0.28f)
        val to = dm.heightPixels * (if (down) 0.28f else 0.72f)
        val path = Path().apply {
            moveTo(x, from)
            lineTo(x, to)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onCancelled(g: GestureDescription?) {
                if (cont.isActive) cont.resume(false)
            }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }

    fun typeText(text: String, submit: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok && submit && Build.VERSION.SDK_INT >= 30) {
            target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return ok
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            findEditable(n.getChild(i))?.let { return it }
        }
        return null
    }

    fun press(button: String): Boolean {
        val action = when (button) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return false
        }
        return performGlobalAction(action)
    }

    // ---------- Seeing (Android 11+) ----------

    suspend fun screenshotBase64(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        return shoot()
    }

    @RequiresApi(30)
    private suspend fun shoot(): String? = suspendCancellableCoroutine { cont ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val hb = result.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                    val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (soft == null) {
                        if (cont.isActive) cont.resume(null)
                        return
                    }
                    val w = 720
                    val h = (soft.height * (w / soft.width.toFloat())).toInt()
                    val scaled = Bitmap.createScaledBitmap(soft, w, h, true)
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    if (cont.isActive) cont.resume(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }

            override fun onFailure(errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
        })
    }
}
