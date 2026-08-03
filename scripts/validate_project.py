#!/usr/bin/env python3
from __future__ import annotations

import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
KOTLIN = ROOT / "app" / "src" / "main" / "java" / "tw" / "nanbu" / "ourochrono"


def main() -> int:
    errors: list[str] = []
    xml_files = sorted(RES.rglob("*.xml")) + [ROOT / "app" / "src" / "main" / "AndroidManifest.xml"]
    for path in xml_files:
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            errors.append(f"XML 解析失敗：{path.relative_to(ROOT)}：{exc}")

    declared_ids: set[str] = set()
    declared_layouts = {path.stem for path in (RES / "layout").glob("*.xml")}
    for path in RES.rglob("*.xml"):
        text = path.read_text(encoding="utf-8")
        declared_ids.update(re.findall(r"@\+id/([A-Za-z0-9_]+)", text))

    used_ids: set[str] = set()
    used_layouts: set[str] = set()
    for path in KOTLIN.glob("*.kt"):
        text = path.read_text(encoding="utf-8")
        used_ids.update(re.findall(r"R\.id\.([A-Za-z0-9_]+)", text))
        used_layouts.update(re.findall(r"R\.layout\.([A-Za-z0-9_]+)", text))

    missing_ids = sorted(used_ids - declared_ids)
    missing_layouts = sorted(used_layouts - declared_layouts)
    if missing_ids:
        errors.append("缺少 R.id 資源：" + ", ".join(missing_ids))
    if missing_layouts:
        errors.append("缺少 R.layout 資源：" + ", ".join(missing_layouts))

    forbidden = ["RelayApiClient", "SecureSessionStore", "relay_token", "relay_url"]
    for path in KOTLIN.glob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for token in forbidden:
            if token in text:
                errors.append(f"仍包含舊 Relay 符號：{token}，檔案：{path.name}")

    usage_client = (KOTLIN / "CodexUsageClient.kt").read_text(encoding="utf-8")
    usage_models = (KOTLIN / "UsageModels.kt").read_text(encoding="utf-8")
    usage_cache = (KOTLIN / "UsageCache.kt").read_text(encoding="utf-8")

    forbidden_window_fallbacks = [
        'scope == "primary" -> 300',
        'else -> 10_080',
        'limitName = "5 小時"',
        'limitName = "每週"',
    ]
    for token in forbidden_window_fallbacks:
        if token in usage_client:
            errors.append(f"仍包含依 primary/secondary 推測週期的邏輯：{token!r}")

    if 'window.windowDurationMinutes == durationMinutes' not in usage_models:
        errors.append("用量視窗未以實際週期長度進行精確分類")
    if 'CACHE_SCHEMA_VERSION = 2' not in usage_cache:
        errors.append("用量快取 schema 未更新為第 2 版")

    widget_detail = (RES / "layout" / "widget_detail.xml").read_text(encoding="utf-8")
    widget_compact = (RES / "layout" / "widget_compact.xml").read_text(encoding="utf-8")
    widget_code = (KOTLIN / "OuroChronoWidget.kt").read_text(encoding="utf-8")
    reset_code = (KOTLIN / "ResetCreditDisplay.kt").read_text(encoding="utf-8")

    for layout_name, layout_text, expected_ring_size in [
        ("詳細版", widget_detail, "113dp"),
        ("窄版", widget_compact, "101dp"),
    ]:
        for obsolete in [
            "widget_plan",
            "widget_weekly_progress",
            "widget_weekly_percent",
            "widget_short_percent",
            "widget_status",
            "widget_weekly_reset",
            "widget_short_reset",
            "widget_reset_icon_1",
            "widget_reset_icon_2",
            "widget_reset_icon_3",
            "widget_reset_multiplier",
        ]:
            if obsolete in layout_text:
                errors.append(f"{layout_name}小工具仍包含應移除的元件：{obsolete}")
        if 'android:text="OuroChrono"' in layout_text:
            errors.append(f"{layout_name}小工具仍顯示 OuroChrono 標題列")

        for ring_id in ["widget_weekly_ring", "widget_short_ring"]:
            marker = f'android:id="@+id/{ring_id}"'
            if marker not in layout_text:
                errors.append(f"{layout_name}小工具缺少用量圓環：{ring_id}")
                continue
            ring_start = layout_text.find(marker)
            ring_block = layout_text[ring_start:ring_start + 500]
            if (
                f'android:layout_width="{expected_ring_size}"' not in ring_block
                or f'android:layout_height="{expected_ring_size}"' not in ring_block
            ):
                errors.append(f"{layout_name}小工具圓環尺寸不是 {expected_ring_size}：{ring_id}")

        for required_id in ["widget_reset_hearts", "widget_divider", "widget_refresh"]:
            if f"@+id/{required_id}" not in layout_text:
                errors.append(f"{layout_name}小工具缺少底部元件：{required_id}")
        if "widget_reset_label" in layout_text or 'android:text="重置次數："' in layout_text:
            errors.append(f"{layout_name}小工具仍顯示重置次數文字")
        if 'android:text="♥️♥️🤍"' not in layout_text:
            errors.append(f"{layout_name}小工具的愛心預覽不是 2 顆紅心與 1 顆白心")
        if "<Chronometer" not in layout_text or 'android:text="↻ 05:00"' not in layout_text:
            errors.append(f"{layout_name}小工具未使用帶倒數的立即更新 Chronometer")

        reset_marker = 'android:id="@+id/widget_reset_hearts"'
        refresh_marker = 'android:id="@+id/widget_refresh"'
        for marker in [reset_marker, refresh_marker]:
            marker_start = layout_text.find(marker)
            marker_block = layout_text[marker_start:marker_start + 450]
            if 'android:layout_width="0dp"' not in marker_block or 'android:layout_weight="1"' not in marker_block:
                errors.append(f"{layout_name}小工具底列未維持左右各半：{marker}")

    if "ResetCreditDisplayFactory.create" not in widget_code:
        errors.append("小工具未使用統一的重置次數顯示規則")
    if 'private const val RED_HEART = "\\u2665\\uFE0F"' not in widget_code:
        errors.append("紅心未固定使用 U+2665 U+FE0F")
    if 'private const val WHITE_HEART = "\\uD83E\\uDD0D"' not in widget_code:
        errors.append("白心未固定使用 U+1F90D")
    for required_reset_rule in [
        "enum class ResetHeartState",
        "RED,",
        "WHITE",
        "List(MAX_INDIVIDUAL_HEARTS)",
        "if (index < count) ResetHeartState.RED else ResetHeartState.WHITE",
        "multiplier = count",
    ]:
        if required_reset_rule not in reset_code:
            errors.append(f"重置次數規則缺少：{required_reset_rule}")
    if "widget_reset_label" in widget_code:
        errors.append("小工具程式仍引用重置次數標籤")
    if 'display.multiplier != null -> "$RED_HEART×${display.multiplier}"' not in widget_code:
        errors.append("4 次以上未使用紅心乘以次數")
    if 'ResetHeartState.RED -> RED_HEART' not in widget_code or 'ResetHeartState.WHITE -> WHITE_HEART' not in widget_code:
        errors.append("0 至 3 次未依狀態輸出紅心與白心")

    ring_renderer = (KOTLIN / "UsageRingRenderer.kt").read_text(encoding="utf-8")
    for token in ["startAngle = -90f + gapAngle / 2f", "GAP_LENGTH_DP = 15f"]:
        if token not in ring_renderer:
            errors.append(f"用量圓環缺少必要繪製規格：{token}")
    if "setImageViewBitmap" not in widget_code:
        errors.append("小工具未以 Bitmap 綁定用量圓環")
    for token in [
        "weeklyRemaining = weekly?.remainingPercent",
        "weeklyUsed = weekly?.usedPercent",
        "shortRemaining = short?.remainingPercent",
        "shortUsed = short?.usedPercent",
        "weeklyRemaining == 100 && shortRemaining == 100",
        "0xFF4CAF50",
        "0xFF2344BA",
        "0xFFFBC02D",
        "0xFFFF9800",
        "0xFFB3180C",
        "0xFF667080",
        "0xFF212121",
        "0xFFFFC73B",
    ]:
        if token not in widget_code:
            errors.append(f"小工具百分比色階缺少：{token}")
    for token in [
        "displayPercent: Int?",
        "color = accentColor",
        "withAlpha(accentColor, TRACK_ALPHA)",
        "MAX_GLOW_RADIUS_DP = 4.5f",
        "setShadowLayer(",
        "MAX_GLOW_ALPHA * strength",
        "val strength = percent.coerceIn(0, 100) / 100f",
        "MAX_GLOW_RADIUS_DP * strength",
        "MAX_TEXT_GLOW_ALPHA * strength",
        "TEXT_COLOR: Int = 0xFF595959.toInt()",
        "color = TEXT_COLOR",
        "withAlpha(TEXT_COLOR, glow.textAlpha)",
    ]:
        if token not in ring_renderer:
            errors.append(f"圓環動態色彩繪製缺少：{token}")
    for token in [
        "remainingPercent: Int?",
        "remainingPercent == 0 -> ZERO_PERCENT_COLOR",
        "ZERO_PERCENT_COLOR: Int = 0xFF212121.toInt()",
    ]:
        if token not in widget_code:
            errors.append(f"0% 淡黑色規則缺少：{token}")

    for layout_name, layout_text in [("詳細版", widget_detail), ("窄版", widget_compact)]:
        if layout_text.count('android:gravity="center"') < 5:
            errors.append(f"{layout_name}小工具四格內容未全部置中")
        if 'android:layout_height="0dp"' not in layout_text or 'android:layout_weight="1"' not in layout_text:
            errors.append(f"{layout_name}小工具上方列未使用剩餘高度")
    for token in [
        "ACTION_REFRESH_COUNTDOWN_EXPIRED",
        "onCountdownExpired(context)",
    ]:
        if token not in widget_code:
            errors.append(f"小工具倒數輪替缺少：{token}")
    refresh_scheduler = (KOTLIN / "RefreshScheduler.kt").read_text(encoding="utf-8")
    for token in [
        "scheduleCountdownAlarm(context, nextAt)",
        "setExactAndAllowWhileIdle",
        "setAndAllowWhileIdle",
        "fun onCountdownExpired(context: Context)",
    ]:
        if token not in refresh_scheduler:
            errors.append(f"倒數排程缺少：{token}")
    for token in ["SystemClock.elapsedRealtime()", "setChronometer(", "setChronometerCountDown", "REFRESH_COUNTDOWN_FORMAT = \"↻ %s\""]:
        if token not in widget_code:
            errors.append(f"小工具更新倒數缺少必要實作：{token}")
    for removed_token in ["widget_weekly_reset", "widget_short_reset", "formatWidgetReset"]:
        if removed_token in widget_code:
            errors.append(f"小工具程式仍包含已移除的重置時間邏輯：{removed_token}")

    for diameter, stroke in ((101.0, 9.8), (113.0, 11.0)):
        radius = (diameter - stroke) / 2.0
        gap_angle = (15.0 + stroke) / radius * 180.0 / math.pi
        visible_gap = gap_angle * math.pi / 180.0 * radius - stroke
        if abs(visible_gap - 15.0) > 0.001:
            errors.append(f"{diameter:g}dp 圓環的可見缺口不是 15dp")

    widget_info = (RES / "xml" / "ourochrono_widget_info.xml").read_text(encoding="utf-8")
    for token in [
        'android:targetCellWidth="4"',
        'android:targetCellHeight="2"',
        'android:minWidth="250dp"',
        'android:minHeight="80dp"',
        'android:minResizeHeight="80dp"',
        'android:maxResizeHeight="110dp"',
        'android:resizeMode="horizontal"',
    ]:
        if token not in widget_info:
            errors.append(f"4×2 小工具尺寸設定缺少：{token}")

    if 'val compact = minWidth < 235' not in widget_code:
        errors.append("4×2 小工具的寬度切換條件不符合預期")
    if 'OPTION_APPWIDGET_MIN_HEIGHT' in widget_code:
        errors.append("4×2 小工具版面選擇不應依賴高度")

    for launcher_name in ["ic_launcher_foreground.xml", "ic_launcher_monochrome.xml"]:
        launcher_xml = (RES / "drawable" / launcher_name).read_text(encoding="utf-8")
        for edge in ["left", "top", "right", "bottom"]:
            if f'android:{edge}="14dp"' not in launcher_xml:
                errors.append(f"{launcher_name} 未套用約 1.6 倍的圖示放大：{edge}")

    main_layout = (RES / "layout" / "activity_main.xml").read_text(encoding="utf-8")
    night_colors = RES / "values-night" / "colors.xml"
    night_styles = RES / "values-night" / "styles.xml"
    if not night_colors.exists() or not night_styles.exists():
        errors.append("主畫面缺少 values-night 深色模式資源")
    else:
        night_style_text = night_styles.read_text(encoding="utf-8")
        if "Theme.Material.NoActionBar" not in night_style_text:
            errors.append("深色模式未使用深色 Material 主題")
        if "<item name=\"android:windowLightStatusBar\">false</item>" not in night_style_text:
            errors.append("深色模式狀態列圖示未切換為亮色")
    for token in [
        '@color/text_primary',
        '@color/text_secondary',
        '@color/text_muted',
        '@color/text_faint',
        '@drawable/bg_usage_primary',
    ]:
        if token not in main_layout:
            errors.append(f"主畫面日夜主題資源缺少：{token}")

    strings_text = (RES / "values" / "strings.xml").read_text(encoding="utf-8")
    for token in [
        '<string name="app_name">OuroChrono</string>',
        '<string name="app_version">OuroChrono v1.0.1</string>',
        '<string name="widget_name">OuroChrono 用量</string>',
    ]:
        if token not in strings_text:
            errors.append(f"OuroChrono 品牌字串缺少：{token}")

    for token in [
        '@+id/loading_overlay',
        '@+id/loading_ring',
        '@+id/loading_status_text',
        '@drawable/ourochrono_loading_ring',
        '@drawable/ourochrono_hourglass',
    ]:
        if token not in main_layout:
            errors.append(f"載入／更新畫面缺少：{token}")

    main_activity = (KOTLIN / "MainActivity.kt").read_text(encoding="utf-8")
    for token in [
        'showLoadingOverlay(getString(R.string.updating_status))',
        'ObjectAnimator.ofFloat(',
        'repeatCount = ValueAnimator.INFINITE',
        'hideLoadingOverlay()',
    ]:
        if token not in main_activity:
            errors.append(f"載入／更新動畫實作缺少：{token}")

    build_gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    if 'versionCode = 2' not in build_gradle or 'versionName = "1.0.1"' not in build_gradle:
        errors.append("Gradle 版本不是 1.0.1／versionCode 2")
    if "@string/app_version" not in main_layout:
        errors.append("主畫面未使用 OuroChrono 版本字串資源")

    if errors:
        print("驗證失敗：")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"XML：{len(xml_files)} 個，全部可解析")
    print(f"Kotlin R.id：{len(used_ids)} 個，全部有資源")
    print(f"Kotlin R.layout：{len(used_layouts)} 個，全部有資源")
    print("舊 Relay 類別與設定鍵：未發現")
    return 0


if __name__ == "__main__":
    sys.exit(main())
