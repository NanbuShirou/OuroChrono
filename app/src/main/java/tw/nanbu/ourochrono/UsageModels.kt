package tw.nanbu.ourochrono

import org.json.JSONArray
import org.json.JSONObject

data class AccountInfo(
    val authenticated: Boolean,
    val email: String?,
    val planType: String?,
    val authMode: String?
)

data class RateLimitWindow(
    val limitId: String,
    val limitName: String?,
    val scope: String?,
    val usedPercent: Int,
    val windowDurationMinutes: Int,
    val resetsAtEpochSeconds: Long?
) {
    val remainingPercent: Int
        get() = (100 - usedPercent).coerceIn(0, 100)
}

data class UsageSnapshot(
    val windows: List<RateLimitWindow>,
    val planType: String?,
    val resetCredits: Int?,
    val updatedAtEpochMillis: Long,
    val stale: Boolean = false,
    val error: String? = null
) {
    fun weeklyWindow(): RateLimitWindow? {
        return findWindowByDuration(WEEK_MINUTES)
    }

    fun shortWindow(): RateLimitWindow? {
        return findWindowByDuration(FIVE_HOUR_MINUTES)
    }

    private fun findWindowByDuration(durationMinutes: Int): RateLimitWindow? {
        // primary/secondary are response slots, not semantic window types. Only the
        // actual duration decides whether a record is the five-hour or weekly meter.
        return windows
            .asSequence()
            .filter { window -> window.windowDurationMinutes == durationMinutes }
            .sortedWith(
                compareBy<RateLimitWindow> { window ->
                    if (window.resetsAtEpochSeconds == null) 1 else 0
                }.thenBy { window -> window.limitId }
            )
            .firstOrNull()
    }

    fun toJson(): JSONObject {
        val windowArray = JSONArray()
        windows.forEach { window ->
            windowArray.put(JSONObject().apply {
                put("limitId", window.limitId)
                put("limitName", window.limitName ?: JSONObject.NULL)
                put("scope", window.scope ?: JSONObject.NULL)
                put("usedPercent", window.usedPercent)
                put("windowDurationMins", window.windowDurationMinutes)
                put("resetsAt", window.resetsAtEpochSeconds ?: JSONObject.NULL)
            })
        }

        return JSONObject().apply {
            put("windows", windowArray)
            put("planType", planType ?: JSONObject.NULL)
            put("resetCredits", resetCredits ?: JSONObject.NULL)
            put("updatedAt", updatedAtEpochMillis)
            put("stale", stale)
            put("error", error ?: JSONObject.NULL)
        }
    }

    companion object {
        private const val FIVE_HOUR_MINUTES = 300
        private const val WEEK_MINUTES = 10_080

        fun fromJson(json: JSONObject): UsageSnapshot {
            val windows = mutableListOf<RateLimitWindow>()
            val array = json.optJSONArray("windows") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                windows += RateLimitWindow(
                    limitId = item.optString("limitId", "unknown-$index"),
                    limitName = item.optNullableString("limitName"),
                    scope = item.optNullableString("scope"),
                    usedPercent = item.optInt("usedPercent", 0).coerceIn(0, 100),
                    windowDurationMinutes = item.optInt("windowDurationMins", 0),
                    resetsAtEpochSeconds = item.optNullableLong("resetsAt")
                )
            }

            return UsageSnapshot(
                windows = windows,
                planType = json.optNullableString("planType"),
                resetCredits = json.optNullableInt("resetCredits"),
                updatedAtEpochMillis = json.optLong("updatedAt", System.currentTimeMillis()),
                stale = json.optBoolean("stale", false),
                error = json.optNullableString("error")
            )
        }
    }
}

internal fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() && it != "null" }
}

internal fun JSONObject.optNullableLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return optLong(key)
}

internal fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}
