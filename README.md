<p align="center">
  <img src="docs/images/app-icon.png" width="144" alt="OuroChrono 圖示">
</p>

# OuroChrono Android

OuroChrono 是一款非官方 Android 用量檢視工具，透過 ChatGPT／Codex Device Code 登入，在手機上顯示 Codex 的 **5 小時用量、每週用量、重置時間與可用重置次數**，並提供 4×2 桌面小工具與用量恢復通知。

> [!IMPORTANT]
> 本專案不是 OpenAI 官方產品，也未獲 OpenAI 背書。專案使用 Codex 產品內部端點，端點格式或授權流程可能隨時變動。

## 功能

- ChatGPT／Codex Device Code 登入，不要求使用者輸入密碼或 API Key。
- OAuth Token 透過 Android Keystore 與 AES-GCM 加密保存在裝置上。
- 顯示 5 小時與每週剩餘用量、已使用比例、重置倒數與重置次數。
- 自動更新週期可選 5、10、15、30 或 60 分鐘。
- 4×2 桌面小工具，顯示兩個剩餘用量圓環、重置次數與下次更新倒數。
- 用量由低於 100% 恢復到 100% 時，可發送系統通知。
- 通知音效與震動可分別控制。
- 主畫面提供「用量檢視」、「通知設定」與「系統設定」三個頁簽，並自動跟隨 Android 深色／淺色模式。
- 離線或暫時失敗時保留最後一次成功資料。

## 小工具顏色規則

中央數字與圓弧長度代表**剩餘百分比**，顏色警示依**已使用百分比**判斷。

| 已使用比例 | 每週用量 | 5 小時用量 | 狀態 |
|---:|---|---|---|
| 0%～49% | `#4CAF50` 綠色 | `#2344BA` 藍色 | 正常 |
| 50%～74% | `#FBC02D` 黃色 | `#FBC02D` 黃色 | 注意 |
| 75%～89% | `#FF9800` 橘色 | `#FF9800` 橘色 | 偏高 |
| 90%～100% | `#B3180C` 紅色 | `#B3180C` 紅色 | 即將用完 |
| 無資料 | `#667080` 灰色 | `#667080` 灰色 | 未取得資料 |

當兩個量表同時剩餘 100% 時，兩者皆使用 `#FFC73B`。動態進度弧會套用同色柔和光暈；中央百分比文字固定使用 `#595959`，並以相同的剩餘百分比控制灰色光暈強度。光暈越接近 0% 越黯淡，呈現類似電量不足的效果。單一量表剩餘 0% 時改用淡黑色 `#212121`，並完全停用光暈。小工具選擇器中的靜態預覽顯示每週 49%、5 小時 20%、重置次數 2；實際加入桌面後會改用帳號資料。

## 系統需求

- Android 8.0（API 26）以上
- 可登入 ChatGPT 且具有 Codex 用量資料的帳號
- Android Studio 與 JDK 17，僅在自行建置時需要

## 快速使用

1. 安裝簽署過的 Release APK。
2. 開啟 OuroChrono，按下「使用 ChatGPT 帳號登入」。
3. App 會複製一次性代碼並開啟 OpenAI Device Code 頁面。
4. 在瀏覽器完成授權後返回 App。
5. 選擇更新週期與通知設定。
6. 長按 Android 桌面，加入「OuroChrono 用量」小工具。

完整說明請參閱 [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md)。

## 建置

```powershell
# 靜態檢查
python scripts\validate_project.py

# Debug APK
.\gradlew.bat assembleDebug

# Release APK（需要自行設定正式簽章）
.\gradlew.bat assembleRelease
```

建置需求與簽章流程請參閱 [`docs/BUILD_AND_RELEASE.md`](docs/BUILD_AND_RELEASE.md)。

## 程式架構

```text
MainActivity
├─ DeviceLoginCoordinator ── CodexOAuthClient ── auth.openai.com
├─ CodexUsageClient ─────────────────────────── chatgpt.com/backend-api
├─ CodexTokenStore ──────────────────────────── Android Keystore
├─ UsageCache
├─ RefreshScheduler ── WorkManager / AlarmManager ── RefreshWorker
├─ OuroChronoWidget ── UsageRingRenderer / ResetCreditDisplay
└─ UsageRecoveryNotifier
```

詳細模組、資料流與檔案職責請參閱 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

## 權限

| 權限 | 用途 |
|---|---|
| `INTERNET` | 登入與讀取 Codex 用量 |
| `ACCESS_NETWORK_STATE` | 判斷網路狀態 |
| `POST_NOTIFICATIONS` | Android 13 以上顯示恢復通知 |
| `VIBRATE` | 通知震動 |
| `SCHEDULE_EXACT_ALARM` | 讓小工具倒數在 00:00 後重新開始，避免負秒數 |

## 安全與隱私

- 不讀取 ChatGPT 官方 App 的資料。
- 不保存 ChatGPT 密碼。
- OAuth Token 加密後僅保存在本機。
- 不含分析、廣告或第三方追蹤 SDK。
- `android:allowBackup="false"`，避免 Token 被系統備份。

詳見 [`PRIVACY.md`](PRIVACY.md) 與 [`SECURITY.md`](SECURITY.md)。

## 已知限制

- OpenAI 沒有為本用途提供正式公開的第三方 Codex 用量 API。
- `chatgpt.com/backend-api/wham/*` 與相關端點可能更名、限制或停止運作。
- Android 的省電模式、Doze 與廠牌排程策略可能延後背景更新。
- 不同 Launcher 對小工具格數與邊距的解讀可能不同；本專案以 4×2 為目標。
- 本專案較適合自行側載與技術測試，不保證符合 Google Play 上架政策。

## 文件

- [使用說明](docs/USER_GUIDE.md)
- [程式架構](docs/ARCHITECTURE.md)
- [建置與發布](docs/BUILD_AND_RELEASE.md)
- [GitHub 發布流程](docs/GITHUB_RELEASE_GUIDE.md)
- [直接登入流程](docs/DIRECT_AUTH.md)
- [疑難排解](docs/TROUBLESHOOTING.md)
- [隱私說明](PRIVACY.md)
- [安全政策](SECURITY.md)

## 授權

本專案採 MIT License。專案最初衍生自 Claude Pulse Android，原始授權資訊保留於 [`LICENSE`](LICENSE) 與 [`NOTICE.md`](NOTICE.md)。
