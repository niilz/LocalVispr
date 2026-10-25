package dev.localvispr.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.sin

// ==============================================================================
// Companion equalizer pill modelled after Wispr Flow's `BubblePOPOComposable`
// and `WaveformKt`
// ==============================================================================
//
// Appears next to the circular Cyberpunk-V bubble only while a dictation is
// active (`LISTENING` or `PROCESSING`):
// - Left: compact "✕" cancel circle to discard the recording.
// - Centre: dark metallic pill with 10 symmetric equalizer bars using the exact
//   bell-curve envelope from the reference app (`BAR_SCALES`). When `audioLevel`
//   is 0 (silence), the bars rest completely still as minimal dots/ticks; they
//   only animate while actual speech energy arrives.
// - During `PROCESSING` (`FrozenTranscription`), the bars freeze at 33 % opacity
//   and a spinning neon arc indicates local transcription/cleanup.

class WaveformPillView(
    context: Context,
    private val onCancelTapped: () -> Unit,
    private val onConfirmTapped: () -> Unit,
) : View(context) {

    private var state: DictationState = DictationState.IDLE
    private var targetAudioLevel: Float = 0f
    private var smoothedAudioLevel: Float = 0f
    private val barHeights = FloatArray(NUM_BARS)
    private val frozenHeights = FloatArray(NUM_BARS)
    private val startTimeNanos = System.nanoTime()

    private val pillRect = RectF()
    private val barRect = RectF()
    private val spinnerRect = RectF()

    private val housingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cancelDiscPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#140C24") }
    private val cancelCrossPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#F472B6")
        }
    private val spinnerPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#6EE7F9")
        }

    fun setDictationState(next: DictationState) {
        if (state == next) return
        if (next == DictationState.PROCESSING) {
            // Freeze the last bar snapshot just like `WaveformState.setFrozenHeights`.
            System.arraycopy(barHeights, 0, frozenHeights, 0, NUM_BARS)
        } else if (next == DictationState.IDLE) {
            targetAudioLevel = 0f
            smoothedAudioLevel = 0f
            barHeights.fill(0f)
        }
        state = next
        visibility = if (next == DictationState.IDLE) GONE else VISIBLE
        invalidate()
    }

    fun setAudioLevel(level: Float) {
        targetAudioLevel = level.coerceIn(0f, 1f)
        if (visibility == VISIBLE) invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(156), dp(46))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        housingPaint.shader =
            LinearGradient(
                0f,
                0f,
                0f,
                h.toFloat(),
                Color.parseColor("#19102C"),
                Color.parseColor("#07040E"),
                Shader.TileMode.CLAMP,
            )
        borderPaint.strokeWidth = dpF(1.6f)
        borderPaint.shader =
            LinearGradient(
                0f,
                0f,
                w.toFloat(),
                h.toFloat(),
                intArrayOf(
                    Color.parseColor("#FF1E9A"),
                    Color.parseColor("#6EE7F9"),
                    Color.parseColor("#FF1E9A"),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        barPaint.shader =
            LinearGradient(
                0f,
                h * 0.18f,
                0f,
                h * 0.82f,
                Color.parseColor("#7CF7FF"),
                Color.parseColor("#FF2BD6"),
                Shader.TileMode.CLAMP,
            )
        cancelCrossPaint.strokeWidth = dpF(2f)
        spinnerPaint.strokeWidth = dpF(2.4f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (state == DictationState.IDLE) return

        val h = height.toFloat()
        val w = width.toFloat()
        val cancelRadius = h * 0.38f
        val cancelCx = cancelRadius + dpF(3f)
        val cancelCy = h / 2f

        // 1. Left circular "✕" cancel button (matches BubblePOPOComposable's cancel icon).
        canvas.drawCircle(cancelCx, cancelCy, cancelRadius, cancelDiscPaint)
        canvas.drawCircle(cancelCx, cancelCy, cancelRadius, borderPaint)
        val crossHalf = cancelRadius * 0.34f
        canvas.drawLine(
            cancelCx - crossHalf,
            cancelCy - crossHalf,
            cancelCx + crossHalf,
            cancelCy + crossHalf,
            cancelCrossPaint,
        )
        canvas.drawLine(
            cancelCx + crossHalf,
            cancelCy - crossHalf,
            cancelCx - crossHalf,
            cancelCy + crossHalf,
            cancelCrossPaint,
        )

        // 2. Main equalizer pill container to the right of the cancel button.
        val pillLeft = cancelCx + cancelRadius + dpF(6f)
        val pillTop = dpF(3f)
        val pillBottom = h - dpF(3f)
        val pillHeight = pillBottom - pillTop
        val pillRadius = pillHeight / 2f
        pillRect.set(pillLeft, pillTop, w - dpF(3f), pillBottom)
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, housingPaint)
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, borderPaint)

        // 3. Smooth the incoming speech level (same 0.15 smoothing factor as Constants.java).
        smoothedAudioLevel += (targetAudioLevel - smoothedAudioLevel) * SMOOTHING_FACTOR
        if (smoothedAudioLevel < 0.01f) smoothedAudioLevel = 0f

        val barWidth = dpF(3.4f)
        val barGap = dpF(3.6f)
        val minBarH = barWidth // circular dot when silent
        val maxBarH = pillHeight * 0.68f
        val totalBarsWidth = NUM_BARS * barWidth + (NUM_BARS - 1) * barGap
        // Leave room on the right for the spinner during PROCESSING.
        val waveformAreaRight =
            if (state == DictationState.PROCESSING) pillRect.right - dpF(24f) else pillRect.right
        val startX = pillRect.left + ((waveformAreaRight - pillRect.left) - totalBarsWidth) / 2f

        barPaint.alpha = if (state == DictationState.PROCESSING) 85 else 255 // 33% when frozen

        val elapsedSec = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0
        for (i in 0 until NUM_BARS) {
            val barH =
                if (state == DictationState.PROCESSING) {
                    max(minBarH, frozenHeights[i])
                } else if (smoothedAudioLevel <= 0f) {
                    // Completely still when no speech arrives.
                    minBarH
                } else {
                    // Travelling wave modulated by the bell-curve envelope and speech level.
                    val wave = 0.68f + 0.32f * sin((elapsedSec * 7.5) + i * 0.65).toFloat()
                    val activeH = wave * BAR_SCALES[i] * maxBarH * smoothedAudioLevel
                    max(minBarH, activeH)
                }
            barHeights[i] = barH

            val left = startX + i * (barWidth + barGap)
            val top = (h - barH) / 2f
            barRect.set(left, top, left + barWidth, top + barH)
            canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, barPaint)
        }

        // 4. Spinning loader on the right side of the pill while transcribing/cleaning.
        if (state == DictationState.PROCESSING) {
            val r = dpF(7f)
            val cx = pillRect.right - dpF(14f)
            val cy = h / 2f
            spinnerRect.set(cx - r, cy - r, cx + r, cy + r)
            val sweepStart = ((elapsedSec * 360.0) % 360.0).toFloat()
            canvas.drawArc(spinnerRect, sweepStart, 250f, false, spinnerPaint)
        }

        // Keep redrawing at 60 fps only while bars are moving or the spinner is active.
        if (
            state == DictationState.PROCESSING || smoothedAudioLevel > 0f || targetAudioLevel > 0f
        ) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val cancelBoundary = height * 0.9f
            if (event.x <= cancelBoundary) {
                onCancelTapped()
            } else {
                onConfirmTapped()
            }
            performClick()
            return true
        }
        return true
    }

    private fun dp(value: Int): Int = dpF(value.toFloat()).toInt()

    private fun dpF(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val NUM_BARS = 10
        const val SMOOTHING_FACTOR = 0.22f
        // Exact symmetric envelope from Wispr Flow's `Constants.barScales`.
        val BAR_SCALES =
            floatArrayOf(0.25f, 0.47f, 0.69f, 0.875f, 1.0f, 1.0f, 0.875f, 0.69f, 0.47f, 0.25f)
    }
}
