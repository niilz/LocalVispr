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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.max
import kotlin.math.sin

// ==============================================================================
// Companion equalizer pill modelled after Wispr Flow's `BubblePOPOComposable`
// and `WaveformKt`
// ==============================================================================
//
// Appears next to the circular Cyberpunk-V bubble only while a dictation is
// active (`LISTENING` or `PROCESSING`):
// - Starts as `GONE` so the idle overlay window is only as wide as the 64 dp V
//   bubble and can be dragged freely across the full screen width.
// - Mirrors its layout automatically when the bubble is docked on the right
//   screen edge (`dockedRight == true` -> `[✕] [Equalizer] [V]`;
//   `dockedRight == false` -> `[V] [Equalizer] [✕]`).
// - Animates in with scale + alpha from the V bubble instead of popping in.

class WaveformPillView(
    context: Context,
    private val onCancelTapped: () -> Unit,
    private val onConfirmTapped: () -> Unit,
) : View(context) {

    private var state: DictationState = DictationState.IDLE
    private var dockedRight: Boolean = false
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

    init {
        // Must start GONE so the idle overlay window is 64 dp wide rather than 228 dp.
        visibility = GONE
        alpha = 0f
        scaleX = 0.5f
        scaleY = 0.5f
    }

    fun setDockedRight(isDockedRight: Boolean) {
        if (dockedRight != isDockedRight) {
            dockedRight = isDockedRight
            pivotX = if (dockedRight) width.toFloat() else 0f
            invalidate()
        }
    }

    fun setDictationState(next: DictationState) {
        val previous = state
        if (next == DictationState.PROCESSING) {
            System.arraycopy(barHeights, 0, frozenHeights, 0, NUM_BARS)
        } else if (next == DictationState.IDLE) {
            targetAudioLevel = 0f
            smoothedAudioLevel = 0f
            barHeights.fill(0f)
        }
        state = next

        if (next == DictationState.IDLE) {
            if (visibility != GONE) {
                animate().cancel().let {
                    animate()
                        .alpha(0f)
                        .scaleX(0.55f)
                        .scaleY(0.55f)
                        .setDuration(140L)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            if (state == DictationState.IDLE) {
                                visibility = GONE
                            }
                        }
                        .start()
                }
            }
        } else {
            val wasHidden = visibility != VISIBLE || previous == DictationState.IDLE
            visibility = VISIBLE
            if (wasHidden) {
                pivotX = if (dockedRight) width.toFloat().coerceAtLeast(dpF(156f)) else 0f
                pivotY = height.toFloat().coerceAtLeast(dpF(46f)) / 2f
                alpha = 0f
                scaleX = 0.55f
                scaleY = 0.55f
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(210L)
                    .setInterpolator(OvershootInterpolator(1.25f))
                    .start()
            }
            invalidate()
        }
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
        pivotX = if (dockedRight) w.toFloat() else 0f
        pivotY = h / 2f

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
                    Color.parseColor("#8B2CF5"),
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
                Color.parseColor("#FF1E9A"),
                Color.parseColor("#8B2CF5"),
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
        val cancelCy = h / 2f

        // Place the "✕" button on the outer end (away from the V bubble):
        // - Left-docked:  [V] [Equalizer] [✕]  (cancel on the right)
        // - Right-docked: [✕] [Equalizer] [V]  (cancel on the left)
        val cancelCx: Float
        val pillLeft: Float
        val pillRight: Float
        if (dockedRight) {
            cancelCx = cancelRadius + dpF(3f)
            pillLeft = cancelCx + cancelRadius + dpF(6f)
            pillRight = w - dpF(3f)
        } else {
            cancelCx = w - cancelRadius - dpF(3f)
            pillLeft = dpF(3f)
            pillRight = cancelCx - cancelRadius - dpF(6f)
        }

        val pillTop = dpF(3f)
        val pillBottom = h - dpF(3f)
        val pillHeight = pillBottom - pillTop
        val pillRadius = pillHeight / 2f
        pillRect.set(pillLeft, pillTop, pillRight, pillBottom)
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, housingPaint)
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, borderPaint)

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

        smoothedAudioLevel += (targetAudioLevel - smoothedAudioLevel) * SMOOTHING_FACTOR
        if (smoothedAudioLevel < 0.01f) smoothedAudioLevel = 0f

        val barWidth = dpF(3.4f)
        val barGap = dpF(3.6f)
        val minBarH = barWidth
        val maxBarH = pillHeight * 0.68f
        val totalBarsWidth = NUM_BARS * barWidth + (NUM_BARS - 1) * barGap
        val waveformAreaRight =
            if (state == DictationState.PROCESSING) pillRect.right - dpF(24f) else pillRect.right
        val startX = pillRect.left + ((waveformAreaRight - pillRect.left) - totalBarsWidth) / 2f

        barPaint.alpha = if (state == DictationState.PROCESSING) 85 else 255

        val elapsedSec = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0
        for (i in 0 until NUM_BARS) {
            val barH =
                if (state == DictationState.PROCESSING) {
                    max(minBarH, frozenHeights[i])
                } else if (smoothedAudioLevel <= 0f) {
                    minBarH
                } else {
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

        if (state == DictationState.PROCESSING) {
            val r = dpF(7f)
            val cx = pillRect.right - dpF(14f)
            val cy = h / 2f
            spinnerRect.set(cx - r, cy - r, cx + r, cy + r)
            val sweepStart = ((elapsedSec * 360.0) % 360.0).toFloat()
            canvas.drawArc(spinnerRect, sweepStart, 250f, false, spinnerPaint)
        }

        if (
            state == DictationState.PROCESSING || smoothedAudioLevel > 0f || targetAudioLevel > 0f
        ) {
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val isCancelHit =
                if (dockedRight) {
                    event.x <= height * 0.9f
                } else {
                    event.x >= width - height * 0.9f
                }
            if (isCancelHit) {
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
        val BAR_SCALES =
            floatArrayOf(0.25f, 0.47f, 0.69f, 0.875f, 1.0f, 1.0f, 0.875f, 0.69f, 0.47f, 0.25f)
    }
}
