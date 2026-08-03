package tw.nanbu.ourochrono

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    private const val SCHEDULED_WORK_NAME = "ourochrono_scheduled_refresh"
    private const val LEGACY_PERIODIC_WORK_NAME = "ourochrono_periodic_refresh"
    private const val MANUAL_WORK_NAME = "ourochrono_manual_refresh"
    private const val COUNTDOWN_ALARM_REQUEST_CODE = 102
    private const val COUNTDOWN_ROLLOVER_EARLY_MILLIS = 500L

    internal const val INPUT_IS_SCHEDULED = "is_scheduled"
    internal const val INPUT_SCHEDULE_GENERATION = "schedule_generation"

    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        ensureScheduled(context)
    }

    fun ensureScheduled(context: Context) {
        if (!CodexTokenStore.hasTokens(context)) {
            AppPreferences.setNextRefreshAtMillis(context, 0L)
            cancelCountdownAlarm(context)
            return
        }

        val now = System.currentTimeMillis()
        val savedNext = AppPreferences.nextRefreshAtMillis(context)
        val overdue = savedNext in 1..now
        val nextAt = when {
            savedNext > now -> savedNext
            else -> calculateNextRefreshAt(context, now)
        }
        val delayMillis = if (overdue) {
            1_000L
        } else {
            (nextAt - now).coerceAtLeast(1_000L)
        }
        val generation = AppPreferences.scheduleGeneration(context)

        if (savedNext != nextAt) {
            AppPreferences.setNextRefreshAtMillis(context, nextAt)
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            SCHEDULED_WORK_NAME,
            if (overdue) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            scheduledRequest(delayMillis, generation)
        )
        scheduleCountdownAlarm(context, nextAt)
        OuroChronoWidget.updateAll(context)
    }

    fun reschedule(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        val generation = AppPreferences.incrementScheduleGeneration(context)
        if (!CodexTokenStore.hasTokens(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(SCHEDULED_WORK_NAME)
            AppPreferences.setNextRefreshAtMillis(context, 0L)
            cancelCountdownAlarm(context)
            OuroChronoWidget.updateAll(context)
            return
        }

        val now = System.currentTimeMillis()
        val nextAt = calculateNextRefreshAt(context, now)
        AppPreferences.setNextRefreshAtMillis(context, nextAt)

        WorkManager.getInstance(context).enqueueUniqueWork(
            SCHEDULED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            scheduledRequest(nextAt - now, generation)
        )
        scheduleCountdownAlarm(context, nextAt)
        OuroChronoWidget.updateAll(context)
    }

    /**
     * Called by a lightweight alarm at the visible countdown boundary. WorkManager is still kept
     * as the durable background scheduler, while this alarm immediately rolls the Chronometer into
     * the next interval so it never keeps counting below zero.
     */
    fun onCountdownExpired(context: Context) {
        if (!CodexTokenStore.hasTokens(context)) {
            AppPreferences.setNextRefreshAtMillis(context, 0L)
            cancelCountdownAlarm(context)
            OuroChronoWidget.updateAll(context)
            return
        }

        val now = System.currentTimeMillis()
        val savedNext = AppPreferences.nextRefreshAtMillis(context)
        if (savedNext > now + 1_000L) {
            scheduleCountdownAlarm(context, savedNext)
            OuroChronoWidget.updateAll(context)
            return
        }

        val generation = AppPreferences.incrementScheduleGeneration(context)
        val nextAt = calculateNextRefreshAt(context, now)
        AppPreferences.setNextRefreshAtMillis(context, nextAt)

        WorkManager.getInstance(context).enqueueUniqueWork(
            SCHEDULED_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            scheduledRequest(nextAt - now, generation)
        )
        scheduleCountdownAlarm(context, nextAt)
        OuroChronoWidget.updateAll(context)
        refreshNow(context)
    }

    internal fun onScheduledWorkStarted(context: Context, generation: Int): Boolean {
        if (generation != AppPreferences.scheduleGeneration(context)) return false

        // WorkManager may start later than the requested instant. Advance the visible
        // countdown as soon as this run begins instead of leaving the Chronometer at 00:00.
        val nextAt = calculateNextRefreshAt(context, System.currentTimeMillis())
        AppPreferences.setNextRefreshAtMillis(context, nextAt)
        scheduleCountdownAlarm(context, nextAt)
        UsageCache.markStale(context, "更新中")
        OuroChronoWidget.updateAll(context)
        return true
    }

    internal fun scheduleNextAfterRun(context: Context, generation: Int) {
        if (generation != AppPreferences.scheduleGeneration(context)) return
        if (!CodexTokenStore.hasTokens(context)) {
            AppPreferences.setNextRefreshAtMillis(context, 0L)
            cancelCountdownAlarm(context)
            OuroChronoWidget.updateAll(context)
            return
        }

        val now = System.currentTimeMillis()
        val savedNext = AppPreferences.nextRefreshAtMillis(context)
        val nextAt = savedNext.takeIf { it > now }
            ?: calculateNextRefreshAt(context, now)
        AppPreferences.setNextRefreshAtMillis(context, nextAt)
        WorkManager.getInstance(context).enqueueUniqueWork(
            SCHEDULED_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            scheduledRequest((nextAt - now).coerceAtLeast(1_000L), generation)
        )
        scheduleCountdownAlarm(context, nextAt)
        OuroChronoWidget.updateAll(context)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(workDataOf(INPUT_IS_SCHEDULED to false))
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll(context: Context) {
        AppPreferences.incrementScheduleGeneration(context)
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(SCHEDULED_WORK_NAME)
        manager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME)
        manager.cancelUniqueWork(MANUAL_WORK_NAME)
        AppPreferences.setNextRefreshAtMillis(context, 0L)
        cancelCountdownAlarm(context)
    }

    private fun scheduledRequest(delayMillis: Long, generation: Int) =
        OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(
                workDataOf(
                    INPUT_IS_SCHEDULED to true,
                    INPUT_SCHEDULE_GENERATION to generation
                )
            )
            .setInitialDelay(delayMillis.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

    private fun calculateNextRefreshAt(context: Context, now: Long): Long {
        return now + TimeUnit.MINUTES.toMillis(
            AppPreferences.refreshIntervalMinutes(context).toLong()
        )
    }

    private fun scheduleCountdownAlarm(context: Context, nextAt: Long) {
        if (nextAt <= 0L) {
            cancelCountdownAlarm(context)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = countdownPendingIntent(context)
        val triggerAt = (nextAt - COUNTDOWN_ROLLOVER_EARLY_MILLIS)
            .coerceAtLeast(System.currentTimeMillis() + 250L)

        alarmManager.cancel(pendingIntent)
        try {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
                alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
                else -> {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    private fun cancelCountdownAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(countdownPendingIntent(context))
    }

    private fun countdownPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, OuroChronoWidget::class.java).apply {
            action = OuroChronoWidget.ACTION_REFRESH_COUNTDOWN_EXPIRED
        }
        return PendingIntent.getBroadcast(
            context,
            COUNTDOWN_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
