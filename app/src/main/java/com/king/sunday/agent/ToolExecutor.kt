package com.king.sunday.agent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import com.king.sunday.MainActivity
import com.king.sunday.data.Prefs
import com.king.sunday.phone.SundayAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ToolOutput(val text: String, val imageB64: String? = null)

/**
 * Runs the tools Claude asks for and enforces the safety rules in code (not just in the prompt):
 *  - banking / UPI / wallet / password apps are never read or operated
 *  - risky taps and calls need King's on-screen confirmation
 *  - Play Store installs need confirmation unless "trusted installs" is on
 */
class ToolExecutor(
    private val ctx: Context,
    private val prefs: Prefs,
    private val confirm: suspend (String) -> Boolean
) {
    private val svc: SundayAccessibilityService? get() = SundayAccessibilityService.instance

    private val blockedHints = listOf(
        "bank", "upi", "paytm", "phonepe", "paisa", "bhim", "wallet", "payment", "paypal",
        "authenticator", "lastpass", "bitwarden", "1password", "keepass", "passwords",
        "hdfc", "icici", "sbi", "axis", "kotak", "razorpay", "mobikwik", "freecharge", "dreamplug"
    )

    private val riskyLabel = Regex(
        "install anyway|uninstall|delete|erase|factory reset|format|pay now|\\bpay\\b|transfer|send money|" +
            "unknown apps|unknown sources|play protect|sign out|log out|forget",
        RegexOption.IGNORE_CASE
    )

    private val noSvc = ToolOutput(
        "Accessibility service is OFF. Tell King to turn on Sunday in Settings > Accessibility > Installed services."
    )

    suspend fun execute(name: String, input: JSONObject): ToolOutput = try {
        dispatch(name, input)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ToolOutput("Error in $name: ${e.message}")
    }

    private suspend fun dispatch(name: String, input: JSONObject): ToolOutput {
        when (name) {
            "get_device_status" -> return ToolOutput(deviceStatus())

            "open_app" -> {
                val app = findApp(input.getString("app_name"))
                    ?: return ToolOutput("No installed app matches '${input.getString("app_name")}'.")
                launch(ctx.packageManager.getLaunchIntentForPackage(app.second) ?: return ToolOutput("Cannot launch."))
                delay(1200)
                return ToolOutput("Opened ${app.first}.")
            }

            "open_app_info" -> {
                val app = findApp(input.getString("app_name")) ?: return ToolOutput("App not found.")
                launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.second}"))
                )
                delay(1000)
                return ToolOutput("Opened App info for ${app.first}.")
            }

            "open_settings" -> {
                val action = when (input.getString("page")) {
                    "wifi" -> Settings.ACTION_WIFI_SETTINGS
                    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                    "battery_saver" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                    "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                    "display" -> Settings.ACTION_DISPLAY_SETTINGS
                    "sound" -> Settings.ACTION_SOUND_SETTINGS
                    "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    "wireless" -> Settings.ACTION_WIRELESS_SETTINGS
                    "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                launch(Intent(action))
                delay(1000)
                return ToolOutput("Opened settings page '${input.getString("page")}'.")
            }

            "play_store_search" -> {
                val q = Uri.encode(input.getString("query"))
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps"))
                    .setPackage("com.android.vending")
                launch(i)
                delay(2500)
                return ToolOutput("Play Store search opened for '${input.getString("query")}'. Call read_screen next.")
            }

            "open_url" -> {
                val url = input.getString("url")
                if (!url.startsWith("http://") && !url.startsWith("https://")) return ToolOutput("Only http(s) links allowed.")
                launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                delay(1500)
                return ToolOutput("Opened link.")
            }

            "read_screen" -> {
                val s = svc ?: return noSvc
                blockedMessage(s)?.let { return ToolOutput(it) }
                return ToolOutput(s.dumpScreen())
            }

            "screenshot" -> {
                val s = svc ?: return noSvc
                blockedMessage(s)?.let { return ToolOutput(it) }
                val img = s.screenshotBase64() ?: return ToolOutput("Screenshot failed (protected screen or unsupported).")
                return ToolOutput("Screenshot attached.", img)
            }

            "click_node" -> {
                val s = svc ?: return noSvc
                blockedMessage(s)?.let { return ToolOutput(it) }
                val idx = input.getInt("index")
                val label = s.nodeLabel(idx) ?: return ToolOutput("Invalid index. Call read_screen again.")
                gate(label, s.currentPackage())?.let { return ToolOutput(it) }
                val ok = s.clickNode(idx)
                delay(700)
                return ToolOutput(if (ok) "Clicked [$idx] $label." else "Click failed on [$idx].")
            }

            "click_text" -> {
                val s = svc ?: return noSvc
                blockedMessage(s)?.let { return ToolOutput(it) }
                val t = input.getString("text")
                gate(t, s.currentPackage())?.let { return ToolOutput(it) }
                val ok = s.clickText(t)
                delay(700)
                return ToolOutput(if (ok) "Clicked '$t'." else "No visible element with text '$t'.")
            }

            "tap" -> {
                val s = svc ?: return noSvc
                blockedMessage(s)?.let { return ToolOutput(it) }
                val ok = s.tap(input.getDouble("x").toFloat(), input.getDouble("y").toFloat())
                delay(700)
                return ToolOutput(if (ok) "Tapped." else "Tap failed.")
            }

            "type_text" -> {
                val s = svc ?: return noSvc
                blockedMessage(s)?.let { return ToolOutput(it) }
                val ok = s.typeText(input.getString("text"), input.optBoolean("submit", false))
                delay(500)
                return ToolOutput(if (ok) "Typed." else "No input field found.")
            }

            "scroll" -> {
                val s = svc ?: return noSvc
                blockedMessage(s)?.let { return ToolOutput(it) }
                val ok = s.scroll(input.getString("direction") == "down")
                delay(600)
                return ToolOutput(if (ok) "Scrolled." else "Scroll failed.")
            }

            "press" -> {
                val s = svc ?: return noSvc
                val ok = s.press(input.getString("button"))
                delay(600)
                return ToolOutput(if (ok) "Pressed ${input.getString("button")}." else "Press failed.")
            }

            "call" -> return call(input.getString("who"))

            "set_alarm" -> {
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, input.getInt("hour"))
                    .putExtra(AlarmClock.EXTRA_MINUTES, input.getInt("minute"))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, input.optString("label", "Sunday alarm"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                launch(i)
                return ToolOutput("Alarm set for %02d:%02d.".format(input.getInt("hour"), input.getInt("minute")))
            }

            "set_flashlight" -> {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = cm.cameraIdList.firstOrNull {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return ToolOutput("No flashlight found.")
                val on = input.getBoolean("on")
                cm.setTorchMode(id, on)
                return ToolOutput("Flashlight ${if (on) "on" else "off"}.")
            }

            "set_volume" -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val pct = input.getInt("percent").coerceIn(0, 100)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, pct * max / 100, 0)
                return ToolOutput("Volume set to $pct%.")
            }

            "agni_submit" ->
                return agniCall("POST", "/jobs", JSONObject().put("task", input.getString("task")))

            "agni_status" -> {
                val id = input.optString("job_id", "")
                return agniCall("GET", if (id.isBlank()) "/jobs?limit=5" else "/jobs/$id", null)
            }

            "remember" -> {
                prefs.addFact(input.getString("fact"))
                return ToolOutput("Saved.")
            }

            "wait" -> {
                delay((input.optDouble("seconds", 1.0).coerceIn(0.5, 5.0) * 1000).toLong())
                return ToolOutput("Waited.")
            }

            "return_to_sunday" -> {
                launch(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                return ToolOutput("Sunday brought to front.")
            }

            else -> return ToolOutput("Unknown tool: $name")
        }
    }

    // ---------- Safety ----------

    private fun blockedMessage(s: SundayAccessibilityService): String? {
        val pkg = s.currentPackage()?.lowercase() ?: return null
        return if (blockedHints.any { pkg.contains(it) })
            "BLOCKED: this looks like a banking/payment/password app ($pkg). Sunday never operates these. Ask King to do it himself."
        else null
    }

    /** Returns a refusal text if King declines, or null to proceed. */
    private suspend fun gate(label: String, pkg: String?): String? {
        val l = label.trim()
        val isInstall = pkg == "com.android.vending" &&
            Regex("^(install|update|get)$", RegexOption.IGNORE_CASE).matches(l)
        if (isInstall && !prefs.trustedInstalls) {
            if (!confirm("Play Store par '$l' dabana hai. App sahi hai na? Allow karun?")) return "King declined the install."
        } else if (riskyLabel.containsMatchIn(l)) {
            if (!confirm("Ye button dabana hai: '$l'. Theek hai?")) return "King declined this action."
        }
        return null
    }

    // ---------- Agni link ----------

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun agniCall(method: String, path: String, body: JSONObject?): ToolOutput =
        withContext(Dispatchers.IO) {
            if (prefs.agniUrl.isBlank() || prefs.agniToken.isBlank())
                return@withContext ToolOutput("Agni server set nahi hai. Settings mein Agni URL aur token daalo.")
            val b = body?.toString()?.toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url(prefs.agniUrl + path)
                .header("Authorization", "Bearer ${prefs.agniToken}")
                .method(method, if (method == "GET") null else b)
                .build()
            try {
                http.newCall(req).execute().use { r ->
                    val t = r.body?.string().orEmpty().take(3000)
                    ToolOutput(if (r.isSuccessful) t else "Agni error ${r.code}: $t")
                }
            } catch (e: Exception) {
                ToolOutput("Agni server tak pahunch nahi paayi: ${e.message}")
            }
        }

    // ---------- Helpers ----------

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun findApp(query: String): Pair<String, String>? {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0).map {
            it.loadLabel(pm).toString() to it.activityInfo.packageName
        }
        val q = query.trim().lowercase()
        return apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.firstOrNull { it.first.lowercase().startsWith(q) }
            ?: apps.firstOrNull { it.first.lowercase().contains(q) }
    }

    private suspend fun call(who: String): ToolOutput {
        var number = who.filter { it.isDigit() || it == '+' }
        var display = who
        if (number.length < 5) {
            val c = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$who%"),
                null
            )
            var found: Pair<String, String>? = null
            c?.use { if (it.moveToFirst()) found = it.getString(0) to it.getString(1) }
            val f = found ?: return ToolOutput("No contact matches '$who' (or contacts permission is missing).")
            display = f.first
            number = f.second
        }
        if (!confirm("$display ko call karun? ($number)")) return ToolOutput("King declined the call.")
        launch(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))))
        return ToolOutput("Calling $display.")
    }

    private fun deviceStatus(): String {
        val bi = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val sf = StatFs(Environment.getDataDirectory().path)
        val freeGb = sf.availableBytes / 1e9
        val totalGb = sf.totalBytes / 1e9
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "other"
        }
        val time = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date())
        val fg = svc?.currentPackage() ?: "unknown (accessibility off)"
        return "battery=${if (level >= 0) level * 100 / scale else -1}% charging=$plugged; " +
            "storage free=%.1f GB of %.1f GB; ".format(freeGb, totalGb) +
            "ram free=%.1f GB of %.1f GB; network=$net; time=$time; foreground=$fg".format(
                mi.availMem / 1e9, mi.totalMem / 1e9
            )
    }
}
