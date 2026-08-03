# 疑難排解

## 登入頁顯示 Device Code 未啟用

先在 ChatGPT 安全性設定中啟用 Codex 裝置代碼授權，再重新建立代碼。

## 返回 App 後仍停在等待登入

- 按「重新檢查登入結果」。
- 確認代碼尚未過期。
- 確認手機可連線 `auth.openai.com`。
- 取消後重新建立 Device Code。

## 每週或 5 小時顯示 `--`

API 回應可能沒有提供可辨識的 300 分鐘或 10,080 分鐘視窗。OuroChrono 不會依 `primary`／`secondary` 猜測，以免顯示錯誤資料。

## Widget 預覽與實際資料不同

Android 小工具選擇器使用靜態預覽。加入桌面並完成第一次更新後，Widget 才會顯示真實帳號資料。

## Widget 還顯示舊版預覽

Launcher 可能快取預覽：

1. 移除舊 Widget。
2. 安裝新版 APK。
3. 強制停止或重新啟動 Launcher。
4. 必要時重新開機後再加入 Widget。

## 倒數顯示負秒數

Android 12 以上請允許 OuroChrono 使用「鬧鐘與提醒」權限。未授權時系統可能延後 `00:00` 邊界更新。

## 背景更新延遲

檢查：

- OuroChrono 是否被設為省電限制。
- 廠牌自啟動／背景活動權限。
- 網路是否可用。
- 是否曾強制停止 App。被強制停止後，Android 通常會暫停排程直到再次開啟 App。

## Release APK 無法覆蓋 Debug APK

兩者簽章不同。先解除安裝 Debug 版，再安裝 Release 版。未來的 Release 更新必須使用同一把正式 JKS。

## Android Studio 顯示「The destination folder does not exist or is not writeable」

這不是 Kotlin 或 Gradle 編譯錯誤，而是「Generate Signed Bundle or APK」精靈記住了不存在或不可寫入的輸出路徑。

處理方式：

1. 在 Destination Folder 選擇專案內已存在的 `dist` 資料夾，或自行建立一個可寫入資料夾。
2. 不要選擇 ZIP 內部、唯讀資料夾、已不存在的磁碟機，或受系統保護的目錄。
3. 若 Android Studio 仍自動帶入舊路徑，關閉專案後刪除 `.idea`，再重新開啟專案。
4. Debug APK 可直接執行根目錄的 `BUILD_DEBUG_APK.bat`，輸出在 `dist\OuroChrono-debug.apk`。
