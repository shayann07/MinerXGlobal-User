package com.minerxgloble.minerxgloble.ui.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import com.google.android.material.card.MaterialCardView
import kotlin.math.max
import kotlin.math.min

class LiquidGradientCard @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    // Global tone (lower = darker)
    private val DIM_FACTOR = 0.86f

    // Gold palette (no blacks)
    private val goldEdge = Color.parseColor("#AF9350")
    private val goldSoft = Color.parseColor("#BEA45A")
    private val goldMain = Color.parseColor("#CFB56A")
    private val goldLite = Color.parseColor("#E3C980")

    // Pre-33
    private val paintGold = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath  = Path()
    private val rect      = RectF()
    private val mx        = Matrix()
    private var shaderGold: LinearGradient? = null

    // 33+
    private var runtimeShader: RuntimeShader? = null
    private val paintAGSL = Paint(Paint.ANTI_ALIAS_FLAG)

    // Animation t ∈ [0,1] (ping-pong handled by animator itself)
    private var t = 0f
    private var animator: ValueAnimator? = null

    init {
        radius = dp(20f)
        cardElevation = dp(10f)
        useCompatPadding = true
        preventCornerOverlap = true

        strokeWidth = dp(1f).toInt()
        strokeColor = Color.parseColor("#E6CC8A")

        if (Build.VERSION.SDK_INT >= 33) {
            // No wrap, no breathing. Band moves up (0→1) then down (1→0) via REVERSE.
            val src = """
                uniform float2 iResolution;
                uniform float iPhase;   // 0..1, ping-pong supplied from Java/Kotlin
                uniform float iDim;     // 0..1 global darken

                float3 mix3(float3 a, float3 b, float3 c, float t) {
                    float3 ab = mix(a, b, smoothstep(0.0, 0.5, t));
                    float3 bc = mix(b, c, smoothstep(0.5, 1.0, t));
                    return mix(ab, bc, smoothstep(0.25, 0.75, t));
                }

                float gauss(float x, float w) {
                    float s = x / w;
                    return exp(-s*s);
                }

                half4 main(float2 fragCoord) {
                    float2 uv = fragCoord / iResolution;
                    float y = uv.y;

                    // Darker gold ramp
                    float3 c1 = float3(0.686, 0.576, 0.314); // #AF9350
                    float3 c2 = float3(0.745, 0.643, 0.353); // #BEA45A
                    float3 c3 = float3(0.812, 0.710, 0.416); // #CFB56A
                    float3 c4 = float3(0.890, 0.788, 0.502); // #E3C980

                    float3 baseRamp = mix3(c1, c2, c3, y);
                    baseRamp = mix(baseRamp, c4, smoothstep(0.60, 1.00, y));

                    // Band center moves strictly within [0.25..0.75] to keep it away from edges
                    float center = mix(0.25, 0.75, iPhase);
                    float width  = 0.48;        // wide = subtle
                    float band   = gauss(y - center, width);

                    // Very gentle lift; no breathing, no wrap
                    float3 col = mix(baseRamp, c4, 0.05 * band);

                    // Global dim
                    col = clamp(col * iDim, 0.0, 1.0);

                    return half4(half3(col), 1.0);
                }
            """.trimIndent()
            runtimeShader = RuntimeShader(src)
            paintAGSL.shader = runtimeShader
        }

        startAnim()
    }

    private fun startAnim() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            // 4s up, 4s down => 8s full loop with REVERSE (no jump)
            duration = 4000L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                // Clamp away from exact 0/1 to avoid any precision edge case
                t = max(0.0001f, min(0.9999f, animatedValue as Float))
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rect.set(0f, 0f, w.toFloat(), h.toFloat())

        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        if (Build.VERSION.SDK_INT >= 33) {
            runtimeShader?.setFloatUniform("iResolution", w.toFloat(), h.toFloat())
            runtimeShader?.setFloatUniform("iDim", DIM_FACTOR)
        } else {
            // Tall gold ramp; we only ping-pong within a safe window (no wrap).
            val h3 = h * 3f
            val colors = intArrayOf(
                darken(goldEdge), darken(goldSoft), darken(goldMain), darken(goldLite),
                darken(goldMain), darken(goldSoft), darken(goldEdge)
            )
            val pos = floatArrayOf(0f, 0.18f, 0.38f, 0.50f, 0.62f, 0.82f, 1f)
            shaderGold = LinearGradient(
                0f, h3, 0f, 0f,
                colors, pos,
                Shader.TileMode.CLAMP
            )
            paintGold.shader = shaderGold
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clipPath)

        if (Build.VERSION.SDK_INT >= 33 && runtimeShader != null) {
            runtimeShader?.setFloatUniform("iPhase", t)
            canvas.drawRoundRect(rect, radius, radius, paintAGSL)
        } else {
            // Pre-33: ping-pong translation between two safe offsets, no wrap.
            val h = rect.height()

            // Travel only within [-0.5h, +0.5h] window inside the 3h gradient,
            // so endpoints never show and there’s no reset jump.
            val travel = h * 1.0f
            val minOff = -0.5f * h
            val maxOff = +0.5f * h
            val offset = minOff + t * (maxOff - minOff)

            mx.reset()
            // Center within the 3h gradient so we always have headroom
            mx.postTranslate(0f, offset - h) // -h centers within [0..3h]
            shaderGold?.setLocalMatrix(mx)

            canvas.drawRoundRect(rect, radius, radius, paintGold)
        }

        canvas.restore()
        super.onDraw(canvas)
    }

    private fun darken(color: Int): Int {
        val r = (Color.red(color)   * DIM_FACTOR).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * DIM_FACTOR).toInt().coerceIn(0, 255)
        val b = (Color.blue(color)  * DIM_FACTOR).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
