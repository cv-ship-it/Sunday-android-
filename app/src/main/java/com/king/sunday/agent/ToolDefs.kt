package com.king.sunday.agent

import org.json.JSONArray
import org.json.JSONObject

/** Tool schemas sent to Claude. Execution lives in ToolExecutor. */
object ToolDefs {

    private fun prop(type: String, desc: String) =
        JSONObject().put("type", type).put("description", desc)

    private fun enumProp(desc: String, vararg values: String) =
        JSONObject().put("type", "string").put("description", desc)
            .put("enum", JSONArray(values.toList()))

    private fun tool(
        name: String,
        desc: String,
        props: JSONObject = JSONObject(),
        required: List<String> = emptyList()
    ) = JSONObject()
        .put("name", name)
        .put("description", desc)
        .put(
            "input_schema",
            JSONObject()
                .put("type", "object")
                .put("properties", props)
                .put("required", JSONArray(required))
        )

    fun all(): JSONArray = JSONArray().apply {
        put(
            tool(
                "get_device_status",
                "Battery, charging, free storage, free RAM, network type, current time and the app currently on screen. Use first when diagnosing a phone problem."
            )
        )
        put(
            tool(
                "open_app",
                "Launch an installed app by its visible name (e.g. 'YouTube', 'Settings', 'Play Store').",
                JSONObject().put("app_name", prop("string", "Visible app name")),
                listOf("app_name")
            )
        )
        put(
            tool(
                "open_app_info",
                "Open the system App info page of an installed app (for force stop, clear cache/storage, permissions, uninstall).",
                JSONObject().put("app_name", prop("string", "Visible app name")),
                listOf("app_name")
            )
        )
        put(
            tool(
                "open_settings",
                "Open a system settings page.",
                JSONObject().put(
                    "page",
                    enumProp(
                        "Which page",
                        "main", "wifi", "bluetooth", "battery_saver", "storage", "apps", "display",
                        "sound", "accessibility", "location", "wireless", "developer"
                    )
                ),
                listOf("page")
            )
        )
        put(
            tool(
                "play_store_search",
                "Open Google Play Store search results for an app name. Then use read_screen and click to open the right listing and install.",
                JSONObject().put("query", prop("string", "App name to search")),
                listOf("query")
            )
        )
        put(
            tool(
                "open_url",
                "Open an http(s) link in the browser.",
                JSONObject().put("url", prop("string", "Full http or https URL")),
                listOf("url")
            )
        )
        put(
            tool(
                "read_screen",
                "Return the visible UI as a numbered list of elements ([index] type \"text\" flags). Use the indexes with click_node. Cheaper than a screenshot."
            )
        )
        put(
            tool(
                "screenshot",
                "Take a screenshot and look at it. Use when read_screen text is not enough (images, games, custom UI)."
            )
        )
        put(
            tool(
                "click_node",
                "Click an element by its index from the latest read_screen.",
                JSONObject().put("index", prop("integer", "Index from read_screen")),
                listOf("index")
            )
        )
        put(
            tool(
                "click_text",
                "Click the visible element whose text matches. Use when you know the label.",
                JSONObject().put("text", prop("string", "Visible label")),
                listOf("text")
            )
        )
        put(
            tool(
                "tap",
                "Tap raw screen coordinates in pixels (last resort, e.g. after a screenshot).",
                JSONObject()
                    .put("x", prop("number", "X pixel"))
                    .put("y", prop("number", "Y pixel")),
                listOf("x", "y")
            )
        )
        put(
            tool(
                "type_text",
                "Type text into the focused input (or the first input on screen). Optionally press Enter/Search afterwards.",
                JSONObject()
                    .put("text", prop("string", "Text to type"))
                    .put("submit", prop("boolean", "Press the keyboard Enter/Search action after typing")),
                listOf("text")
            )
        )
        put(
            tool(
                "scroll",
                "Scroll the screen content.",
                JSONObject().put("direction", enumProp("Direction to scroll", "down", "up")),
                listOf("direction")
            )
        )
        put(
            tool(
                "press",
                "Press a system button.",
                JSONObject().put(
                    "button",
                    enumProp("Button", "back", "home", "recents", "notifications", "quick_settings")
                ),
                listOf("button")
            )
        )
        put(
            tool(
                "call",
                "Place a phone call to a contact name or number. Always asks King to confirm.",
                JSONObject().put("who", prop("string", "Contact name or phone number")),
                listOf("who")
            )
        )
        put(
            tool(
                "set_alarm",
                "Set an alarm (24h clock).",
                JSONObject()
                    .put("hour", prop("integer", "0-23"))
                    .put("minute", prop("integer", "0-59"))
                    .put("label", prop("string", "Alarm label")),
                listOf("hour", "minute")
            )
        )
        put(
            tool(
                "set_flashlight",
                "Turn the flashlight on or off.",
                JSONObject().put("on", prop("boolean", "true = on")),
                listOf("on")
            )
        )
        put(
            tool(
                "set_volume",
                "Set media volume.",
                JSONObject().put("percent", prop("integer", "0-100")),
                listOf("percent")
            )
        )
        put(
            tool(
                "agni_submit",
                "Send an engineering task to Agni, King's AI engineering/manufacturing system running on his server (e.g. design a mounting bracket for 5 kg load, run an FEM check). Returns a job id. Long jobs run in the background; check them with agni_status.",
                JSONObject().put("task", prop("string", "Plain-language engineering task with all constraints King mentioned")),
                listOf("task")
            )
        )
        put(
            tool(
                "agni_status",
                "Check an Agni job by id, or list the latest jobs if no id is given.",
                JSONObject().put("job_id", prop("string", "Job id, optional"))
            )
        )
        put(
            tool(
                "remember",
                "Save one durable, non-sensitive fact about King (preference, routine, project detail) for future conversations. Never save passwords, OTPs or card numbers.",
                JSONObject().put("fact", prop("string", "Short fact")),
                listOf("fact")
            )
        )
        put(
            tool(
                "wait",
                "Wait for the UI to settle (max 5 seconds).",
                JSONObject().put("seconds", prop("number", "Seconds, 1-5")),
                listOf("seconds")
            )
        )
        put(
            tool(
                "return_to_sunday",
                "Bring the Sunday app back to the front (use at the end of a task if King is waiting to see you)."
            )
        )
    }
}
