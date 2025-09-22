package com.minerxgloble.minerxgloble.ui.animation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import com.google.android.material.card.MaterialCardView
import kotlin.math.hypot
import kotlin.math.sin

class LiquidGradientCard @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val deepGray  = Color.parseColor("#121212")
    private val goldSoft  = Color.parseColor("#C8A95A")
    private val goldMain  = Color.parseColor("#D9BF72")
    private val goldLite  = Color.parseColor("#EAD088")

    // Paints & geometry
    private val paintBand = Paint(Paint.ANTI_ALIAS_FLAG)     // linear band
    private val paintVign = Paint(Paint.ANTI_ALIAS_FLAG)     // radial vignette overlay
    private val clipPath  = Path()
    private val rect      = RectF()
    private val localMatrix = Matrix()

    // Shaders
    private var linear: LinearGradient? = null
    private var vignette: RadialGradient? = null

    // AGSL (API 33+)
    private var runtimeShader: RuntimeShader? = null
    private val paintAGSL = Paint(Paint.ANTI_ALIAS_FLAG)

    private var time = 0f
    private var animator: ValueAnimator? = null

    init {
        radius = dp(20f)
        cardElevation = dp(10f)
        useCompatPadding = true
        preventCornerOverlap = true

        // optional outline (remove in XML with app:strokeWidth="0dp" if you don't want it)
        strokeWidth = dp(1f).toInt()
        strokeColor = Color.parseColor("#E6CC8A")

        if (Build.VERSION.SDK_INT >= 33) {
            // Stronger liquid: triple-layer sine warp + pulsing band width + stronger highlight
            val src = """
                uniform float2 iResolution;
                uniform float iTime;

                float smoothBand(float g, float c, float w) {
                    float x = (g - c) / max(w, 1e-3);
                    return exp(-x*x*1.5);
                }

                half4 main(float2 fragCoord) {
                    float2 uv = fragCoord / iResolution;
                    float g = dot(uv, normalize(float2(1.0, 1.0)));

                    // ----- Liquid wobble (more prominent) -----
                    float t = iTime;
                    // 3 layered fields with different scales/speeds
                    float w1 = 0.030 * sin(uv.x * 10.0 + t * 2.0);
                    float w2 = 0.024 * sin(uv.y * 14.0 - t * 1.6);
                    float w3 = 0.018 * sin((uv.x * 18.0 + uv.y * 7.0) + t * 1.2);
                    g += (w1 + w2 + w3);

                    // Slight band "breathing"
                    float pulse = 0.04 * sin(t * 0.8);
                    g = clamp(g, 0.0, 1.0);

                    // Colors
                    float3 cBase   = float3(0.07, 0.07, 0.07);
                    float3 cGoldS  = float3(0.784, 0.663, 0.353);
                    float3 cGoldM  = float3(0.851, 0.749, 0.447);
                    float3 cGoldL  = float3(0.918, 0.816, 0.533);

                    // Wider band + secondary shoulder (wider due to 'pulse')
                    float b1 = smoothBand(g, 0.50, 0.34 + pulse);
                    float b2 = smoothBand(g, 0.50, 0.56 + pulse) * 0.40;

                    // Blend sequence
                    float3 col = cBase;
                    col = mix(col, cGoldS, 0.95 * b2);
                    col = mix(col, cGoldM, 1.00 * b1);
                    col = mix(col, cGoldL, 0.60 * smoothstep(0.0, 1.0, b1));

                    // Stronger/ wider moving highlight line
                    float sweep = g - (0.50 + 0.05 * sin(t * 0.7));
                    float spec  = 1.0 - smoothstep(0.00, 0.28, abs(sweep));
                    col += spec * 0.14;

                    // Corner vignette (unchanged)
                    float2 c1 = float2(0.0,0.0), c2 = float2(1.0,0.0), c3 = float2(0.0,1.0), c4 = float2(1.0,1.0);
                    float dc = min(min(length(uv-c1), length(uv-c2)), min(length(uv-c3), length(uv-c4)));
                    float vig = smoothstep(0.95, 0.60, dc);
                    col = mix(col * 0.82, col, 1.0 - vig * 0.65);

                    // Final lift
                    col = clamp(col * 1.04 + 0.02, 0.0, 1.0);
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
            duration = 5200L            // was 3800L → a bit slower
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                time = animatedFraction
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
        } else {
            // PRE-33
            linear = LinearGradient(
                0f, h.toFloat(), w.toFloat(), 0f, // BL -> TR
                intArrayOf(deepGray, goldSoft, goldMain, goldLite, goldMain, goldSoft, deepGray),
                floatArrayOf(0f, 0.22f, 0.40f, 0.50f, 0.60f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
            paintBand.shader = linear

            val r = (hypot(w.toDouble(), h.toDouble()) * 0.72).toFloat()
            vignette = RadialGradient(
                w / 2f, h / 2f, r,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(150, 0, 0, 0)),
                floatArrayOf(0f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
            paintVign.shader = vignette
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clipPath)

        if (Build.VERSION.SDK_INT >= 33 && runtimeShader != null) {
            runtimeShader?.setFloatUniform("iTime", time * 6.28318f)
            canvas.drawRoundRect(rect, radius, radius, paintAGSL)
        } else {
            // PRE-33: stronger motion — drift + subtle skew ripple
            linear?.let {
                localMatrix.reset()
                val omega = time * 6.28318f * 0.6f
                val drift = 10f * sin(omega)           // ↑ from 4f
                val skewX = 0.06f * sin(omega * 1.2f)  // add wavy feel
                val skewY = 0.05f * sin(omega * 0.9f)

                // translate a bit along diagonal
                localMatrix.postTranslate(drift, -drift)
                // skew around center (approx)
                val cx = rect.centerX()
                val cy = rect.centerY()
                localMatrix.postTranslate(-cx, -cy)
                localMatrix.postSkew(skewX, skewY)
                localMatrix.postTranslate(cx, cy)

                it.setLocalMatrix(localMatrix)
            }
            canvas.drawRoundRect(rect, radius, radius, paintBand)
            canvas.drawRoundRect(rect, radius, radius, paintVign)
        }

        canvas.restore()
        super.onDraw(canvas)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
