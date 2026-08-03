# GitHub 發布流程

## 1. 建立 Repository

建議名稱：

```text
OuroChrono-Android
```

Repository 說明範例：

```text
Unofficial Android Codex usage monitor with Device Code login and a 4×2 home-screen widget.
```

建議主題：

```text
android kotlin codex chatgpt appwidget usage-monitor
```

## 2. 上傳原始碼

在專案根目錄執行：

```powershell
git init
git branch -M main
git add .
git commit -m "Release OuroChrono v1.0.1"
git remote add origin https://github.com/<帳號>/<Repository>.git
git push -u origin main
```

提交前先確認：

```powershell
git status
git ls-files | Select-String -Pattern "jks|keystore|local.properties|apk|aab"
```

搜尋結果應為空。公開私鑰這件事通常只需要犯一次，就足以讓後續版本永遠帶著簽章災難生活。

## 3. 建立 Tag

```powershell
git tag -a v1.0.1 -m "OuroChrono v1.0.1"
git push origin v1.0.1
```

## 4. 建立 GitHub Release

1. 進入 Repository 的 **Releases**。
2. 選擇 **Draft a new release**。
3. Tag 選 `v1.0.1`。
4. Release title 填 `OuroChrono v1.0.1`。
5. 將 `RELEASE_NOTES_v1.0.1.md` 內容貼到說明。
6. 附加自行編譯的 Signed Release APK。
7. 附加 `SHA256SUMS.txt`。
8. 確認不是 Debug APK，再發布。

建議 APK 名稱：

```text
OuroChrono-v1.0.1-release.apk
```

## 5. 不要放進 Repository 的檔案

- Release JKS 或任何簽署金鑰
- KeyStore／Key 密碼
- OAuth Token、帳號資料或除錯回應
- `local.properties`
- `.idea/`、`.gradle/`、`build/`
- Debug／Release APK；APK 應放在 GitHub Release Assets，而不是 Git history
