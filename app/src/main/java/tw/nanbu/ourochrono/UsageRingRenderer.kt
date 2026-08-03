package tw.nanbu.ourochrono

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Produces a small bitmap for RemoteViews because App Widgets cannot host a custom View.
 * The requested gap is the visible gap after round line caps are taken into account.
 */
object UsageRingRenderer {
    private const val TRACK_COLOR: Int = 0xFF39414D.toInt()
    private const val TEXT_COLOR: Int = 0xFF595959.toInt()
    private const val GAP_LENGTH_DP = 15f
    private const val TRACK_ALPHA = 0x55

    // The remaining percentage behaves like battery charge: a full meter has the
    // strongest halo, and the halo fades continuously as the remaining value falls.
    private const val MAX_GLOW_RADIUS_DP = 4.5f
    private const val MAX_GLOW_ALPHA = 0xA0
    private const val MAX_GLOW_STROKE_ALPHA = 0x42
    private const val MAX_TEXT_GLOW_RADIUS_DP = 2.2f
    private const val MAX_TEXT_GLOW_ALPHA = 0x78

    fun create(
        context: Context,
        displayPercent: Int?,
        accentColor: Int,
        diameterDp: Int,
        textSizeSp: Float,
        strokeWidthDp: Float
    ): Bitmap {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val textDensity = metrics.scaledDensity.coerceAtMost(density * 1.15f)
        val sizePx = (diameterDp * density).roundToInt().coerceAtLeast(1)
        val strokeWidthPx = strokeWidthDp * density
        val requestedVisibleGapPx = GAP_LENGTH_DP * density

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Always reserve the maximum possible halo area. The ring therefore stays
        // in the same position while only the halo strength changes with capacity.
        val edgeInsetPx = strokeWidthPx / 2f + MAX_GLOW_RADIUS_DP * density
        val radius = (sizePx - edgeInsetPx * 2f) / 2f
        val bounds = RectF(
            edgeInsetPx,
            edgeInsetPx,
            sizePx - edgeInsetPx,
            sizePx - edgeInsetPx
        )

        // Round caps extend by roughly half a stroke at both ends. Add one stroke to
        // the geometric gap so the final visible opening remains about 15dp.
        val gapAngle = (((requestedVisibleGapPx + strokeWidthPx) / radius) * 180f / PI)
            .toFloat()
            .coerceIn(12f, 90f)
        val availableSweep = 360f - gapAngle
        val startAngle = -90f + gapAngle / 2f

        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
        }

        val percent = displayPercent?.coerceIn(0, 100)
        arcPaint.color = when (percent) {
            null -> TRACK_COLOR
            0 -> accentColor
            else -> withAlpha(accentColor, TRACK_ALPHA)
        }
        canvas.drawArc(bounds, startAngle, availableSweep, false, arcPaint)

        val glow = glowSpec(percent)
        if (percent != null && percent > 0) {
            val progressSweep = availableSweep * percent / 100f
            if (glow != null) {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokeWidthPx
                    strokeCap = Paint.Cap.ROUND
                    color = withAlpha(accentColor, glow.strokeAlpha)
                    setShadowLayer(
                        glow.radiusDp * density,
                        0f,
                        0f,
                        withAlpha(accentColor, glow.shadowAlpha)
                    )
                }
                canvas.drawArc(bounds, startAngle, progressSweep, false, glowPaint)
            }

            arcPaint.clearShadowLayer()
            arcPaint.color = accentColor
            canvas.drawArc(bounds, startAngle, progressSweep, false, arcPaint)
        }

        val label = percent?.let { "$it%" } ?: "--"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_COLOR
            textAlign = Paint.Align.CENTER
            textSize = textSizeSp * textDensity
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            if (glow != null) {
                setShadowLayer(
                    glow.textRadiusDp * density,
                    0f,
                    0f,
                    withAlpha(TEXT_COLOR, glow.textAlpha)
                )
            }
        }
        val baseline = sizePx / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, sizePx / 2f, baseline, textPaint)

        return bitmap
    }

    /**
     * Uses the remaining percentage directly as halo strength.
     * 100% = full halo, 50% = half halo, 1% = almost extinguished, 0% = no halo.
     */
    private fun glowSpec(percent: Int?): GlowSpec? {
        if (percent == null || percent <= 0) return null

        val strength = percent.coerceIn(0, 100) / 100f
        return GlowSpec(
            radiusDp = MAX_GLOW_RADIUS_DP * strength,
            shadowAlpha = (MAX_GLOW_ALPHA * strength).roundToInt(),
            strokeAlpha = (MAX_GLOW_STROKE_ALPHA * strength).roundToInt(),
            textRadiusDp = MAX_TEXT_GLOW_RADIUS_DP * strength,
            textAlpha = (MAX_TEXT_GLOW_ALPHA * strength).roundToInt()
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
    }

    private data class GlowSpec(
        val radiusDp: Float,
        val shadowAlpha: Int,
        val strokeAlpha: Int,
        val textRadiusDp: Float,
        val textAlpha: Int
    )
}
