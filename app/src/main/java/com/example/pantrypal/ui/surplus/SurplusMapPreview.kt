package com.example.pantrypal.ui.surplus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.pantrypal.R

/** Lightweight, tappable map-style preview for nearby redistribution hubs. */
class SurplusMapPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        contentDescription = "Map preview showing nearby surplus donation hubs"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx = width / 360f
        val sy = height / 150f
        canvas.save()
        canvas.scale(sx, sy)

        paint.color = 0xFFE7F0E9.toInt()
        canvas.drawRect(0f, 0f, 360f, 150f, paint)
        paint.color = 0xFFD8E9DC.toInt()
        canvas.drawRoundRect(RectF(12f, 14f, 104f, 62f), 18f, 18f, paint)
        canvas.drawRoundRect(RectF(232f, 18f, 346f, 70f), 20f, 20f, paint)
        canvas.drawRoundRect(RectF(176f, 98f, 350f, 140f), 17f, 17f, paint)

        paint.color = 0xFFFAFBF7.toInt()
        paint.strokeWidth = 9f
        paint.style = Paint.Style.STROKE
        val road1 = Path().apply { moveTo(-10f, 38f); cubicTo(92f, 58f, 132f, 12f, 218f, 48f); lineTo(374f, 30f) }
        val road2 = Path().apply { moveTo(56f, -10f); cubicTo(84f, 54f, 58f, 105f, 112f, 160f) }
        val road3 = Path().apply { moveTo(240f, -8f); cubicTo(202f, 44f, 269f, 88f, 218f, 160f) }
        canvas.drawPath(road1, paint)
        canvas.drawPath(road2, paint)
        canvas.drawPath(road3, paint)
        paint.style = Paint.Style.FILL

        drawHub(canvas, 92f, 72f, "Colombo Kitchen", sx, sy)
        drawHub(canvas, 265f, 104f, "Green Table", sx, sy)
        canvas.restore()
    }

    private fun drawHub(canvas: Canvas, x: Float, y: Float, label: String, sx: Float, sy: Float) {
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, y, 14f, paint)
        paint.color = ContextCompat.getColor(context, R.color.pantry_primary)
        canvas.drawCircle(x, y, 9f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 10f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("+", x, y + 3.5f, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textSize = 9f
        val labelWidth = paint.measureText(label) + 14f
        paint.color = 0xEFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(x - labelWidth / 2, y + 14f, x + labelWidth / 2, y + 29f), 7f, 7f, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textSize = 8f
        canvas.drawText(label, x, y + 24f, paint)
    }
}
