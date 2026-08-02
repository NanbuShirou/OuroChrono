# OuroChrono v1.0.0

OuroChrono 首次公開版本。這是一款非官方 Android Codex 用量檢視工具，可直接使用 ChatGPT Device Code 登入，並在 App 與 4×2 桌面小工具中顯示 5 小時／每週剩餘用量。

## 主要功能

- Device Code 登入，不需輸入 API Key。
- OAuth Token 由 Android Keystore 加密保存。
- 顯示每週、5 小時用量與重置資訊。
- 5～60 分鐘可選自動更新週期。
- 4×2 桌面小工具、立即更新與更新倒數。
- 動態量表加入電量式光暈：剩餘越多越明亮，剩餘越低越黯淡。
- 中央百分比文字固定使用 `#595959`，不跟隨量表警示色。
- 量表剩餘 0% 時使用淡黑色 `#212121` 並停止發光。
- 用量恢復至 100% 時通知。
- 自動跟隨手機深色／淺色模式。

## 安裝

下載並安裝：

```text
OuroChrono-v1.0.0-release.apk
```

從 Debug 版切換到 Release 版時，因簽章不同，可能必須先解除安裝舊版。

## 注意

- 本專案不是 OpenAI 官方產品。
- 使用的是 Codex 產品內部端點，未來可能因服務變更失效。
- APK 應以本 Release 附帶的 SHA-256 檔案驗證。
