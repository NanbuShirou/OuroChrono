package tw.nanbu.ourochrono

import android.content.Context

object AppPreferences {
    val allowedRefreshIntervalsMinutes: List<Int> = listOf(5, 10, 15, 30, 60)

    private const val PREFS_NAME = "ourochrono_app_preferences"
    private const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"
    private const val KEY_NEXT_REFRESH_AT_MILLIS = "next_refresh_at_millis"
    private const val KEY_SCHEDULE_GENERATION = "schedule_generation"
    private const val KEY_RECOVERY_NOTIFICATION_ENABLED = "recovery_notification_enabled"
    private const val KEY_NOTIFICATION_SOUND_ENABLED = "notification_sound_enabled"
    private const val KEY_NOTIFICATION_VIBRATION_ENABLED = "notification_vibration_enabled"
    private const val KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED =
        "notification_permission_prompt_handled"
    private const val KEY_EXACT_ALARM_PERMISSION_PROMPT_HANDLED =
        "exact_alarm_permission_prompt_handled"

    private const val DEFAULT_REFRESH_INTERVAL_MINUTES = 5

    fun refreshIntervalMinutes(context: Context): Int {
        val saved = prefs(context).getInt(
            KEY_REFRESH_INTERVAL_MINUTES,
            DEFAULT_REFRESH_INTERVAL_MINUTES
        )
        return saved.takeIf { it in allowedRefreshIntervalsMinutes }
            ?: DEFAULT_REFRESH_INTERVAL_MINUTES
    }

    fun setRefreshIntervalMinutes(context: Context, minutes: Int) {
        require(minutes in allowedRefreshIntervalsMinutes) {
            "不支援的更新週期：$minutes 分鐘"
        }
        prefs(context).edit().putInt(KEY_REFRESH_INTERVAL_MINUTES, minutes).apply()
    }

    fun nextRefreshAtMillis(context: Context): Long {
        return prefs(context).getLong(KEY_NEXT_REFRESH_AT_MILLIS, 0L)
    }

    fun setNextRefreshAtMillis(context: Context, epochMillis: Long) {
        prefs(context).edit().putLong(KEY_NEXT_REFRESH_AT_MILLIS, epochMillis).apply()
    }

    fun scheduleGeneration(context: Context): Int {
        return prefs(context).getInt(KEY_SCHEDULE_GENERATION, 0)
    }

    fun incrementScheduleGeneration(context: Context): Int {
        val next = scheduleGeneration(context) + 1
        prefs(context).edit().putInt(KEY_SCHEDULE_GENERATION, next).apply()
        return next
    }

    fun recoveryNotificationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_RECOVERY_NOTIFICATION_ENABLED, true)
    }

    fun setRecoveryNotificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RECOVERY_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun notificationSoundEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NOTIFICATION_SOUND_ENABLED, true)
    }

    fun setNotificationSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_SOUND_ENABLED, enabled).apply()
    }

    fun notificationVibrationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NOTIFICATION_VIBRATION_ENABLED, true)
    }

    fun setNotificationVibrationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_VIBRATION_ENABLED, enabled).apply()
    }

    fun notificationPermissionPromptHandled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED, false)
    }

    fun setNotificationPermissionPromptHandled(context: Context, handled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED, handled)
            .apply()
    }

    fun exactAlarmPermissionPromptHandled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EXACT_ALARM_PERMISSION_PROMPT_HANDLED, false)
    }

    fun setExactAlarmPermissionPromptHandled(context: Context, handled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_EXACT_ALARM_PERMISSION_PROMPT_HANDLED, handled)
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
}
