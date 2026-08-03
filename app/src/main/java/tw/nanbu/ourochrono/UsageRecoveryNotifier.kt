package tw.nanbu.ourochrono

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object UsageRecoveryNotifier {
    private const val NOTIFICATION_ID = 2101
    private const val CHANNEL_PREFIX = "usage_recovery_v1"
    private val vibrationPattern = longArrayOf(0L, 250L, 150L, 250L)

    private val allChannelIds = listOf(
        "${CHANNEL_PREFIX}_sound_vibration",
        "${CHANNEL_PREFIX}_sound",
        "${CHANNEL_PREFIX}_vibration",
        "${CHANNEL_PREFIX}_silent"
    )

    fun notifyIfRecovered(
        context: Context,
        previous: UsageSnapshot?,
        current: UsageSnapshot
    ) {
        if (!AppPreferences.recoveryNotificationEnabled(context)) return

        val recoveredLabels = recoveredUsageLabels(previous, current)
        if (recoveredLabels.isEmpty()) return

        val content = when (recoveredLabels.size) {
            1 -> "${recoveredLabels.first()}用量已回到 100%"
            else -> "${recoveredLabels.joinToString("與")}用量已回到 100%"
        }
        postNotification(context, "用量已恢復", content)
    }

    fun showTestNotification(context: Context): Boolean {
        return postNotification(
            context,
            title = "OuroChrono 測試通知",
            content = "通知音效與震動設定已套用"
        )
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun notificationsAvailable(context: Context): Boolean {
        return hasNotificationPermission(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun refreshSelectedChannel(context: Context) {
        ensureSelectedChannel(context)
    }

    fun openSelectedChannelSettings(context: Context) {
        val channelId = ensureSelectedChannel(context)
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    internal fun recoveredUsageLabels(
        previous: UsageSnapshot?,
        current: UsageSnapshot
    ): List<String> = buildList {
        addRecoveredLabel(
            label = "每週",
            previousRemaining = previous?.weeklyWindow()?.remainingPercent,
            currentRemaining = current.weeklyWindow()?.remainingPercent
        )
        addRecoveredLabel(
            label = "5 小時",
            previousRemaining = previous?.shortWindow()?.remainingPercent,
            currentRemaining = current.shortWindow()?.remainingPercent
        )
    }

    private fun MutableList<String>.addRecoveredLabel(
        label: String,
        previousRemaining: Int?,
        currentRemaining: Int?
    ) {
        if (previousRemaining != null &&
            previousRemaining < 100 &&
            currentRemaining == 100
        ) {
            add(label)
        }
    }

    private fun postNotification(
        context: Context,
        title: String,
        content: String
    ): Boolean {
        if (!notificationsAvailable(context)) return false

        val channelId = ensureSelectedChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            2100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundEnabled = AppPreferences.notificationSoundEnabled(context)
        val vibrationEnabled = AppPreferences.notificationVibrationEnabled(context)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (soundEnabled) {
                builder.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                )
            }
            if (vibrationEnabled) builder.setVibrate(vibrationPattern)
            if (!soundEnabled && !vibrationEnabled) builder.setSilent(true)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        return true
    }

    private fun ensureSelectedChannel(context: Context): String {
        val soundEnabled = AppPreferences.notificationSoundEnabled(context)
        val vibrationEnabled = AppPreferences.notificationVibrationEnabled(context)
        val channelId = channelId(soundEnabled, vibrationEnabled)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            allChannelIds.filterNot { it == channelId }.forEach(manager::deleteNotificationChannel)

            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName(soundEnabled, vibrationEnabled),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Codex 用量恢復到 100% 時通知"
                    enableVibration(vibrationEnabled)
                    vibrationPattern = if (vibrationEnabled) {
                        UsageRecoveryNotifier.vibrationPattern
                    } else {
                        null
                    }

                    if (soundEnabled) {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            audioAttributes
                        )
                    } else {
                        setSound(null, null)
                    }
                }
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    private fun channelId(soundEnabled: Boolean, vibrationEnabled: Boolean): String {
        return when {
            soundEnabled && vibrationEnabled -> "${CHANNEL_PREFIX}_sound_vibration"
            soundEnabled -> "${CHANNEL_PREFIX}_sound"
            vibrationEnabled -> "${CHANNEL_PREFIX}_vibration"
            else -> "${CHANNEL_PREFIX}_silent"
        }
    }

    private fun channelName(soundEnabled: Boolean, vibrationEnabled: Boolean): String {
        return when {
            soundEnabled && vibrationEnabled -> "用量恢復通知（音效與震動）"
            soundEnabled -> "用量恢復通知（音效）"
            vibrationEnabled -> "用量恢復通知（震動）"
            else -> "用量恢復通知（靜音）"
        }
    }
}
