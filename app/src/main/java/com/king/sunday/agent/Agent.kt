package com.king.sunday.agent

import com.king.sunday.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The think -> act -> observe loop. Keeps a rolling conversation in memory. */
class Agent(
    private val prefs: Prefs,
    private val client: ClaudeClient,
    private val tools: ToolExecutor
) {
    private val history = JSONArray()

    suspend fun run(
        userText: String,
        onSay: (String) -> Unit,
        onStatus: (String) -> Unit
    ): String {
        repairHistory()
        trimHistory()

        val stamp = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        history.put(JSONObject().put("role", "user").put("content", "[time: $stamp] $userText"))

        val system = Persona.system(prefs.name, prefs.facts())
        val toolDefs = ToolDefs.all()
        var finalText = ""

        for (step in 0 until 30) {
            val resp = client.send(system, history, toolDefs)
            val content = resp.getJSONArray("content")
            history.put(JSONObject().put("role", "assistant").put("content", content))

            val text = StringBuilder()
            val uses = mutableListOf<JSONObject>()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                when (block.optString("type")) {
                    "text" -> text.append(block.optString("text"))
                    "tool_use" -> uses.add(block)
                }
            }

            if (uses.isEmpty() || resp.optString("stop_reason") != "tool_use") {
                finalText = text.toString()
                break
            }

            if (text.isNotBlank()) onSay(text.toString())

            val results = JSONArray()
            for (use in uses) {
                val name = use.getString("name")
                onStatus(statusFor(name))
                val out = tools.execute(name, use.optJSONObject("input") ?: JSONObject())
                val c = JSONArray()
                if (out.imageB64 != null) {
                    c.put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", out.imageB64)
                        )
                    )
                }
                c.put(JSONObject().put("type", "text").put("text", out.text))
                results.put(
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", use.getString("id"))
                        .put("content", c)
                )
            }
            history.put(JSONObject().put("role", "user").put("content", results))

            if (step == 29) {
                finalText = "<emo>concerned</emo>Itne steps ho gaye, main yahin ruk rahi hoon. Batao aage kya karu?"
            }
        }

        stripImages()
        onStatus("")
        return finalText
    }

    private fun statusFor(tool: String) = when (tool) {
        "read_screen" -> "Screen padh rahi hoon..."
        "screenshot" -> "Screen dekh rahi hoon..."
        "open_app", "open_app_info", "open_settings", "play_store_search", "open_url" -> "App khol rahi hoon..."
        "click_node", "click_text", "tap" -> "Tap kar rahi hoon..."
        "type_text" -> "Type kar rahi hoon..."
        "scroll" -> "Scroll kar rahi hoon..."
        "get_device_status" -> "Phone check kar rahi hoon..."
        "call" -> "Call ki taiyari..."
        "agni_submit", "agni_status" -> "Agni se baat kar rahi hoon..."
        "remember" -> "Yaad rakh rahi hoon..."
        else -> "Kaam chal raha hai..."
    }

    private fun isPlainUser(o: JSONObject) =
        o.optString("role") == "user" && o.opt("content") is String

    /** Keep the window small, always starting on a plain user turn so tool_use/tool_result pairs stay intact. */
    private fun trimHistory() {
        while (history.length() > 0 &&
            (history.length() > 40 || !isPlainUser(history.getJSONObject(0)))
        ) {
            history.remove(0)
        }
    }

    /** If a previous run was cancelled mid tool-call, answer the dangling tool_use so the API accepts the next turn. */
    private fun repairHistory() {
        if (history.length() == 0) return
        val last = history.getJSONObject(history.length() - 1)
        if (last.optString("role") != "assistant") return
        val content = last.optJSONArray("content") ?: return
        val results = JSONArray()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            if (b.optString("type") == "tool_use") {
                results.put(
                    JSONObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", b.getString("id"))
                        .put("content", "Cancelled by King.")
                )
            }
        }
        if (results.length() > 0) {
            history.put(JSONObject().put("role", "user").put("content", results))
        }
    }

    /** Screenshots are heavy; once a turn is done, replace them with a placeholder. */
    private fun stripImages() {
        for (i in 0 until history.length()) {
            val m = history.getJSONObject(i)
            val arr = m.optJSONArray("content") ?: continue
            for (j in 0 until arr.length()) {
                val block = arr.optJSONObject(j) ?: continue
                if (block.optString("type") != "tool_result") continue
                val inner = block.optJSONArray("content") ?: continue
                for (k in 0 until inner.length()) {
                    val ib = inner.optJSONObject(k) ?: continue
                    if (ib.optString("type") == "image") {
                        inner.put(k, JSONObject().put("type", "text").put("text", "[screenshot omitted]"))
                    }
                }
            }
        }
    }
}
