# OuroChrono 程式架構

## 1. 整體設計

OuroChrono 採單一 Android `app` 模組，不使用後端 Relay。登入、Token 保存、用量查詢、排程、快取、通知與桌面小工具皆在手機端完成。

```text
┌──────────────────────────────────────────────────────────────┐
│                         Android App                          │
│                                                              │
│  MainActivity                                                │
│      │                                                       │
│      ├─ DeviceLoginCoordinator ── CodexOAuthClient           │
│      │                                  │                    │
│      │                                  └─ auth.openai.com   │
│      │                                                       │
│      ├─ CodexUsageClient ─────────────── chatgpt.com         │
│      │                                                       │
│      ├─ CodexTokenStore ──────────────── Android Keystore    │
│      ├─ UsageCache ───────────────────── SharedPreferences   │
│      ├─ AppPreferences ───────────────── SharedPreferences   │
│      │                                                       │
│      ├─ RefreshScheduler ── WorkManager / AlarmManager       │
│      │                         │                             │
│      │                         └─ RefreshWorker              │
│      │                                                       │
│      ├─ OuroChronoWidget ── UsageRingRenderer                  │
│      │                  └─ ResetCreditDisplay                │
│      │                                                       │
│      └─ UsageRecoveryNotifier ───────── Android Notification │
└──────────────────────────────────────────────────────────────┘
```

## 2. 主要模組

| 檔案 | 職責 |
|---|---|
| `MainActivity.kt` | 主畫面、登入狀態、用量顯示、設定與手動更新入口 |
| `DeviceLoginCoordinator.kt` | 在 Activity 重建或切換瀏覽器時持續輪詢 Device Code 登入結果 |
| `CodexOAuthClient.kt` | Device Code 請求、Token 交換、Refresh Token 更新與登出撤銷 |
| `CodexTokenStore.kt` | 使用 Android Keystore 建立 AES 金鑰，以 AES-GCM 加密 OAuth Token |
| `PendingDeviceLoginStore.kt` | 暫存尚未完成的 Device Code 工作階段 |
| `JwtClaimsParser.kt` | 從 Token claims 取得帳號與方案資訊 |
| `CodexUsageClient.kt` | 呼叫用量與重置次數端點，將 JSON 轉成 `UsageSnapshot` |
| `UsageWindowParser.kt` | 解析用量視窗，依實際週期長度辨識 5 小時與每週資料 |
| `UsageModels.kt` | 帳號、用量視窗與快照資料模型 |
| `UsageCache.kt` | 保存最後一次成功用量；失敗時標記資料過期但保留可視內容 |
| `AppPreferences.kt` | 更新週期、通知選項、下次更新時間與排程世代 |
| `RefreshScheduler.kt` | 建立下一次單次 WorkManager 工作，並安排倒數歸零鬧鐘 |
| `RefreshWorker.kt` | 背景取得用量、寫入快取、更新 Widget、判斷恢復通知 |
| `OuroChronoWidget.kt` | AppWidgetProvider，處理 Widget 更新、點擊與 RemoteViews 綁定 |
| `UsageRingRenderer.kt` | 以 Canvas 產生圓環 Bitmap，供 RemoteViews 顯示 |
| `ResetCreditDisplay.kt` | 將重置次數轉成紅心／白心或 `♥️×N` |
| `UsageRecoveryNotifier.kt` | 用量恢復至 100% 時建立通知頻道並發送通知 |
| `UsageFormatter.kt` | 重置倒數、更新時間與方案文字格式化 |

## 3. 登入資料流

```text
使用者按下登入
    ↓
CodexOAuthClient.requestDeviceCode()
    ↓
保存 PendingDeviceLoginStore
    ↓
開啟 https://auth.openai.com/codex/device
    ↓
DeviceLoginCoordinator 背景輪詢
    ↓
取得 access_token / refresh_token / id_token
    ↓
CodexTokenStore 使用 Android Keystore 加密保存
    ↓
清除 PendingDeviceLoginStore，啟動第一次用量更新
```

`DeviceLoginCoordinator` 為 application-scoped 協調器，避免使用者切換到瀏覽器後，因 `MainActivity` 被系統重建而中斷登入。

## 4. 用量更新資料流

```text
手動更新或 RefreshWorker 啟動
    ↓
CodexOAuthClient.getValidTokens()
    ├─ Token 有效：直接使用
    └─ Token 即將到期／收到 401：使用 Refresh Token 更新
    ↓
CodexUsageClient.getUsage()
    ↓
UsageWindowParser 依 window_minutes 分類
    ├─ 300 分鐘：5 小時視窗
    └─ 10,080 分鐘：每週視窗
    ↓
UsageCache.saveSuccessful()
    ↓
OuroChronoWidget.updateAllWidgets()
    ↓
UsageRecoveryNotifier.notifyIfRecovered()
```

若 API 缺少週期長度，程式不會依 `primary`／`secondary` 猜測視窗類型，避免將每週用量誤顯示成 5 小時用量。

## 5. 背景排程

WorkManager 的週期工作最短為 15 分鐘，而 OuroChrono 支援 5 與 10 分鐘，因此使用「完成後再排下一個單次工作」的方式：

1. `RefreshScheduler.schedulePeriodic()` 建立下一次單次工作。
2. `RefreshWorker` 開始時驗證排程世代，避免舊工作接管新設定。
3. 工作完成後由 `scheduleNextAfterRun()` 安排下一次工作。
4. `AlarmManager` 在倒數邊界附近重新繪製 Widget，使 `00:00` 後切換到新週期而不是顯示負秒數。

Android 仍可能因 Doze、省電模式或廠牌策略延後真正的網路工作。Widget 倒數代表預定時間，不是即時執行保證。

## 6. Widget 架構

- `widget_detail.xml`：較寬版面，兩個 113dp 圓環。
- `widget_compact.xml`：較窄版面，兩個 101dp 圓環。
- `OuroChronoWidget` 依 Widget 最小寬度選擇版面。
- `UsageRingRenderer` 產生包含圓弧、15dp 頂部缺口與中央百分比的 Bitmap。
- 上方兩格顯示每週與 5 小時用量。
- 下方左格顯示重置次數；右格顯示可點擊的更新倒數。

顯示規則：

- 圓弧長度與中央數字：剩餘百分比。
- 顏色：已使用百分比。
- 無資料：灰色並顯示 `--`。
- 兩個視窗同時剩餘 100%：金黃色 `#FFC73B`。
- 單一視窗剩餘 0%：淡黑色 `#212121`，不繪製進度光暈。
- 其餘有效進度：進度弧使用目前警示色及同色光暈；中央數字固定為 `#595959` 並使用灰色光暈。兩者的光暈半徑與透明度皆依剩餘百分比線性衰減。

## 7. 本機資料

| 儲存區 | 內容 | 保護方式 |
|---|---|---|
| `ourochrono_oauth_session` | OAuth Token JSON | Android Keystore AES-GCM |
| `ourochrono_usage_cache` | 最後一次用量快照 | App 私有 SharedPreferences |
| `ourochrono_app_preferences` | 更新週期與通知設定 | App 私有 SharedPreferences |
| Pending login store | Device Code 暫存 | App 私有 SharedPreferences |

App 設定 `android:allowBackup="false"`，避免 OAuth Token 被 Android 自動備份。

## 8. 網路端點

OuroChrono 僅直接連線至：

- `https://auth.openai.com`：Device Code、Token 更新與撤銷。
- `https://chatgpt.com/backend-api/wham/usage`
- `https://chatgpt.com/backend-api/codex/usage`
- `https://chatgpt.com/backend-api/wham/rate-limit-reset-credits`

最後三項屬產品內部端點，並非穩定公開 API。
