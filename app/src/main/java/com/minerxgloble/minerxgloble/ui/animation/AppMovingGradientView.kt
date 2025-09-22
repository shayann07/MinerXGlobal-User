package com.minerxgloble.minerxgloble.ui.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import com.minerxgloble.minerxgloble.R
import kotlin.math.*

class AppMovingGradientView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: LinearGradient? = null
    private val localMatrix = Matrix()
    private var animator: ValueAnimator? = null

    // Colors: brighter + obvious gradient by default (Ocean vibe)
    @ColorInt private var colorStart: Int = Color.parseColor("#22D3EE") // cyan-400
    @ColorInt private var colorMid:   Int = Color.parseColor("#0EA5E9") // sky-500
    @ColorInt private var colorEnd:   Int = Color.parseColor("#6366F1") // indigo-500

    private var angleDeg: Float = 18f          // subtle diagonal motion
    private var durationMs: Long = 6000L       // sweep cycle
    private var mirrorRepeat: Boolean = false  // CLAMP by default to avoid “X” seams
    private var autoStart: Boolean = true

    // Optional subtle wobble to feel more “liquid”
    private var wobbleAmplitudeDeg: Float = 3f   // ±3°
    private var wobbleSpeedHz: Float = 0.12f     // 0.12 cycles/sec

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.AppMovingGradientView)
            colorStart = a.getColor(R.styleable.AppMovingGradientView_mg_colorStart, colorStart)
            colorMid   = a.getColor(R.styleable.AppMovingGradientView_mg_colorMid,   colorMid)
            colorEnd   = a.getColor(R.styleable.AppMovingGradientView_mg_colorEnd,   colorEnd)
            angleDeg   = a.getFloat(R.styleable.AppMovingGradientView_mg_angleDeg,   angleDeg)
            durationMs = a.getInt(R.styleable.AppMovingGradientView_mg_durationMs,   durationMs.toInt()).toLong()
            mirrorRepeat = a.getBoolean(R.styleable.AppMovingGradientView_mg_mirrorRepeat, mirrorRepeat)
            autoStart = a.getBoolean(R.styleable.AppMovingGradientView_mg_autoStart, autoStart)
            a.recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
        if (autoStart) start()
    }

    private fun rebuildShader() {
        if (width == 0 || height == 0) return

        val rad = Math.toRadians(angleDeg.toDouble())
        // Make the gradient longer than the diagonal so edges stay off-screen
        val span = (Math.hypot(width.toDouble(), height.toDouble()) * 1.6).toFloat()
        val dx = (cos(rad) * span).toFloat()
        val dy = (sin(rad) * span).toFloat()

        val x0 = -dx
        val y0 = -dy
        val x1 =  dx
        val y1 =  dy

        shader = LinearGradient(
            x0, y0, x1, y1,
            intArrayOf(colorStart, colorMid, colorEnd),
            floatArrayOf(0f, 0.5f, 1f),
            if (mirrorRepeat) Shader.TileMode.MIRROR else Shader.TileMode.CLAMP
        )
        paint.shader = shader
    }

    private fun ensureAnimator() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { va ->
                val frac = va.animatedFraction
                val tSec = frac * (durationMs / 1000f)

                // Translate along the gradient axis
                val maxShift = (max(width, height) * 0.6f)  // keep seams off-screen
                val rad = Math.toRadians(angleDeg.toDouble())
                val tx = (cos(rad) * maxShift * frac).toFloat()
                val ty = (sin(rad) * maxShift * frac).toFloat()

                // Optional gentle wobble rotation around center
                val wobbleDeg = sin((2 * Math.PI * wobbleSpeedHz * tSec)).toFloat() * wobbleAmplitudeDeg

                localMatrix.reset()
                localMatrix.setRotate(wobbleDeg, width / 2f, height / 2f)
                localMatrix.postTranslate(tx, ty)

                shader?.setLocalMatrix(localMatrix)
                invalidate()
            }
        }
    }

    fun start() {
        ensureAnimator()
        if (animator?.isStarted != true) animator?.start()
    }

    fun stop() {
        animator?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoStart) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    // ——— Public API ———
    fun setColors(@ColorInt start: Int, @ColorInt mid: Int, @ColorInt end: Int) {
        colorStart = start; colorMid = mid; colorEnd = end
        rebuildShader(); invalidate()
    }
    fun setAngle(degrees: Float) { angleDeg = degrees; rebuildShader(); invalidate() }
    fun setDuration(ms: Long) { durationMs = ms; animator?.duration = ms }
    fun setMirrorRepeat(enabled: Boolean) { mirrorRepeat = enabled; rebuildShader(); invalidate() }

    // Optional: tune the wobble
    fun setWobble(amplitudeDeg: Float, speedHz: Float) {
        wobbleAmplitudeDeg = amplitudeDeg
        wobbleSpeedHz = speedHz
    }
}
