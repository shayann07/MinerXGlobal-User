package com.minerxgloble.minerxgloble.ui.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser
import kotlin.math.min

/**
 * XLogoLoadingView
 * - Animates your 4-piece logo "X" by FILLING it from bottom → top.
 * - Indeterminate by default (loops).
 * - Determinate supported via setProgress(0..1) or playOnceThenHold().
 * - Auto-starts on attach / when made visible; stops on detach / when hidden.
 */
class XLogoLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Timing/behavior */
    var indeterminate: Boolean = true
    var durationMs: Long = 1200L

    /** Visuals */
    var backgroundDim: Int = Color.TRANSPARENT       // optional dim behind the X
    var trackAlpha: Int = 40                          // faint track (unfilled) alpha (0..255)
    var fillAlpha: Int = 255                          // filled part alpha (0..255)

    private val viewportW = 969f
    private val viewportH = 970f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = trackAlpha
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundDim
    }

    private val paths = ArrayList<Path>(4)
    private val shaders = ArrayList<Shader>(4)
    private val contentBounds = RectF()

    private var animator: ValueAnimator? = null
    private var progress = 0f // 0..1 (for indeterminate we loop this)

    init {
        // === SAME 4 PIECES AS YOUR INTRO "X" ===
        val rawPaths = listOf(
            // (index order matches your latest DropIntroView)
            "M452.18 557.72l62.83 74.76-316.44 254.98 253.61-329.74Z", // 0
            "M968.41 0L409.68 402.26l131.7 48.15-43.57 54.35 71.31 85.07 181.61-145.84-128.41-44.48L968.41 0Z", // 1
            "M171.52 214.23h45.86l177.93 209.13H238.47L0 137.19h263.24l181.6 215.54-28.43 22.93-172.44-205.45-167.84-0.92L260.48 388.5l56.87 0.92-145.83-175.19Z", // 2
            "M599.67 587.3l28.55-22.93 60.83 74.53-147.29-1.38-114.19-133.3-77.35-0.76 38.83 45.86L74.87 935.04H282.5l212.48-261.29 226.4 263.01L888 937.44 707.67 721.62l-54.17-0.06 138.04 172.01-47.39-0.3-172.28-207.14h153.17L962.6 970H700.28L495.29 733.83 305.66 970H0.92l337.83-421.6-68.48-80.71 177.02 2.14 114.95 138.8 56.41 1.37-18.98-22.7Z" // 3
        )
        for (d in rawPaths) {
            val p = PathParser.createPathFromPathData(d)!!
            val b = RectF()
            p.computeBounds(b, true)
            if (contentBounds.isEmpty) contentBounds.set(b) else contentBounds.union(b)
            paths += p
        }

        // Gradients (same vibe as intro)
        shaders += LinearGradient(
            561f, 441f, 816f, 186f,
            intArrayOf(Color.parseColor("#84A5FF"), Color.parseColor("#99D0FF"), Color.parseColor("#ACF5FF")),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        shaders += LinearGradient(
            198f, 722f, 515f, 722f,
            intArrayOf(Color.parseColor("#BC43FD"), Color.parseColor("#86AAFF")),
            null,
            Shader.TileMode.CLAMP
        )
        shaders += LinearGradient(
            0f, 280f, 444f, 280f,
            intArrayOf(Color.parseColor("#83A5FF"), Color.parseColor("#86AAFF")),
            null,
            Shader.TileMode.CLAMP
        )
        shaders += LinearGradient(
            296f, 1128f, 1199f, -27f,
            intArrayOf(Color.parseColor("#BC43FD"), Color.parseColor("#86AAFF")),
            null,
            Shader.TileMode.CLAMP
        )
    }

    // ---- Lifecycle auto start/stop ----
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) start()
    }
    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) start() else stop()
    }

    // ---- Controls ----
    fun start() {
        if (animator?.isRunning == true) return
        if (indeterminate) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    progress = va.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            invalidate()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    /** Determinate mode. Also stops looping. Call again as progress updates (0..1). */
    fun setProgress(p: Float) {
        indeterminate = false
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    /** One-shot fill then hold full. */
    fun playOnceThenHold() {
        indeterminate = false
        stop()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---- Render ----
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (backgroundDim != Color.TRANSPARENT) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        // Fit the 969x970 artbox into view
        val scale = min(width / viewportW, height / viewportH)
        val dx = (width - viewportW * scale) / 2f
        val dy = (height - viewportH * scale) / 2f
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)

        // Optional "track" (unfilled faint shape)
        trackPaint.alpha = trackAlpha
        for (i in 0 until paths.size) {
            canvas.drawPath(paths[i], trackPaint)
        }

        // Clip to a rect rising from the *logo’s* bottom to top by progress
        val topFill = contentBounds.bottom - contentBounds.height() * progress
        val left = contentBounds.left
        val right = contentBounds.right
        val bottom = contentBounds.bottom

        canvas.save()
        canvas.clipRect(left, topFill, right, bottom)

        // Draw filled part with original gradients
        paint.alpha = fillAlpha
        for (i in 0 until paths.size) {
            paint.shader = shaders[i % shaders.size]
            canvas.drawPath(paths[i], paint)
        }
        paint.shader = null

        canvas.restore()
    }
}
