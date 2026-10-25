package dev.localvispr.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

// ==============================================================================
// Circular cyberpunk "V" bubble view
// ==============================================================================
//
// Renders a dark brushed-metal disc carrying a heavy chrome/cyan "V" with a
// top-right serif, framed by a glowing neon-pink tube (inspired by 80s/cyberpunk
// chrome lettering). Recording and processing states modulate the neon tube's
// colour and pulse intensity so the bubble stays a compact round icon without
// needing a text label.

class CyberpunkVBubbleView(context: Context) : View(context) {

    private var state: DictationState = DictationState.IDLE
    private var pulsePhase: Float = 0f

    private val pulseAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulsePhase = it.animatedValue as Float
                invalidate()
            }
        }

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rimInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val vBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val vBevelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.MITER
            strokeMiter = 6f
        }
    private val neonGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    private val neonTubePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    private val neonCorePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

    private val vPath = Path()
    private val bevelHighlightPath = Path()

    init {
        // Software layer is required on older/emulated GPUs so BlurMaskFilter
        // renders the outer neon halo reliably.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setDictationState(next: DictationState) {
        if (state == next) return
        state = next
        if (next == DictationState.IDLE) {
            pulseAnimator.cancel()
            pulsePhase = 0f
        } else if (!pulseAnimator.isRunning) {
            pulseAnimator.start()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp(64)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = min(w, h).toFloat()
        if (s <= 0f) return
        val cx = w / 2f
        val cy = h / 2f
        val radius = s * 0.44f

        // Deep obsidian/violet radial fill for the circular housing.
        discPaint.shader =
            RadialGradient(
                cx - radius * 0.25f,
                cy - radius * 0.30f,
                radius * 1.25f,
                intArrayOf(
                    Color.parseColor("#1D1230"),
                    Color.parseColor("#0B0714"),
                    Color.parseColor("#040208"),
                ),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP,
            )

        // Metallic gunmetal/chrome outer bezel.
        rimOuterPaint.strokeWidth = s * 0.045f
        rimOuterPaint.shader =
            LinearGradient(
                0f,
                0f,
                w.toFloat(),
                h.toFloat(),
                intArrayOf(
                    Color.parseColor("#6EE7F9"),
                    Color.parseColor("#2A2438"),
                    Color.parseColor("#9D174D"),
                    Color.parseColor("#1E1B2E"),
                ),
                floatArrayOf(0f, 0.4f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )

        // Metallic body of the V: bright chrome-pink in the top half with a tight
        // transition around the vertical midpoint into deep metallic purple below.
        val vTop = s * 0.27f
        val vBottom = s * 0.77f
        vBodyPaint.shader =
            LinearGradient(
                0f,
                vTop,
                0f,
                vBottom,
                intArrayOf(
                    Color.parseColor("#FF85D0"),
                    Color.parseColor("#D946EF"),
                    Color.parseColor("#6D28D9"),
                    Color.parseColor("#2E1065"),
                ),
                floatArrayOf(0f, 0.44f, 0.56f, 1f),
                Shader.TileMode.CLAMP,
            )

        // Inner metallic bevel along the V edges so it reads as solid steel/chrome.
        vBevelPaint.strokeWidth = s * 0.022f
        vBevelPaint.shader =
            LinearGradient(
                0f,
                vTop,
                0f,
                vBottom,
                intArrayOf(
                    Color.parseColor("#FFE4F7"),
                    Color.parseColor("#F0ABFC"),
                    Color.parseColor("#A855F7"),
                    Color.parseColor("#4C1D95"),
                ),
                floatArrayOf(0f, 0.44f, 0.56f, 1f),
                Shader.TileMode.CLAMP,
            )

        buildVGeometry(s)
    }

    // Constructs a bold, slightly asymmetric "V" whose right arm carries a
    // distinct top-right serif bar.
    private fun buildVGeometry(s: Float) {
        vPath.reset()
        // Top-left outer corner of left arm
        vPath.moveTo(s * 0.24f, s * 0.27f)
        // Top-left inner corner of left arm
        vPath.lineTo(s * 0.39f, s * 0.27f)
        // Inner valley of the V
        vPath.lineTo(s * 0.49f, s * 0.57f)
        // Inner edge rising up to the right arm
        vPath.lineTo(s * 0.60f, s * 0.27f)
        // Top-right serif extending to the right
        vPath.lineTo(s * 0.80f, s * 0.27f)
        // Bottom-right corner of the top-right serif slab
        vPath.lineTo(s * 0.78f, s * 0.36f)
        // Step-in from the serif into the outer right arm
        vPath.lineTo(s * 0.70f, s * 0.36f)
        // Bottom apex of the V (chiselled flat tip for stability)
        vPath.lineTo(s * 0.53f, s * 0.77f)
        vPath.lineTo(s * 0.44f, s * 0.77f)
        vPath.close()

        // Specular metallic ridge running down the left arm and across the serif.
        bevelHighlightPath.reset()
        bevelHighlightPath.moveTo(s * 0.30f, s * 0.31f)
        bevelHighlightPath.lineTo(s * 0.485f, s * 0.72f)
        bevelHighlightPath.lineTo(s * 0.65f, s * 0.31f)
        bevelHighlightPath.lineTo(s * 0.76f, s * 0.31f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = s * 0.44f
        val vTop = s * 0.27f
        val vBottom = s * 0.77f

        // Top half: hot neon pink; bottom half: deep electric purple, separated by
        // a tight transition band (0.43 .. 0.57) across the vertical middle of the V.
        val (topTube, bottomTube, rimAccent) =
            when (state) {
                DictationState.IDLE ->
                    Triple(
                        Color.parseColor("#FF1E9A"),
                        Color.parseColor("#8B2CF5"),
                        Color.parseColor("#CCFF1E9A"),
                    )
                DictationState.LISTENING ->
                    Triple(
                        Color.parseColor("#FF0066"),
                        Color.parseColor("#A855F7"),
                        Color.parseColor("#FFFF0066"),
                    )
                DictationState.PROCESSING ->
                    Triple(
                        Color.parseColor("#FFB800"),
                        Color.parseColor("#9333EA"),
                        Color.parseColor("#CCFFB800"),
                    )
            }
        val (topCore, bottomCore) =
            if (state == DictationState.PROCESSING) {
                Color.parseColor("#FFF7D6") to Color.parseColor("#E9D5FF")
            } else {
                Color.parseColor("#FFD6F5") to Color.parseColor("#E9D5FF")
            }

        val splits = floatArrayOf(0f, 0.43f, 0.57f, 1f)
        val tubeGradient =
            LinearGradient(
                0f,
                vTop,
                0f,
                vBottom,
                intArrayOf(topTube, topTube, bottomTube, bottomTube),
                splits,
                Shader.TileMode.CLAMP,
            )
        val coreGradient =
            LinearGradient(
                0f,
                vTop,
                0f,
                vBottom,
                intArrayOf(topCore, topCore, bottomCore, bottomCore),
                splits,
                Shader.TileMode.CLAMP,
            )

        // 1. Dark metallic circular base + gunmetal rim.
        canvas.drawCircle(cx, cy, radius, discPaint)
        canvas.drawCircle(cx, cy, radius, rimOuterPaint)

        // Subtle neon reflection ring inside the metal bezel during active states.
        val boost = if (state == DictationState.IDLE) 0f else pulsePhase
        rimInnerPaint.strokeWidth = s * (0.018f + 0.012f * boost)
        rimInnerPaint.color = rimAccent
        canvas.drawCircle(cx, cy, radius * 0.91f, rimInnerPaint)

        // 2. Solid metallic body of the V + specular chrome ridge.
        canvas.drawPath(vPath, vBodyPaint)
        canvas.drawPath(bevelHighlightPath, vBevelPaint)

        // 3. Two-tone neon tube wrapping the V (pink top -> short blend -> purple bottom).
        neonGlowPaint.shader = tubeGradient
        neonGlowPaint.strokeWidth = s * (0.085f + 0.030f * boost)
        neonGlowPaint.maskFilter =
            BlurMaskFilter(s * (0.065f + 0.025f * boost), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(vPath, neonGlowPaint)

        neonTubePaint.shader = tubeGradient
        neonTubePaint.strokeWidth = s * 0.036f
        canvas.drawPath(vPath, neonTubePaint)

        neonCorePaint.shader = coreGradient
        neonCorePaint.strokeWidth = s * 0.013f
        canvas.drawPath(vPath, neonCorePaint)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            )
            .toInt()
}
