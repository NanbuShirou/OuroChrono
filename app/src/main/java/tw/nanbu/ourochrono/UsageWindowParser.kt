package tw.nanbu.ourochrono

import org.json.JSONObject
import kotlin.math.roundToInt

internal object UsageWindowParser {
    fun parse(
        json: JSONObject?,
        fallbackLimitId: String,
        sourceSlot: String
    ): RateLimitWindow? {
        if (json == null) return null

        val usedPercent = when {
            json.has("used_percent") -> json.optDouble("used_percent", 0.0).roundToInt()
            json.has("usedPercent") -> json.optDouble("usedPercent", 0.0).roundToInt()
            else -> return null
        }.coerceIn(0, 100)

        val durationMinutes = readDurationMinutes(json)
        val resetAt = firstPositiveLong(
            json.optLong("reset_at", 0L),
            json.optLong("resets_at", 0L),
            json.optLong("resetsAt", 0L)
        ) ?: firstPositiveLong(
            json.optLong("reset_after_seconds", 0L),
            json.optLong("resetAfterSeconds", 0L)
        )?.let { delaySeconds ->
            System.currentTimeMillis() / 1_000 + delaySeconds
        }

        return RateLimitWindow(
            limitId = json.optNullableString("limit_id")
                ?: json.optNullableString("limitId")
                ?: json.optNullableString("id")
                ?: fallbackLimitId,
            limitName = json.optNullableString("limit_name")
                ?: json.optNullableString("limitName")
                ?: json.optNullableString("name")
                ?: json.optNullableString("label"),
            scope = json.optNullableString("scope") ?: sourceSlot,
            usedPercent = usedPercent,
            windowDurationMinutes = durationMinutes,
            resetsAtEpochSeconds = resetAt
        )
    }

    private fun readDurationMinutes(json: JSONObject): Int {
        val durationSeconds = firstPositiveLong(
            json.optLong("limit_window_seconds", 0L),
            json.optLong("limitWindowSeconds", 0L)
        )
        if (durationSeconds != null) {
            return (durationSeconds / SECONDS_PER_MINUTE).toInt()
        }

        return firstPositiveInt(
            json.optInt("window_minutes", 0),
            json.optInt("window_duration_mins", 0),
            json.optInt("windowDurationMins", 0),
            json.optInt("windowDurationMinutes", 0)
        ) ?: UNKNOWN_DURATION_MINUTES
    }

    private fun firstPositiveLong(vararg values: Long): Long? {
        return values.firstOrNull { value -> value > 0L }
    }

    private fun firstPositiveInt(vararg values: Int): Int? {
        return values.firstOrNull { value -> value > 0 }
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val UNKNOWN_DURATION_MINUTES = 0
}
