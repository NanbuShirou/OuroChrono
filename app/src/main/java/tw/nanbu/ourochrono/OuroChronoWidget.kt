package tw.nanbu.ourochrono

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews

class OuroChronoWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.schedulePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        RefreshScheduler.schedulePeriodic(context)
        appWidgetIds.forEach { widgetId -> render(context, appWidgetManager, widgetId) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH_WIDGET -> {
                UsageCache.markStale(context, "更新中")
                updateAll(context)
                RefreshScheduler.refreshNow(context)
                return
            }
            ACTION_REFRESH_COUNTDOWN_EXPIRED -> {
                RefreshScheduler.onCountdownExpired(context)
                return
            }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                RefreshScheduler.schedulePeriodic(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val options = manager.getAppWidgetOptions(widgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val compact = minWidth < 235
        val layoutId = if (compact) R.layout.widget_compact else R.layout.widget_detail
        val views = RemoteViews(context.packageName, layoutId)
        val signedIn = CodexTokenStore.hasTokens(context)
        val usage = UsageCache.load(context)

        bindOpenApp(context, views)
        bindRefresh(context, views)

        if (!signedIn || usage == null || usage.windows.isEmpty()) {
            bindEmptyState(context, views, compact)
        } else {
            bindUsage(context, views, compact, usage)
        }

        manager.updateAppWidget(widgetId, views)
    }

    private fun bindUsage(
        context: Context,
        views: RemoteViews,
        compact: Boolean,
        usage: UsageSnapshot
    ) {
        val weekly = usage.weeklyWindow()
        val short = usage.shortWindow()
        bindUsageRings(
            context = context,
            views = views,
            compact = compact,
            weeklyRemaining = weekly?.remainingPercent,
            weeklyUsed = weekly?.usedPercent,
            shortRemaining = short?.remainingPercent,
            shortUsed = short?.usedPercent
        )
        bindResetCredits(views, usage.resetCredits)
    }

    private fun bindUsageRings(
        context: Context,
        views: RemoteViews,
        compact: Boolean,
        weeklyRemaining: Int?,
        weeklyUsed: Int?,
        shortRemaining: Int?,
        shortUsed: Int?
    ) {
        val diameterDp = if (compact) 101 else 113
        val textSizeSp = if (compact) 24f else 28f
        val strokeWidthDp = if (compact) 9.8f else 11f
        val bothMetersFull = weeklyRemaining == 100 && shortRemaining == 100
        val weeklyColor = usageColor(
            remainingPercent = weeklyRemaining,
            usedPercent = weeklyUsed,
            normalColor = WEEKLY_GREEN,
            bothMetersFull = bothMetersFull
        )
        val shortColor = usageColor(
            remainingPercent = shortRemaining,
            usedPercent = shortUsed,
            normalColor = SHORT_BLUE,
            bothMetersFull = bothMetersFull
        )

        views.setImageViewBitmap(
            R.id.widget_weekly_ring,
            UsageRingRenderer.create(
                context = context,
                displayPercent = weeklyRemaining,
                accentColor = weeklyColor,
                diameterDp = diameterDp,
                textSizeSp = textSizeSp,
                strokeWidthDp = strokeWidthDp
            )
        )
        views.setImageViewBitmap(
            R.id.widget_short_ring,
            UsageRingRenderer.create(
                context = context,
                displayPercent = shortRemaining,
                accentColor = shortColor,
                diameterDp = diameterDp,
                textSizeSp = textSizeSp,
                strokeWidthDp = strokeWidthDp
            )
        )
    }

    private fun usageColor(
        remainingPercent: Int?,
        usedPercent: Int?,
        normalColor: Int,
        bothMetersFull: Boolean
    ): Int {
        return when {
            remainingPercent == null || usedPercent == null -> USAGE_UNAVAILABLE
            remainingPercent == 0 -> ZERO_PERCENT_COLOR
            bothMetersFull -> BOTH_METERS_FULL
            usedPercent >= 90 -> USAGE_DANGER
            usedPercent >= 75 -> USAGE_HIGH
            usedPercent >= 50 -> USAGE_WARNING
            else -> normalColor
        }
    }

    private fun bindResetCredits(views: RemoteViews, resetCredits: Int?) {
        val display = ResetCreditDisplayFactory.create(resetCredits)
        val text = when {
            display.unavailable -> "--"
            display.multiplier != null -> "$RED_HEART×${display.multiplier}"
            else -> buildString {
                display.hearts.forEach { heart ->
                    append(
                        when (heart) {
                            ResetHeartState.RED -> RED_HEART
                            ResetHeartState.WHITE -> WHITE_HEART
                        }
                    )
                }
            }
        }
        views.setTextViewText(R.id.widget_reset_hearts, text)
    }

    private fun bindEmptyState(
        context: Context,
        views: RemoteViews,
        compact: Boolean
    ) {
        bindUsageRings(
            context = context,
            views = views,
            compact = compact,
            weeklyRemaining = null,
            weeklyUsed = null,
            shortRemaining = null,
            shortUsed = null
        )
        bindResetCredits(views, null)
    }

    private fun bindOpenApp(context: Context, views: RemoteViews) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }

    private fun bindRefresh(context: Context, views: RemoteViews) {
        val intent = Intent(context, OuroChronoWidget::class.java).apply {
            action = ACTION_REFRESH_WIDGET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh, pendingIntent)

        val now = System.currentTimeMillis()
        val nextRefreshAt = AppPreferences.nextRefreshAtMillis(context)
        val remainingMillis = nextRefreshAt - now
        if (remainingMillis > 0L) {
            val chronometerBase = SystemClock.elapsedRealtime() + remainingMillis
            views.setChronometer(
                R.id.widget_refresh,
                chronometerBase,
                REFRESH_COUNTDOWN_FORMAT,
                true
            )
            views.setChronometerCountDown(R.id.widget_refresh, true)
        } else {
            views.setChronometer(
                R.id.widget_refresh,
                SystemClock.elapsedRealtime(),
                REFRESH_UNAVAILABLE_FORMAT,
                false
            )
            views.setChronometerCountDown(R.id.widget_refresh, true)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "tw.nanbu.ourochrono.ACTION_REFRESH_WIDGET"
        const val ACTION_REFRESH_COUNTDOWN_EXPIRED =
            "tw.nanbu.ourochrono.ACTION_REFRESH_COUNTDOWN_EXPIRED"
        private const val WEEKLY_GREEN: Int = 0xFF4CAF50.toInt()
        private const val SHORT_BLUE: Int = 0xFF2344BA.toInt()
        private const val USAGE_WARNING: Int = 0xFFFBC02D.toInt()
        private const val USAGE_HIGH: Int = 0xFFFF9800.toInt()
        private const val USAGE_DANGER: Int = 0xFFB3180C.toInt()
        private const val USAGE_UNAVAILABLE: Int = 0xFF667080.toInt()
        private const val ZERO_PERCENT_COLOR: Int = 0xFF212121.toInt()
        private const val BOTH_METERS_FULL: Int = 0xFFFFC73B.toInt()
        private const val RED_HEART = "\u2665\uFE0F"
        private const val WHITE_HEART = "\uD83E\uDD0D"
        private const val REFRESH_COUNTDOWN_FORMAT = "↻ %s"
        private const val REFRESH_UNAVAILABLE_FORMAT = "↻ --:--"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, OuroChronoWidget::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            val provider = OuroChronoWidget()
            ids.forEach { widgetId -> provider.render(context, manager, widgetId) }
        }
    }
}
