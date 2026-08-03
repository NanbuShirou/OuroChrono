package tw.nanbu.ourochrono

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var panelSignedOut: View
    private lateinit var panelLoginProgress: View
    private lateinit var panelUsage: View
    private lateinit var mainContentScroll: ScrollView
    private lateinit var tabUsage: TextView
    private lateinit var tabNotifications: TextView
    private lateinit var tabSystem: TextView
    private lateinit var pageUsage: View
    private lateinit var pageNotifications: View
    private lateinit var pageSystem: View
    private lateinit var globalStatus: TextView
    private lateinit var loginStatusText: TextView
    private lateinit var loginCodeText: TextView
    private lateinit var openLoginPageButton: Button
    private lateinit var retryLoginButton: Button
    private lateinit var accountLabel: TextView
    private lateinit var weeklyPercent: TextView
    private lateinit var weeklyUsed: TextView
    private lateinit var weeklyReset: TextView
    private lateinit var weeklyProgress: ProgressBar
    private lateinit var shortPercent: TextView
    private lateinit var shortUsed: TextView
    private lateinit var shortReset: TextView
    private lateinit var shortProgress: ProgressBar
    private lateinit var resetCredits: TextView
    private lateinit var refreshCountdown: TextView
    private lateinit var refreshIntervalButtons: Map<Int, TextView>
    private lateinit var notificationEnabledCheckBox: Switch
    private lateinit var notificationSoundCheckBox: Switch
    private lateinit var notificationVibrationCheckBox: Switch
    private lateinit var notificationStatus: TextView
    private lateinit var loadingOverlay: View
    private lateinit var loadingRing: ImageView
    private lateinit var loadingStatusText: TextView

    private var loadingAnimator: ObjectAnimator? = null

    private var bindingSettings = false
    private var pendingNotificationTest = false
    private var notificationPermissionDialogShown = false

    private val refreshCountdownRunnable = object : Runnable {
        override fun run() {
            updateRefreshCountdown()
            mainHandler.postDelayed(this, 1_000L)
        }
    }

    @Volatile
    private var activeLogin: DeviceCodeLoginSession? = null

    @Volatile
    private var lifecycleDestroyed = false

    private val loginStateListener: (DeviceLoginCoordinator.State) -> Unit = { state ->
        mainHandler.post {
            if (!lifecycleDestroyed) handleDeviceLoginState(state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleDestroyed = false
        setContentView(R.layout.activity_main)
        CodexTokenStore.removeLegacyRelaySettings(this)
        bindViews()
        showLoadingOverlay(getString(R.string.loading_status))
        bindActions()
        bindSettings()
        DeviceLoginCoordinator.addListener(loginStateListener)
        showInitialState()
    }

    override fun onResume() {
        super.onResume()
        if (::notificationStatus.isInitialized) updateNotificationStatus()
        if (::refreshCountdown.isInitialized) startRefreshCountdown()
        maybeRequestExactAlarmAccess()
    }

    override fun onPause() {
        stopRefreshCountdown()
        super.onPause()
    }

    private fun maybeRequestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!CodexTokenStore.hasTokens(this)) return

        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) {
            RefreshScheduler.schedulePeriodic(this)
            return
        }
        if (AppPreferences.exactAlarmPermissionPromptHandled(this)) return

        AppPreferences.setExactAlarmPermissionPromptHandled(this, true)
        AlertDialog.Builder(this)
            .setTitle("允許精準倒數")
            .setMessage(
                "為了讓小工具在 00:00 時立即重新計時，而不是繼續顯示負秒數，" +
                    "請允許 OuroChrono 使用「鬧鐘與提醒」權限。"
            )
            .setPositiveButton("前往設定") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    "package:$packageName".toUri()
                )
                runCatching { startActivity(intent) }
                    .onFailure { showToast("無法開啟鬧鐘與提醒設定") }
            }
            .setNegativeButton("稍後", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) return

        updateNotificationStatus()
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (granted && pendingNotificationTest) {
            pendingNotificationTest = false
            UsageRecoveryNotifier.showTestNotification(this)
            showToast("測試通知已送出")
        } else if (!granted) {
            pendingNotificationTest = false
            showToast("未取得通知權限，恢復提醒不會出現")
        }
    }

    override fun onDestroy() {
        lifecycleDestroyed = true
        DeviceLoginCoordinator.removeListener(loginStateListener)
        activeLogin = null
        stopRefreshCountdown()
        stopLoadingAnimation()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        panelSignedOut = findViewById(R.id.panel_signed_out)
        panelLoginProgress = findViewById(R.id.panel_login_progress)
        panelUsage = findViewById(R.id.panel_usage)
        mainContentScroll = findViewById(R.id.main_content_scroll)
        tabUsage = findViewById(R.id.tab_usage)
        tabNotifications = findViewById(R.id.tab_notifications)
        tabSystem = findViewById(R.id.tab_system)
        pageUsage = findViewById(R.id.page_usage)
        pageNotifications = findViewById(R.id.page_notifications)
        pageSystem = findViewById(R.id.page_system)
        globalStatus = findViewById(R.id.global_status)
        loginStatusText = findViewById(R.id.login_status_text)
        loginCodeText = findViewById(R.id.login_code_text)
        openLoginPageButton = findViewById(R.id.open_login_page_button)
        retryLoginButton = findViewById(R.id.retry_login_button)
        accountLabel = findViewById(R.id.account_label)
        weeklyPercent = findViewById(R.id.weekly_percent)
        weeklyUsed = findViewById(R.id.weekly_used)
        weeklyReset = findViewById(R.id.weekly_reset)
        weeklyProgress = findViewById(R.id.weekly_progress)
        shortPercent = findViewById(R.id.short_percent)
        shortUsed = findViewById(R.id.short_used)
        shortReset = findViewById(R.id.short_reset)
        shortProgress = findViewById(R.id.short_progress)
        resetCredits = findViewById(R.id.reset_credits)
        refreshCountdown = findViewById(R.id.refresh_countdown)
        refreshIntervalButtons = linkedMapOf(
            5 to findViewById(R.id.refresh_interval_5),
            10 to findViewById(R.id.refresh_interval_10),
            15 to findViewById(R.id.refresh_interval_15),
            30 to findViewById(R.id.refresh_interval_30),
            60 to findViewById(R.id.refresh_interval_60)
        )
        notificationEnabledCheckBox = findViewById(R.id.notification_enabled_checkbox)
        notificationSoundCheckBox = findViewById(R.id.notification_sound_checkbox)
        notificationVibrationCheckBox = findViewById(R.id.notification_vibration_checkbox)
        notificationStatus = findViewById(R.id.notification_status)
        loadingOverlay = findViewById(R.id.loading_overlay)
        loadingRing = findViewById(R.id.loading_ring)
        loadingStatusText = findViewById(R.id.loading_status_text)
    }

    private fun bindActions() {
        tabUsage.setOnClickListener { selectMainPage(MainPage.USAGE) }
        tabNotifications.setOnClickListener { selectMainPage(MainPage.NOTIFICATIONS) }
        tabSystem.setOnClickListener { selectMainPage(MainPage.SYSTEM) }
        selectMainPage(MainPage.USAGE, scrollToTop = false)

        findViewById<Button>(R.id.login_button).setOnClickListener { startDeviceCodeLogin() }
        openLoginPageButton.setOnClickListener { reopenActiveLoginPage() }
        retryLoginButton.setOnClickListener { retryPendingDeviceCodeLogin() }
        findViewById<Button>(R.id.cancel_login_button).setOnClickListener { cancelDeviceCodeLogin() }
        findViewById<Button>(R.id.refresh_button).setOnClickListener { refreshUsage() }
        findViewById<Button>(R.id.logout_button).setOnClickListener { confirmLogout() }
        findViewById<Button>(R.id.clear_phone_button).setOnClickListener { confirmClearLocalData() }
        findViewById<Button>(R.id.test_notification_button).setOnClickListener {
            testRecoveryNotification()
        }
        findViewById<Button>(R.id.open_notification_settings_button).setOnClickListener {
            UsageRecoveryNotifier.openSelectedChannelSettings(this)
        }
    }

    private fun showInitialState() {
        val tokens = CodexTokenStore.load(this)
        if (tokens == null) {
            val pending = PendingDeviceLoginStore.load(this)
            if (pending != null && !CodexOAuthClient.isDeviceLoginExpired(pending)) {
                resumePendingDeviceCodeLogin(pending)
            } else {
                PendingDeviceLoginStore.clear(this)
                showSignedOut("尚未登入 ChatGPT")
            }
            return
        }

        RefreshScheduler.schedulePeriodic(this)
        val cached = UsageCache.load(this)
        if (cached != null && cached.windows.isNotEmpty()) {
            showUsage(tokens.toAccountInfo(), cached)
        } else {
            showUsageUnavailable(tokens.toAccountInfo(), "已登入，正在讀取 Codex 用量")
        }
        refreshUsage()
    }

    private fun startDeviceCodeLogin() {
        DeviceLoginCoordinator.cancel(applicationContext, notify = false)
        activeLogin = null
        ensureExecutor()
        hideLoadingOverlay()
        showOnly(panelLoginProgress)
        globalStatus.text = "正在建立登入代碼"
        loginStatusText.text = "正在向 OpenAI 取得一次性登入代碼…"
        loginCodeText.text = "---- ----"
        openLoginPageButton.isEnabled = false
        retryLoginButton.isEnabled = false

        executor.submit {
            try {
                val session = CodexOAuthClient.requestDeviceCode()
                PendingDeviceLoginStore.save(applicationContext, session)
                activeLogin = session

                // Start polling before opening the browser. The coordinator is
                // application-scoped and survives Activity recreation.
                DeviceLoginCoordinator.ensurePolling(applicationContext, session)

                mainHandler.post {
                    if (!lifecycleDestroyed) {
                        showDeviceCode(session)
                        copyLoginCode(session.userCode, showMessage = false)
                        openBrowser(session.verificationUrl)
                    }
                }
            } catch (error: Exception) {
                PendingDeviceLoginStore.clear(applicationContext)
                activeLogin = null
                if (!lifecycleDestroyed) {
                    mainHandler.post {
                        showSignedOut(error.message ?: "登入失敗")
                        showToast(error.message ?: "登入失敗")
                    }
                }
            }
        }
    }

    private fun resumePendingDeviceCodeLogin(session: DeviceCodeLoginSession) {
        activeLogin = session
        showDeviceCode(session)
        globalStatus.text = "恢復等待 OpenAI 驗證"
        loginStatusText.text =
            "登入程序仍在等待。完成網頁授權後，App 會自動取得結果。"
        DeviceLoginCoordinator.ensurePolling(applicationContext, session)
    }

    private fun handleDeviceLoginState(state: DeviceLoginCoordinator.State) {
        when (state) {
            DeviceLoginCoordinator.State.Idle -> Unit
            DeviceLoginCoordinator.State.Cancelled -> {
                activeLogin = null
                showSignedOut("登入已取消")
            }
            is DeviceLoginCoordinator.State.Waiting -> {
                activeLogin = state.session
                showDeviceCode(state.session)
                globalStatus.text = "等待 OpenAI 驗證"
                loginStatusText.text = state.statusMessage
                    ?: "完成網頁驗證後會自動繼續。已等待 ${state.elapsedSeconds} 秒。"
                retryLoginButton.isEnabled = false
            }
            is DeviceLoginCoordinator.State.Succeeded -> {
                activeLogin = null
                RefreshScheduler.schedulePeriodic(applicationContext)
                DeviceLoginCoordinator.resetState()
                finishLoginWithUsage(state.tokens)
                maybeRequestExactAlarmAccess()
            }
            is DeviceLoginCoordinator.State.Failed -> {
                activeLogin = state.session
                if (state.session != null) {
                    showDeviceCode(state.session)
                    globalStatus.text = "登入結果尚未保存"
                    loginStatusText.text = state.message
                    retryLoginButton.isEnabled = state.canRetry
                } else {
                    showSignedOut(state.message)
                }
            }
        }
    }

    private fun showDeviceCode(session: DeviceCodeLoginSession) {
        hideLoadingOverlay()
        showOnly(panelLoginProgress)
        globalStatus.text = "等待 OpenAI 驗證"
        loginCodeText.text = session.userCode
        loginStatusText.text =
            "已複製登入代碼。請在 OpenAI 網頁輸入代碼並完成登入。首次使用前，必須先在 ChatGPT 設定 > 安全性開啟 Codex 裝置代碼授權。"
        openLoginPageButton.isEnabled = true
        retryLoginButton.isEnabled = false
    }

    private fun finishLoginWithUsage(tokens: OAuthTokenSet) {
        try {
            val usage = CodexUsageClient.getUsage(this)
            UsageCache.saveSuccessful(this, usage)
            OuroChronoWidget.updateAll(this)
            mainHandler.post {
                showUsage(tokens.toAccountInfo(), usage)
                showToast("ChatGPT 登入完成")
            }
        } catch (error: Exception) {
            UsageCache.markStale(this, error.message ?: "登入完成，但無法取得用量")
            OuroChronoWidget.updateAll(this)
            mainHandler.post {
                showUsageUnavailable(
                    tokens.toAccountInfo(),
                    error.message ?: "登入完成，但無法取得 Codex 用量"
                )
                showToast("登入完成，但用量讀取失敗")
            }
        }
    }

    private fun reopenActiveLoginPage() {
        val session = activeLogin
            ?: DeviceLoginCoordinator.currentSession()
            ?: PendingDeviceLoginStore.load(this)
        if (session == null) {
            showToast("登入代碼已失效，請重新開始登入")
            return
        }
        copyLoginCode(session.userCode, showMessage = true)
        openBrowser(session.verificationUrl)
    }

    private fun retryPendingDeviceCodeLogin() {
        retryLoginButton.isEnabled = false
        globalStatus.text = "重新檢查登入結果"
        loginStatusText.text = "正在向 OpenAI 重新確認授權結果…"
        if (!DeviceLoginCoordinator.retry(applicationContext)) {
            showSignedOut("登入代碼已失效，請重新登入")
        }
    }

    private fun copyLoginCode(code: String, showMessage: Boolean) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Codex 登入代碼", code))
        if (showMessage) showToast("登入代碼已複製")
    }

    private fun refreshUsage() {
        val currentTokens = CodexTokenStore.load(this)
        if (currentTokens == null) {
            showSignedOut("尚未登入 ChatGPT")
            return
        }

        val cached = UsageCache.load(this)
        if (cached != null && cached.windows.isNotEmpty()) {
            showUsage(currentTokens.toAccountInfo(), cached)
        }
        setBusy("正在更新 Codex 用量…")
        showLoadingOverlay(getString(R.string.updating_status))

        runAsync(
            task = {
                val usage = CodexUsageClient.getUsage(this)
                val tokens = CodexTokenStore.load(this)
                    ?: throw CodexAuthException("登入已失效，請重新登入")
                tokens to usage
            },
            success = { (tokens, usage) ->
                UsageCache.saveSuccessful(this, usage)
                RefreshScheduler.schedulePeriodic(this)
                OuroChronoWidget.updateAll(this)
                showUsage(tokens.toAccountInfo(), usage)
            },
            failure = { error ->
                hideLoadingOverlay()
                val previous = UsageCache.load(this)
                if (previous != null && previous.windows.isNotEmpty()) {
                    val stale = previous.copy(stale = true, error = error.message ?: "更新失敗")
                    UsageCache.save(this, stale)
                    val tokens = CodexTokenStore.load(this)
                    if (tokens != null) showUsage(tokens.toAccountInfo(), stale)
                } else if (!CodexTokenStore.hasTokens(this)) {
                    showSignedOut("登入已失效，請重新登入")
                } else {
                    val tokens = CodexTokenStore.load(this)
                    if (tokens != null) {
                        showUsageUnavailable(tokens.toAccountInfo(), error.message ?: "更新失敗")
                    } else {
                        showSignedOut("登入已失效，請重新登入")
                    }
                }
                OuroChronoWidget.updateAll(this)
                showToast(error.message ?: "更新失敗")
            }
        )
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("登出 OuroChrono")
            .setMessage("將嘗試撤銷 OpenAI Refresh Token，並清除手機內保存的登入憑證與用量快取。")
            .setNegativeButton("取消", null)
            .setPositiveButton("確認登出") { _, _ -> performLogout() }
            .show()
    }

    private fun performLogout() {
        cancelActiveLoginOnly(clearPending = true)
        setBusy("正在登出…")
        runAsync(
            task = { CodexOAuthClient.logout(this) },
            success = {
                clearLocalState()
                showSignedOut("已登出 ChatGPT")
            },
            failure = {
                clearLocalState()
                showSignedOut("本機登入資料已清除")
            }
        )
    }

    private fun confirmClearLocalData() {
        AlertDialog.Builder(this)
            .setTitle("清除本機資料")
            .setMessage("只清除這支手機的登入憑證與用量快取，不呼叫 OpenAI 撤銷端點。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清除") { _, _ ->
                cancelActiveLoginOnly(clearPending = true)
                clearLocalState()
                showSignedOut("本機資料已清除")
            }
            .show()
    }

    private fun clearLocalState() {
        PendingDeviceLoginStore.clear(this)
        CodexTokenStore.clear(this)
        UsageCache.clear(this)
        RefreshScheduler.cancelAll(this)
        OuroChronoWidget.updateAll(this)
    }

    private fun cancelDeviceCodeLogin() {
        DeviceLoginCoordinator.cancel(applicationContext)
        activeLogin = null
        showSignedOut("登入已取消")
    }

    private fun cancelActiveLoginOnly(clearPending: Boolean) {
        DeviceLoginCoordinator.cancel(applicationContext)
        activeLogin = null
        if (clearPending) PendingDeviceLoginStore.clear(this)
    }

    private fun openBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            DeviceLoginCoordinator.cancel(applicationContext)
            activeLogin = null
            showSignedOut("找不到可開啟登入頁面的瀏覽器")
        }
    }

    private fun showSignedOut(status: String) {
        hideLoadingOverlay()
        selectMainPage(MainPage.USAGE, scrollToTop = false)
        showOnly(panelSignedOut)
        globalStatus.text = status
    }

    private fun showUsageUnavailable(account: AccountInfo, status: String) {
        hideLoadingOverlay()
        showOnly(panelUsage)
        globalStatus.text = status
        accountLabel.text = buildString {
            append(UsageFormatter.planLabel(account.planType))
            account.email?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        weeklyPercent.text = "--"
        weeklyUsed.text = "尚無每週用量資料"
        weeklyReset.text = "重置時間未知"
        weeklyProgress.progress = 0
        shortPercent.text = "--"
        shortUsed.text = "尚無 5 小時用量資料"
        shortReset.text = "重置時間未知"
        shortProgress.progress = 0
        resetCredits.text = resetCreditsText(null)
        maybePromptNotificationPermission()
    }

    private fun showUsage(account: AccountInfo, usage: UsageSnapshot) {
        val weekly = usage.weeklyWindow()
        val short = usage.shortWindow()
        val weeklyRemaining = weekly?.remainingPercent
        val shortRemaining = short?.remainingPercent

        hideLoadingOverlay()
        showOnly(panelUsage)
        globalStatus.text = if (usage.stale) usage.error ?: "顯示快取資料" else "Codex 用量已更新"
        accountLabel.text = buildString {
            append(UsageFormatter.planLabel(account.planType ?: usage.planType))
            account.email?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }

        weeklyPercent.text = weeklyRemaining?.let { "$it%" } ?: "--"
        weeklyUsed.text = weekly?.let {
            "已使用 ${it.usedPercent}% · 剩餘 ${it.remainingPercent}%"
        } ?: "尚無每週用量資料"
        weeklyReset.text = UsageFormatter.resetCountdown(weekly?.resetsAtEpochSeconds)
        weeklyProgress.progress = weeklyRemaining ?: 0

        shortPercent.text = shortRemaining?.let { "$it%" } ?: "--"
        shortUsed.text = short?.let {
            "已使用 ${it.usedPercent}% · 剩餘 ${it.remainingPercent}%"
        } ?: "尚無 5 小時用量資料"
        shortReset.text = UsageFormatter.resetCountdown(short?.resetsAtEpochSeconds)
        shortProgress.progress = shortRemaining ?: 0

        resetCredits.text = resetCreditsText(usage.resetCredits)
        maybePromptNotificationPermission()
    }

    private fun resetCreditsText(value: Int?): String {
        val display = ResetCreditDisplayFactory.create(value)
        return when {
            display.unavailable -> "--"
            display.multiplier != null -> "$RESET_RED_HEART×${display.multiplier}"
            else -> buildString {
                display.hearts.forEach { heart ->
                    append(
                        when (heart) {
                            ResetHeartState.RED -> RESET_RED_HEART
                            ResetHeartState.WHITE -> RESET_WHITE_HEART
                        }
                    )
                }
            }
        }
    }

    private fun bindSettings() {
        val selectedInterval = AppPreferences.refreshIntervalMinutes(this)
        updateRefreshIntervalSelection(selectedInterval)

        refreshIntervalButtons.forEach { (minutes, button) ->
            button.setOnClickListener {
                if (minutes == AppPreferences.refreshIntervalMinutes(this)) return@setOnClickListener

                AppPreferences.setRefreshIntervalMinutes(this, minutes)
                RefreshScheduler.reschedule(this)
                updateRefreshIntervalSelection(minutes)
                updateRefreshCountdown()
                showToast("自動更新已改為每 $minutes 分鐘")
            }
        }

        bindingSettings = true
        notificationEnabledCheckBox.isChecked =
            AppPreferences.recoveryNotificationEnabled(this)
        notificationSoundCheckBox.isChecked =
            AppPreferences.notificationSoundEnabled(this)
        notificationVibrationCheckBox.isChecked =
            AppPreferences.notificationVibrationEnabled(this)
        bindingSettings = false

        notificationEnabledCheckBox.setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            AppPreferences.setRecoveryNotificationEnabled(this, checked)
            if (checked) requestNotificationPermission(testAfterGrant = false)
            updateNotificationStatus()
        }

        notificationSoundCheckBox.setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            AppPreferences.setNotificationSoundEnabled(this, checked)
            UsageRecoveryNotifier.refreshSelectedChannel(this)
            updateNotificationStatus()
        }

        notificationVibrationCheckBox.setOnCheckedChangeListener { _, checked ->
            if (bindingSettings) return@setOnCheckedChangeListener
            AppPreferences.setNotificationVibrationEnabled(this, checked)
            UsageRecoveryNotifier.refreshSelectedChannel(this)
            updateNotificationStatus()
        }

        updateNotificationStatus()
    }

    private fun updateRefreshIntervalSelection(selectedMinutes: Int) {
        refreshIntervalButtons.forEach { (minutes, button) ->
            val selected = minutes == selectedMinutes
            button.setBackgroundResource(
                if (selected) R.drawable.bg_interval_selected
                else R.drawable.bg_interval_unselected
            )
            button.setTextColor(
                getColor(if (selected) R.color.button_outline_border else R.color.text_muted)
            )
            button.contentDescription = if (selected) {
                "$minutes 分鐘，已選取"
            } else {
                "$minutes 分鐘"
            }
        }
    }

    private fun testRecoveryNotification() {
        if (!AppPreferences.recoveryNotificationEnabled(this)) {
            bindingSettings = true
            notificationEnabledCheckBox.isChecked = true
            bindingSettings = false
            AppPreferences.setRecoveryNotificationEnabled(this, true)
        }

        if (!UsageRecoveryNotifier.hasNotificationPermission(this)) {
            requestNotificationPermission(testAfterGrant = true)
            return
        }

        if (UsageRecoveryNotifier.showTestNotification(this)) {
            showToast("測試通知已送出")
        } else {
            showToast("系統目前不允許 OuroChrono 顯示通知")
        }
        updateNotificationStatus()
    }

    private fun requestNotificationPermission(testAfterGrant: Boolean) {
        pendingNotificationTest = testAfterGrant
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            UsageRecoveryNotifier.hasNotificationPermission(this)
        ) {
            if (testAfterGrant) {
                pendingNotificationTest = false
                UsageRecoveryNotifier.showTestNotification(this)
                showToast("測試通知已送出")
            }
            updateNotificationStatus()
            return
        }

        AppPreferences.setNotificationPermissionPromptHandled(this, true)
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION
        )
    }

    private fun maybePromptNotificationPermission() {
        if (notificationPermissionDialogShown ||
            !AppPreferences.recoveryNotificationEnabled(this) ||
            AppPreferences.notificationPermissionPromptHandled(this) ||
            UsageRecoveryNotifier.hasNotificationPermission(this) ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        notificationPermissionDialogShown = true
        AlertDialog.Builder(this)
            .setTitle("允許用量恢復通知")
            .setMessage("Codex 用量由低於 100% 回復到 100% 時，可使用手機內建通知音效與震動提醒。")
            .setNegativeButton("稍後") { _, _ ->
                AppPreferences.setNotificationPermissionPromptHandled(this, true)
                updateNotificationStatus()
            }
            .setPositiveButton("繼續") { _, _ ->
                requestNotificationPermission(testAfterGrant = false)
            }
            .show()
    }

    private fun updateNotificationStatus() {
        notificationSoundCheckBox.isEnabled = notificationEnabledCheckBox.isChecked
        notificationVibrationCheckBox.isEnabled = notificationEnabledCheckBox.isChecked

        val enabled = AppPreferences.recoveryNotificationEnabled(this)
        val permissionGranted = UsageRecoveryNotifier.hasNotificationPermission(this)
        val notificationsAvailable = UsageRecoveryNotifier.notificationsAvailable(this)

        notificationStatus.text = when {
            !enabled -> "用量恢復通知已關閉"
            !permissionGranted -> "尚未取得通知權限，提醒不會出現"
            !notificationsAvailable -> "系統已封鎖 OuroChrono 通知"
            else -> {
                val modes = buildList {
                    if (AppPreferences.notificationSoundEnabled(this@MainActivity)) add("系統通知音")
                    if (AppPreferences.notificationVibrationEnabled(this@MainActivity)) add("震動")
                }
                if (modes.isEmpty()) "通知已啟用（靜音）" else "通知已啟用：${modes.joinToString("、")}" 
            }
        }
        notificationStatus.setTextColor(
            getColor(
                when {
                    !enabled -> R.color.text_muted
                    !permissionGranted || !notificationsAvailable -> R.color.danger
                    else -> R.color.status_success
                }
            )
        )
    }


    private fun selectMainPage(page: MainPage, scrollToTop: Boolean = true) {
        pageUsage.visibility = if (page == MainPage.USAGE) View.VISIBLE else View.GONE
        pageNotifications.visibility = if (page == MainPage.NOTIFICATIONS) View.VISIBLE else View.GONE
        pageSystem.visibility = if (page == MainPage.SYSTEM) View.VISIBLE else View.GONE

        updateTabAppearance(tabUsage, page == MainPage.USAGE)
        updateTabAppearance(tabNotifications, page == MainPage.NOTIFICATIONS)
        updateTabAppearance(tabSystem, page == MainPage.SYSTEM)

        if (scrollToTop) mainContentScroll.smoothScrollTo(0, 0)
    }

    private fun updateTabAppearance(tab: TextView, selected: Boolean) {
        tab.setBackgroundResource(
            if (selected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
        )
        tab.setTextColor(
            getColor(if (selected) R.color.text_primary else R.color.text_muted)
        )
        tab.isSelected = selected
    }

    private fun showLoadingOverlay(message: String) {
        loadingStatusText.text = message
        loadingOverlay.visibility = View.VISIBLE
        loadingOverlay.bringToFront()

        if (loadingAnimator?.isRunning == true) return
        loadingRing.rotation = 0f
        loadingAnimator = ObjectAnimator.ofFloat(
            loadingRing,
            View.ROTATION,
            0f,
            360f
        ).apply {
            duration = 2_400L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun hideLoadingOverlay() {
        stopLoadingAnimation()
        loadingOverlay.visibility = View.GONE
    }

    private fun stopLoadingAnimation() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        if (::loadingRing.isInitialized) loadingRing.rotation = 0f
    }

    private fun setBusy(message: String) {
        globalStatus.text = message
    }

    private fun showOnly(panel: View) {
        panelSignedOut.visibility = if (panel === panelSignedOut) View.VISIBLE else View.GONE
        panelLoginProgress.visibility = if (panel === panelLoginProgress) View.VISIBLE else View.GONE
        panelUsage.visibility = if (panel === panelUsage) View.VISIBLE else View.GONE
        globalStatus.visibility = if (panel === panelUsage) View.GONE else View.VISIBLE
    }

    private fun startRefreshCountdown() {
        mainHandler.removeCallbacks(refreshCountdownRunnable)
        updateRefreshCountdown()
        mainHandler.postDelayed(refreshCountdownRunnable, 1_000L)
    }

    private fun stopRefreshCountdown() {
        mainHandler.removeCallbacks(refreshCountdownRunnable)
    }

    private fun updateRefreshCountdown() {
        if (!CodexTokenStore.hasTokens(this)) {
            refreshCountdown.text = "--:--"
            return
        }

        val nextRefreshAt = AppPreferences.nextRefreshAtMillis(this)
        if (nextRefreshAt <= 0L) {
            refreshCountdown.text = "--:--"
            return
        }

        val remainingSeconds = ((nextRefreshAt - System.currentTimeMillis())
            .coerceAtLeast(0L) + 999L) / 1_000L
        val minutes = remainingSeconds / 60L
        val seconds = remainingSeconds % 60L
        refreshCountdown.text = String.format(Locale.TAIWAN, "%02d:%02d", minutes, seconds)
    }

    private fun <T> runAsync(
        task: () -> T,
        success: (T) -> Unit,
        failure: (Exception) -> Unit
    ) {
        ensureExecutor()
        executor.execute {
            try {
                val result = task()
                mainHandler.post { success(result) }
            } catch (error: Exception) {
                mainHandler.post { failure(error) }
            }
        }
    }

    private fun ensureExecutor() {
        if (executor.isShutdown || executor.isTerminated) {
            executor = Executors.newSingleThreadExecutor()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private enum class MainPage {
        USAGE,
        NOTIFICATIONS,
        SYSTEM
    }

    companion object {
        private const val RESET_RED_HEART = "\u2665\uFE0F"
        private const val RESET_WHITE_HEART = "\uD83E\uDD0D"
        private const val REQUEST_NOTIFICATION_PERMISSION = 4201
    }
}
