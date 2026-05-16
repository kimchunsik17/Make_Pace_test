package com.example.makepacetestver.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class WaveLoadingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#81D4FA") // 밝은 바다색
        style = Paint.Style.FILL
    }
    
    private val wavePath = Path()
    private val circlePath = Path()
    private var progress = 0f
    private var waveOffset = 0f

    init {
        // clipPath를 위해 소프트웨어 렌더링 강제 (일부 기기 호환성)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setProgress(p: Float) {
        progress = p
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        circlePath.reset()
        val radius = w / 2f
        circlePath.addCircle(radius, radius, radius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        if (progress <= 0f) return

        canvas.save()
        canvas.clipPath(circlePath)

        val waveHeight = 30f
        val y = height * (1 - progress)
        
        wavePath.reset()
        wavePath.moveTo(-waveOffset, y)
        
        var x = -waveOffset
        val step = 10f
        while (x < width + waveOffset + step) {
            val waveY = y + sin((x + waveOffset) / width * 2 * Math.PI).toFloat() * waveHeight
            wavePath.lineTo(x, waveY)
            x += step
        }
        
        wavePath.lineTo(width.toFloat(), height.toFloat())
        wavePath.lineTo(0f, height.toFloat())
        wavePath.close()
        
        canvas.drawPath(wavePath, wavePaint)
        canvas.restore()

        waveOffset += 15f
        if (waveOffset > width) waveOffset = 0f
        
        if (progress > 0 && progress < 1) {
            postInvalidateOnAnimation()
        }
    }
}
