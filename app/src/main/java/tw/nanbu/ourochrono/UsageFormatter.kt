package tw.nanbu.ourochrono

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

object UsageFormatter {
    fun resetCountdown(resetsAtEpochSeconds: Long?): String {
        if (resetsAtEpochSeconds == null) return "重置時間未知"
        val remaining = max(0L, resetsAtEpochSeconds - System.currentTimeMillis() / 1000)
        val days = remaining / 86_400
        val hours = (remaining % 86_400) / 3_600
        val minutes = (remaining % 3_600) / 60
        return when {
            days > 0 -> "$days 天 $hours 小時後重置"
            hours > 0 -> "$hours 小時 $minutes 分後重置"
            minutes > 0 -> "$minutes 分鐘後重置"
            else -> "即將重置"
        }
    }

    fun updatedAt(epochMillis: Long): String {
        val elapsedSeconds = max(0L, (System.currentTimeMillis() - epochMillis) / 1000)
        return when {
            elapsedSeconds < 60 -> "剛剛更新"
            elapsedSeconds < 3_600 -> "${elapsedSeconds / 60} 分鐘前更新"
            elapsedSeconds < 86_400 -> "${elapsedSeconds / 3_600} 小時前更新"
            else -> {
                val format = SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN)
                format.timeZone = TimeZone.getDefault()
                "更新於 ${format.format(Date(epochMillis))}"
            }
        }
    }

    fun lastUpdatedClock(epochMillis: Long): String {
        val format = SimpleDateFormat("HH:mm", Locale.TAIWAN)
        format.timeZone = TimeZone.getDefault()
        return "最後更新 ${format.format(Date(epochMillis))}"
    }

    fun planLabel(planType: String?): String {
        return when (planType?.lowercase(Locale.US)) {
            "plus" -> "ChatGPT Plus"
            "pro" -> "ChatGPT Pro"
            "team" -> "ChatGPT Team"
            "business" -> "ChatGPT Business"
            "enterprise" -> "ChatGPT Enterprise"
            null, "" -> "ChatGPT"
            else -> "ChatGPT ${planType.replaceFirstChar { it.uppercase() }}"
        }
    }
}
