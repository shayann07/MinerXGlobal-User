package com.minerxgloble.minerxgloble.ui.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.PathParser
import kotlin.math.max
import kotlin.math.min

class DropIntroView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    interface AnimationListener {
        fun onDropStart(index: Int) {}
        // new: also expose ordinal (0..3) so overlay can sync SFX to play-order
        fun onDropStartWithOrdinal(index: Int, ordinal: Int) {}
        fun onDropImpact(index: Int) {}
        fun onDustStarted(index: Int) {}
        fun onSequenceFinished() {}
    }
    var animationListener: AnimationListener? = null

    /* ── Config ───────────────────────────────────────────── */
    var contentScale: Float = 0.33f
    var taglineTopMarginVp: Float = 300f
    var minerColor: Int = Color.WHITE
    var titleFadeDurationMs: Long = 850L

    /* Gradient for “X” */
    private val xGradientColors = intArrayOf(
        Color.parseColor("#84A5FF"),
        Color.parseColor("#99D0FF"),
        Color.parseColor("#ACF5FF")
    )
    private val xGradientPos = floatArrayOf(0f, 0.6f, 1f)

    /* ── Internals ───────────────────────────────────────── */
    private val dustEnabled = false
    private var started = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = minerColor
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isSubpixelText = true
    }
    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        alpha = 150
        strokeJoin = Paint.Join.ROUND
        strokeMiter = 10f
    }

    private val paths = mutableListOf<Path>()
    private val shaders = mutableListOf<Shader>()
    private val pathOffsets = mutableListOf<PointF>()
    private val dustAlphas = mutableListOf<Int>()
    private val fillAlphas = mutableListOf<Int>()

    private val viewportW = 969f
    private val viewportH = 970f
    private var finishedBursts = 0
    private val contentBounds = RectF()

    // Title state (sequential fade: Miner then X)
    private val titleText = "Miner X"
    private var showMinerX = false
    private var minerAlpha = 0
    private var xAlpha = 0
    private var fittedTextSizeVp = 0f

    // store the order so we can pass ordinal to listeners
    private val order = intArrayOf(3, 2, 1, 0) // bottom, top, top-right, bottom-left

    init {
        val rawPaths = listOf(
            // 0: long “flash”
            "M452.18 557.72l62.83 74.76-316.44 254.98 253.61-329.74Z",

            // 1: diamond
            "M968.41 0L409.68 402.26l131.7 48.15-43.57 54.35 71.31 85.07 181.61-145.84-128.41-44.48L968.41 0Z",
            // 2: upper-left piece of X
            "M171.52 214.23h45.86l177.93 209.13H238.47L0 137.19h263.24l181.6 215.54-28.43 22.93-172.44-205.45-167.84-0.92L260.48 388.5l56.87 0.92-145.83-175.19Z",
            // 3: bottom tip
            "M599.67 587.3l28.55-22.93 60.83 74.53-147.29-1.38-114.19-133.3-77.35-0.76 38.83 45.86L74.87 935.04H282.5l212.48-261.29 226.4 263.01L888 937.44 707.67 721.62l-54.17-0.06 138.04 172.01-47.39-0.3-172.28-207.14h153.17L962.6 970H700.28L495.29 733.83 305.66 970H0.92l337.83-421.6-68.48-80.71 177.02 2.14 114.95 138.8 56.41 1.37-18.98-22.7Z"
        )

        rawPaths.forEach { data ->
            val p = PathParser.createPathFromPathData(data)!!
            paths.add(p)
            pathOffsets.add(PointF(0f, 0f))
            dustAlphas.add(0)
            fillAlphas.add(0)
            val b = RectF()
            p.computeBounds(b, true)
            if (contentBounds.isEmpty) contentBounds.set(b) else contentBounds.union(b)
        }

        // gradients
        shaders.add(
            LinearGradient(561f, 441f, 816f, 186f,
                intArrayOf(Color.parseColor("#84A5FF"), Color.parseColor("#99D0FF"), Color.parseColor("#ACF5FF")),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        )
        shaders.add(
            LinearGradient(198f, 722f, 515f, 722f,
                intArrayOf(Color.parseColor("#BC43FD"), Color.parseColor("#86AAFF")), null, Shader.TileMode.CLAMP)
        )
        shaders.add(
            LinearGradient(0f, 280f, 444f, 280f,
                intArrayOf(Color.parseColor("#83A5FF"), Color.parseColor("#86AAFF")), null, Shader.TileMode.CLAMP)
        )
        shaders.add(
            LinearGradient(296f, 1128f, 1199f, -27f,
                intArrayOf(Color.parseColor("#BC43FD"), Color.parseColor("#86AAFF")), null, Shader.TileMode.CLAMP)
        )
        // overlay will call begin() after SFX are ready
    }

    /** Call once to actually start the animation (Overlay triggers this after SFX are ready). */
    fun begin() {
        if (started) return
        started = true
        startSequence()
    }

    /** Sequence & directions (do not change order):
     *  3 (bottom tip)  -> from bottom
     *  2 (upper-left)  -> from top
     *  1 (diamond)     -> from top-right
     *  0 (flash)       -> from bottom-left
     */
    private fun startSequence() {
        val delays = longArrayOf(0L, 500L, 1000L, 1500L)

        for (ordinal in order.indices) {
            val idx = order[ordinal]
            val from = when (idx) {
                3 -> PointF(0f, 1500f)        // up from bottom
                2 -> PointF(0f, -1500f)       // down from top
                1 -> PointF(1500f, -1500f)    // from top-right
                0 -> PointF(-1500f, 1500f)    // from bottom-left
                else -> PointF(0f, -1500f)
            }
            animateDrop(idx, from, delays[ordinal], ordinal)
        }
    }

    private fun animateDrop(index: Int, from: PointF, delay: Long, ordinal: Int) {
        pathOffsets[index] = PointF(from.x, from.y)

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            startDelay = delay
            interpolator = DecelerateInterpolator()

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    animationListener?.onDropStart(index) // legacy
                    animationListener?.onDropStartWithOrdinal(index, ordinal) // precise ordinal
                }
                override fun onAnimationEnd(animation: Animator) {
                    animationListener?.onDropImpact(index)
                    if (dustEnabled) {
                        dustBurst(index)
                    } else {
                        finishedBursts++
                        if (finishedBursts == paths.size) {
                            fittedTextSizeVp = computeFittedTextSizeVp()
                            startMinerThenX()
                        }
                    }
                }
            })

            addUpdateListener {
                val f = it.animatedFraction
                pathOffsets[index].x = from.x * (1f - f)
                pathOffsets[index].y = from.y * (1f - f)
                fillAlphas[index] = (255 * f).toInt()
                invalidate()
            }
        }
        anim.start()
    }

    /** Size "Miner X" so its width equals the union width of the logo paths. */
    private fun computeFittedTextSizeVp(): Float {
        val desiredWidth = contentBounds.width()
        if (desiredWidth <= 0f) return 0f
        val p = Paint(textPaint)
        p.textSize = 1f
        val measuredAt1 = p.measureText(titleText)
        if (measuredAt1 <= 0f) return 0f
        return desiredWidth / measuredAt1
    }

    /** Fade in "Miner" first, then "X". */
    private fun startMinerThenX() {
        showMinerX = true
        minerAlpha = 0
        xAlpha = 0

        val minerAnim = ValueAnimator.ofInt(0, 255).apply {
            duration = titleFadeDurationMs
            addUpdateListener {
                minerAlpha = it.animatedValue as Int
                invalidate()
            }
            doOnEnd {
                val xAnim = ValueAnimator.ofInt(0, 255).apply {
                    duration = titleFadeDurationMs
                    addUpdateListener { va ->
                        xAlpha = va.animatedValue as Int
                        invalidate()
                    }
                    doOnEnd { animationListener?.onSequenceFinished() }
                }
                xAnim.start()
            }
        }
        minerAnim.start()
    }

    // kept for completeness; not used when dust is off
    private fun dustBurst(index: Int) {
        val anim = ValueAnimator.ofInt(180, 0).apply {
            duration = 600L
            addUpdateListener {
                if (it.animatedFraction == 0f) animationListener?.onDustStarted(index)
                dustAlphas[index] = it.animatedValue as Int
                invalidate()
            }
            doOnEnd {
                dustAlphas[index] = 0
                finishedBursts++
                if (finishedBursts == paths.size) {
                    fittedTextSizeVp = computeFittedTextSizeVp()
                    startMinerThenX()
                }
            }
        }
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val fitScale = min(width / viewportW, height / viewportH)
        val scale = fitScale * contentScale
        val dx = (width - viewportW * scale) / 2f
        val dy = (height - viewportH * scale) / 2f

        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        // Logo pieces
        for (i in paths.indices) {
            val save = canvas.save()
            canvas.translate(pathOffsets[i].x, pathOffsets[i].y)
            paint.shader = shaders[i % shaders.size]
            paint.alpha = fillAlphas[i]
            canvas.drawPath(paths[i], paint)
            canvas.restoreToCount(save)
        }

        // Text: "Miner" then "X", width-matched to logo span
        if (showMinerX) {
            val textSize = if (fittedTextSizeVp > 0f) fittedTextSizeVp else 80f
            textPaint.textSize = textSize
            textStrokePaint.textSize = textSize
            textStrokePaint.strokeWidth = max(1f, textSize * 0.045f)

            val margin = taglineTopMarginVp * contentScale
            val baseline = contentBounds.bottom + margin - textPaint.ascent()

            val miner = "Miner "
            val xChar = "X"

            // Measure widths
            val minerWidth = textPaint.measureText(miner)
            val xWidth = textPaint.measureText(xChar)
            val totalWidth = minerWidth + xWidth
            val startX = (viewportW - totalWidth) / 2f

            // ---- MINER (stroke then fill) ----
            textStrokePaint.alpha = (minerAlpha * 0.8f).toInt()
            canvas.drawText(miner, startX, baseline, textStrokePaint)

            textPaint.shader = null
            textPaint.color = minerColor
            textPaint.alpha = minerAlpha
            canvas.drawText(miner, startX, baseline, textPaint)

            // ---- X (align bottom with "Miner") ----
            val minerBounds = Rect()
            textPaint.getTextBounds(miner, 0, miner.length, minerBounds)
            val xBounds = Rect()
            textPaint.getTextBounds(xChar, 0, 1, xBounds)
            val xBaselineShift = (minerBounds.bottom - xBounds.bottom).toFloat()
            val baselineX = baseline + xBaselineShift

            val xStart = startX + minerWidth
            val yTop = baselineX + textPaint.ascent()
            val yBottom = baselineX + textPaint.descent()

            textStrokePaint.alpha = (xAlpha * 0.8f).toInt()
            canvas.drawText(xChar, xStart, baselineX, textStrokePaint)

            val xShader = LinearGradient(
                xStart, yTop,
                xStart + xWidth, yBottom,
                xGradientColors, xGradientPos, Shader.TileMode.CLAMP
            )
            textPaint.shader = xShader
            textPaint.alpha = xAlpha
            canvas.drawText(xChar, xStart, baselineX, textPaint)

            // reset
            textPaint.shader = null
            textPaint.alpha = 255
        }
    }

    // In DropIntroView.kt
    fun forceFinish() {
        // End all animators, make all pieces visible
        for (i in fillAlphas.indices) fillAlphas[i] = 255
        showMinerX = true
        minerAlpha = 255
        xAlpha = 255
        invalidate()
        animationListener?.onSequenceFinished()
    }

}
