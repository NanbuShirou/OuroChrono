# 建置與 Release APK

## 1. 建置環境

- Android Studio
- JDK 17
- Android SDK 34
- Gradle Wrapper 8.5
- Android Gradle Plugin 8.2.2
- Kotlin 1.9.22

## 2. 開啟專案

使用 Android Studio 開啟包含下列檔案的專案根目錄：

```text
settings.gradle.kts
build.gradle.kts
gradlew.bat
app/
```

等待 Gradle Sync 完成。若電腦尚未設定 Android SDK 路徑，可由 Android Studio 自動建立 `local.properties`。該檔案不可提交到 GitHub。

## 3. 靜態驗證

Windows：

```powershell
python scripts\validate_project.py
```

驗證項目包含 XML、Kotlin 資源 ID、Widget 版面、色階、版本號與已移除的舊 Relay 符號。

## 4. Debug APK

```powershell
.\gradlew.bat clean assembleDebug
```

輸出：

```text
app\build\outputs\apk\debug\app-debug.apk
```

Debug APK 僅供開發測試。

## 5. 正式簽署 Release APK

Android Studio：

```text
Build
→ Generate Signed Bundle / APK
→ APK
→ Choose existing...
→ 選擇自己的 Release JKS
→ Build Variant: release
→ Create
```

輸出通常位於：

```text
app\build\outputs\apk\release\app-release.apk
```

### 金鑰安全

絕對不要提交：

- `*.jks`
- `*.keystore`
- KeyStore 密碼
- Key Alias 密碼
- `local.properties`
- `keystore.properties`

同一個應用程式的後續更新必須持續使用同一把正式簽署金鑰。金鑰遺失或更換後，既有安裝通常無法直接覆蓋更新。

## 6. 版本資訊

本公開版本固定為：

```kotlin
versionCode = 2
versionName = "1.0.1"
```

Git tag 建議使用：

```text
v1.0.1
```

## 7. 產生 SHA-256

PowerShell：

```powershell
Get-FileHash .\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

建議將結果保存為 `SHA256SUMS.txt`，與 APK 一起附加到 GitHub Release。

## 8. 發布前檢查

- `python scripts\validate_project.py` 通過。
- Release APK 使用正式 JKS 簽署。
- APK 可在乾淨裝置安裝。
- Device Code 登入成功。
- 每週與 5 小時用量不互換。
- Widget 能加入桌面並立即更新。
- 倒數到 `00:00` 後不顯示負秒數。
- 深色與淺色模式皆可閱讀。
- Repository 中沒有金鑰、密碼、Token、APK 或本機路徑。
