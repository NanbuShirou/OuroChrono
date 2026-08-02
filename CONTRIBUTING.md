# 貢獻指南

## 開發前

- 使用 JDK 17 與 Android SDK 34。
- 不提交 `local.properties`、APK、AAB、JKS、Token 或帳號資料。
- 修改用量解析時，不得以 `primary`／`secondary` 直接假設 5 小時或每週視窗。

## 驗證

```powershell
python scripts\validate_project.py
.\gradlew.bat assembleDebug
```

提交 Widget 版面修改時，至少確認：

- 詳細與精簡版皆不裁切。
- 每週與 5 小時數值未交換。
- 中央數字是剩餘百分比。
- 色階依已使用百分比。
- 倒數不會越過 00:00 後持續顯示負數。

## Commit 建議

使用清楚、單一目的的訊息，例如：

```text
Fix weekly usage window classification
Update widget preview assets
Document signed release build
```
