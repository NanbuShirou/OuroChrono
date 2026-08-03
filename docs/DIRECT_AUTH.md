# Direct Device Code 登入

OuroChrono 不使用自建 Relay，也不讀取 ChatGPT 官方 Android App 的 Token。登入流程直接在手機與 OpenAI 驗證服務之間完成。

## 流程

1. App 向 `https://auth.openai.com/api/accounts/deviceauth/usercode` 取得 Device Code。
2. App 顯示並複製 `user_code`，開啟 `https://auth.openai.com/codex/device`。
3. 使用者在 OpenAI 頁面完成授權。
4. `DeviceLoginCoordinator` 輪詢 Device Code Token 端點。
5. 成功後取得 Access Token、Refresh Token 與 ID Token。
6. `CodexTokenStore` 使用 Android Keystore AES-GCM 加密保存 Token。
7. Access Token 即將到期或 API 回傳 401 時，App 使用 Refresh Token 更新。
8. 登出時 App 嘗試呼叫撤銷端點，之後清除本機資料。

## 安全邊界

- 使用者密碼只在 OpenAI 官方登入頁輸入。
- App 不接觸或保存密碼。
- Token 不以純文字寫入檔案。
- Token 無法跨裝置備份還原，因為加密金鑰位於該裝置的 Android Keystore。

## 相容性提醒

Device Code 流程與 Client ID 依 Codex CLI 可觀察行為實作。這不表示 OpenAI 承諾第三方 App 的長期相容性。登入端點、Client ID 或授權政策變動時，本功能可能需要更新。
