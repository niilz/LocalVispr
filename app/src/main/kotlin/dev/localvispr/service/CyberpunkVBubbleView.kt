package dev.localvispr.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

// ==============================================================================
// Circular cyberpunk "V" bubble view (3D italic + speed trails)
// ==============================================================================
//
// Renders a dark brushed-metal disc carrying an italic-skewed, 3D-extruded
// metallic "V" with a top-right serif, a slightly diagonal neon-pink-to-purple
// gradient (with a tight midpoint transition), and high-speed motion trails
// fading out to the left.

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
    private val vExtrusionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val vExtrusionEdgePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
    private val vBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val vBevelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.MITER
            strokeMiter = 6f
        }
    private val speedTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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
    private val vExtrusionPath = Path()
    private val bevelHighlightPath = Path()
    private val trailRect = RectF()
    private val skewMatrix = Matrix()

    init {
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

        // 3D side-wall extrusion shader (dark metallic violet-indigo with cyan rim).
        vExtrusionPaint.shader =
            LinearGradient(
                s * 0.25f,
                s * 0.25f,
                s * 0.75f,
                s * 0.80f,
                intArrayOf(
                    Color.parseColor("#4C1D95"),
                    Color.parseColor("#1E1038"),
                    Color.parseColor("#0F0720"),
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        vExtrusionEdgePaint.strokeWidth = s * 0.016f
        vExtrusionEdgePaint.color = Color.parseColor("#9938BDF8")

        // Slightly diagonal gradient (top-left -> bottom-right) with a tight
        // transition around the middle from neon pink (top) to purple (bottom).
        val gx0 = s * 0.34f
        val gy0 = s * 0.26f
        val gx1 = s * 0.66f
        val gy1 = s * 0.78f
        vBodyPaint.shader =
            LinearGradient(
                gx0,
                gy0,
                gx1,
                gy1,
                intArrayOf(
                    Color.parseColor("#FF94D8"),
                    Color.parseColor("#EC4899"),
                    Color.parseColor("#7C3AED"),
                    Color.parseColor("#2E1065"),
                ),
                floatArrayOf(0f, 0.43f, 0.57f, 1f),
                Shader.TileMode.CLAMP,
            )

        vBevelPaint.strokeWidth = s * 0.022f
        vBevelPaint.shader =
            LinearGradient(
                gx0,
                gy0,
                gx1,
                gy1,
                intArrayOf(
                    Color.parseColor("#FFE8F9"),
                    Color.parseColor("#F472B6"),
                    Color.parseColor("#A855F7"),
                    Color.parseColor("#4C1D95"),
                ),
                floatArrayOf(0f, 0.43f, 0.57f, 1f),
                Shader.TileMode.CLAMP,
            )

        buildItalic3DVGeometry(s)
    }

    private fun buildItalic3DVGeometry(s: Float) {
        vPath.reset()
        // Base V with top-right serif before italic forward skew.
        vPath.moveTo(s * 0.28f, s * 0.27f)
        vPath.lineTo(s * 0.43f, s * 0.27f)
        vPath.lineTo(s * 0.51f, s * 0.57f)
        vPath.lineTo(s * 0.62f, s * 0.27f)
        vPath.lineTo(s * 0.82f, s * 0.27f)
        vPath.lineTo(s * 0.80f, s * 0.36f)
        vPath.lineTo(s * 0.72f, s * 0.36f)
        vPath.lineTo(s * 0.55f, s * 0.76f)
        vPath.lineTo(s * 0.45f, s * 0.76f)
        vPath.close()

        bevelHighlightPath.reset()
        bevelHighlightPath.moveTo(s * 0.34f, s * 0.31f)
        bevelHighlightPath.lineTo(s * 0.50f, s * 0.71f)
        bevelHighlightPath.lineTo(s * 0.66f, s * 0.31f)
        bevelHighlightPath.lineTo(s * 0.77f, s * 0.31f)

        // Apply forward italic shear (-0.19x) and mild perspective compression around the centre.
        skewMatrix.reset()
        skewMatrix.setSkew(-0.19f, -0.03f, s * 0.52f, s * 0.52f)
        vPath.transform(skewMatrix)
        bevelHighlightPath.transform(skewMatrix)

        // 3D depth extrusion shifted down-right behind the front face.
        vExtrusionPath.set(vPath)
        vExtrusionPath.offset(s * 0.042f, s * 0.038f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = min(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = s * 0.44f

        val (topTube, bottomTube, rimAccent) =
            when (state) {
                DictationState.IDLE ->
                    Triple(
                        Color.parseColor("#FF1E9A"), // Hot neon pink top
                        Color.parseColor("#8B2CF5"), // Electric purple bottom
                        Color.parseColor("#CCFF1E9A"),
                    )
                DictationState.LISTENING ->
                    Triple(
                        Color.parseColor(
                            "#FFE600"
                        ), // Brilliant cyberpunk neon-yellow top during recording!
                        Color.parseColor("#FF0099"), // Saturated neon-magenta/purple bottom
                        Color.parseColor("#FFFFE600"),
                    )
                DictationState.PROCESSING ->
                    Triple(
                        Color.parseColor("#FF9900"), // Warm amber-gold top
                        Color.parseColor("#7E22CE"), // Deep violet bottom
                        Color.parseColor("#CCFF9900"),
                    )
            }
        val (topCore, bottomCore) =
            when (state) {
                DictationState.IDLE -> Color.parseColor("#FFD6F5") to Color.parseColor("#E9D5FF")
                DictationState.LISTENING ->
                    Color.parseColor("#FFFFF0") to
                        Color.parseColor("#FFD6F5") // Bright white-yellow core
                DictationState.PROCESSING ->
                    Color.parseColor("#FFF7D6") to Color.parseColor("#E9D5FF")
            }

        // Slightly diagonal axis (~28 degrees) with tight midpoint transition (0.43 .. 0.57).
        val gx0 = s * 0.34f
        val gy0 = s * 0.26f
        val gx1 = s * 0.66f
        val gy1 = s * 0.78f
        val splits = floatArrayOf(0f, 0.43f, 0.57f, 1f)

        // Dynamically update the metallic V body shader to match state colors
        val (bodyTop1, bodyTop2, bodyBottom1, bodyBottom2) =
            when (state) {
                DictationState.IDLE ->
                    listOf(
                        Color.parseColor("#FF94D8"),
                        Color.parseColor("#EC4899"),
                        Color.parseColor("#7C3AED"),
                        Color.parseColor("#2E1065"),
                    )
                DictationState.LISTENING ->
                    listOf(
                        Color.parseColor("#FFF066"), // Metallic gold-yellow top
                        Color.parseColor("#F59E0B"),
                        Color.parseColor("#EC4899"),
                        Color.parseColor("#4C1D95"),
                    )
                DictationState.PROCESSING ->
                    listOf(
                        Color.parseColor("#FCD34D"),
                        Color.parseColor("#D97706"),
                        Color.parseColor("#6D28D9"),
                        Color.parseColor("#2E1065"),
                    )
            }
        vBodyPaint.shader =
            LinearGradient(
                gx0,
                gy0,
                gx1,
                gy1,
                intArrayOf(bodyTop1, bodyTop2, bodyBottom1, bodyBottom2),
                splits,
                Shader.TileMode.CLAMP,
            )
        val tubeGradient =
            LinearGradient(
                gx0,
                gy0,
                gx1,
                gy1,
                intArrayOf(topTube, topTube, bottomTube, bottomTube),
                splits,
                Shader.TileMode.CLAMP,
            )
        val coreGradient =
            LinearGradient(
                gx0,
                gy0,
                gx1,
                gy1,
                intArrayOf(topCore, topCore, bottomCore, bottomCore),
                splits,
                Shader.TileMode.CLAMP,
            )

        // 1. Dark metallic circular base + gunmetal rim.
        canvas.drawCircle(cx, cy, radius, discPaint)
        canvas.drawCircle(cx, cy, radius, rimOuterPaint)

        val boost = if (state == DictationState.IDLE) 0f else pulsePhase
        rimInnerPaint.strokeWidth = s * (0.018f + 0.012f * boost)
        rimInnerPaint.color = rimAccent
        canvas.drawCircle(cx, cy, radius * 0.91f, rimInnerPaint)

        // 2. High-speed motion streaks on the left, fading out to transparency towards the left.
        drawSpeedStreak(
            canvas,
            left = s * 0.09f,
            right = s * 0.33f,
            centerY = s * 0.34f,
            thickness = s * 0.026f,
            solidColor = topTube,
        )
        drawSpeedStreak(
            canvas,
            left = s * 0.12f,
            right = s * 0.37f,
            centerY = s * 0.48f,
            thickness = s * 0.020f,
            solidColor = Color.parseColor("#E879F9"),
        )
        drawSpeedStreak(
            canvas,
            left = s * 0.16f,
            right = s * 0.41f,
            centerY = s * 0.62f,
            thickness = s * 0.024f,
            solidColor = bottomTube,
        )

        // 3. 3D extruded block behind the V.
        canvas.drawPath(vExtrusionPath, vExtrusionPaint)
        canvas.drawPath(vExtrusionPath, vExtrusionEdgePaint)

        // 4. Front metallic V face + specular ridge.
        canvas.drawPath(vPath, vBodyPaint)
        canvas.drawPath(bevelHighlightPath, vBevelPaint)

        // 5. Diagonal two-tone neon tube wrapping the V.
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

    private fun drawSpeedStreak(
        canvas: Canvas,
        left: Float,
        right: Float,
        centerY: Float,
        thickness: Float,
        solidColor: Int,
    ) {
        val half = thickness / 2f
        trailRect.set(left, centerY - half, right, centerY + half)
        val transparent = solidColor and 0x00FFFFFF
        speedTrailPaint.shader =
            LinearGradient(
                left,
                centerY,
                right,
                centerY,
                intArrayOf(transparent, solidColor, Color.WHITE),
                floatArrayOf(0f, 0.78f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(trailRect, half, half, speedTrailPaint)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            )
            .toInt()
}
