package com.minerxgloble.minerxgloble.ui.animation

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RawRes
import com.minerxgloble.minerxgloble.R

class DropIntroOverlay(context: Context) : FrameLayout(context) {

    data class Config(
        val soundEnabled: Boolean = true,
        val hapticsEnabled: Boolean = true,
        val useVibratorApi: Boolean = false, // false = use performHapticFeedback (no permission)
        @RawRes val sfxDropRes: Int = R.raw.drop_woosh,
        @RawRes val sfxImpactRes: Int = R.raw.impact_thump,
        @RawRes val sfxDustRes: Int = R.raw.dust_sparkle,
        val volume: Float = 1.0f,
        val removeOnFinish: Boolean = true,
        val finishDelayMs: Long = 350L,
        // micro timing nudges
        val dropOffsetMs: Long = 10L,
        val impactOffsetMs: Long = 0L,
        // start guard: wait until required sounds load (or fallback)
        val maxLoadWaitMs: Long = 700L
    )

    private var soundPool: SoundPool? = null
    private var idDrop = 0
    private var idImpact = 0
    private var idDust = 0
    private var onFinished: (() -> Unit)? = null
    private var config: Config = Config()

    private var loadedCount = 0
    private var pendingStart = false
    private lateinit var requiredToStart: MutableSet<Int>

    private val dropView = DropIntroView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        animationListener = object : DropIntroView.AnimationListener {
            override fun onDropStart(index: Int) {
                // kept for backward-compat; not used
            }
            override fun onDropStartWithOrdinal(index: Int, ordinal: Int) {
                if (config.soundEnabled) {
                    // Use ORDINAL for SFX pitch so 3rd/4th sound “feel” right regardless of piece index
                    val rate = 0.95f + ordinal * 0.03f
                    postDelayed({ play(idDrop, rate = rate, vol = config.volume * 0.8f) }, config.dropOffsetMs)
                }
            }
            override fun onDropImpact(index: Int) {
                if (config.soundEnabled) {
                    postDelayed({ play(idImpact, vol = config.volume) }, config.impactOffsetMs)
                }
                if (config.hapticsEnabled) vibrateImpact()
            }
            override fun onSequenceFinished() {
                postDelayed({
                    onFinished?.invoke()
                    if (config.removeOnFinish) (parent as? ViewGroup)?.removeView(this@DropIntroOverlay)
                }, config.finishDelayMs.coerceAtLeast(0L))
            }
        }
    }

    init {
        isClickable = true
        isFocusable = true
        addView(dropView)
    }

    fun start(config: Config = Config(), onFinished: (() -> Unit)? = null) {
        this.config = config
        this.onFinished = onFinished

        pendingStart = true
        loadedCount = 0

        if (config.soundEnabled) {
            initSoundPool()
            // fallback so we never hang if onLoadComplete is slow
            postDelayed({ ensureAnimationStarted() }, config.maxLoadWaitMs)
        } else {
            dropView.begin()
            pendingStart = false
        }
    }

    fun cancel() {
        onFinished = null
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseSoundPool()
    }

    private fun ensureAnimationStarted() {
        if (!pendingStart) return
        pendingStart = false
        dropView.begin()
    }

    private fun initSoundPool() {
        if (soundPool != null) return

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(attrs)
            .build()
            .also { sp ->
                // only require drop + impact to sync start
                requiredToStart = mutableSetOf()
                sp.setOnLoadCompleteListener { _, sampleId, status ->
                    if (!pendingStart) return@setOnLoadCompleteListener
                    if (status == 0 && sampleId in requiredToStart) {
                        loadedCount++
                        if (loadedCount >= requiredToStart.size) {
                            ensureAnimationStarted()
                        }
                    }
                }

                idDrop   = sp.load(context, config.sfxDropRes, 1).also { requiredToStart += it }
                idImpact = sp.load(context, config.sfxImpactRes, 1).also { requiredToStart += it }
                idDust   = sp.load(context, config.sfxDustRes, 1) // optional
            }
    }

    private fun releaseSoundPool() {
        soundPool?.release()
        soundPool = null
    }

    private fun play(id: Int, rate: Float = 1f, vol: Float = 1f) {
        if (id == 0) return
        soundPool?.play(id, vol, vol, 1, 0, rate)
    }

    private fun vibrateImpact() {
        if (!config.useVibratorApi) {
            if (Build.VERSION.SDK_INT >= 30) {
                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else if (Build.VERSION.SDK_INT >= 23) {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            } else {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            return
        }

        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= 29) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }
    // In DropIntroOverlay.kt
    fun forceFinish() {
        // Forward to DropIntroView or do what your normal animation-end logic does
        dropView?.forceFinish()  // implement this in DropIntroView
    }

    companion object {
        fun play(parent: ViewGroup, config: Config = Config(), onFinished: (() -> Unit)? = null): DropIntroOverlay {
            val overlay = DropIntroOverlay(parent.context)
            overlay.layoutParams = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            parent.addView(overlay)
            overlay.start(config, onFinished)
            return overlay
        }
    }
}
