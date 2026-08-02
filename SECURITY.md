# 安全政策

## 支援版本

目前公開支援版本：`1.0.1`。

## 回報安全問題

請不要在公開 Issue 中貼出：

- Access Token、Refresh Token 或 ID Token
- Device Code
- ChatGPT Account ID
- Release JKS、密碼或憑證私鑰
- 完整 API 回應中可能包含的帳號資料

請透過 Repository 擁有者提供的私人聯絡方式回報，並附上可重現步驟、Android 版本與受影響版本。公開 Repository 尚未設定私人回報管道時，請先建立不含敏感資料的簡短 Issue，要求提供私下聯絡方式。

## 金鑰管理

Repository 不包含 Release 簽署金鑰。正式 APK 發布者必須自行保管 JKS，並確認 Git history 中從未提交私鑰或密碼。

## 威脅模型範圍

本專案可保護 Token 不以純文字儲存在一般 App 檔案中，但無法保證已 Root、被惡意系統修改或具備偵錯／注入能力的裝置仍然安全。
