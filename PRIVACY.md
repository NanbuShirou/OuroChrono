# 隱私說明

OuroChrono 不含廣告、分析或第三方追蹤 SDK。

## 處理的資料

App 可能在裝置上處理：

- OpenAI OAuth Access Token、Refresh Token 與 ID Token。
- ChatGPT 帳號 ID、方案資訊。
- Codex 用量視窗、重置時間與重置次數。
- 更新週期與通知設定。

## 儲存方式

- OAuth Token 使用 Android Keystore AES-GCM 加密後保存在 App 私有儲存區。
- 用量快取與設定保存在 App 私有 SharedPreferences。
- `android:allowBackup="false"`，資料不透過 Android 自動備份。

## 網路傳輸

App 僅直接連線至 OpenAI／ChatGPT 網域以完成登入與用量查詢。專案本身不設置資料收集伺服器。

## 清除資料

使用 App 的登出或清除本機資料功能，或在 Android 系統中清除／解除安裝 App，即可刪除本機保存資料。

## 非官方性質

OuroChrono 不是 OpenAI 官方產品。使用者仍受 OpenAI／ChatGPT 服務條款與隱私政策約束。
