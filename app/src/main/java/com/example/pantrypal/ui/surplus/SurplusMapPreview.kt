package com.example.pantrypal.ui.surplus

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.pantrypal.R

/** A lightweight illustrated neighborhood map preview for nearby donation hubs. */
class SurplusMapPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        contentDescription = "Map preview showing three nearby surplus donation hubs in Colombo"
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sx = width / 360f
        val sy = height / 150f
        canvas.save()
        canvas.scale(sx, sy)

        // Base land and neighborhood blocks.
        paint.style = Paint.Style.FILL
        paint.color = 0xFFF0F5EF.toInt()
        canvas.drawRect(0f, 0f, 360f, 150f, paint)
        paint.color = 0xFFDDEBE0.toInt()
        listOf(
            RectF(8f, 12f, 96f, 48f), RectF(122f, 8f, 208f, 32f),
            RectF(272f, 12f, 350f, 44f), RectF(18f, 108f, 118f, 144f),
            RectF(192f, 98f, 348f, 140f), RectF(117f, 53f, 156f, 78f)
        ).forEach { canvas.drawRoundRect(it, 15f, 15f, paint) }

        // Park areas provide visual landmarks.
        paint.color = 0xFFCFE4D4.toInt()
        canvas.drawRoundRect(RectF(260f, 57f, 348f, 88f), 14f, 14f, paint)
        paint.color = 0xFFBFD9C8.toInt()
        canvas.drawCircle(279f, 70f, 4f, paint)
        canvas.drawCircle(293f, 77f, 3f, paint)
        canvas.drawCircle(306f, 67f, 4f, paint)
        paint.color = 0xFF668A76.toInt()
        paint.textSize = 6.5f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Vihara Park", 314f, 73f, paint)

        // Secondary streets: subtle casing under the warm road surface.
        val sideRoads = listOf(
            Path().apply { moveTo(-8f, 76f); cubicTo(85f, 66f, 128f, 94f, 201f, 78f); cubicTo(260f, 65f, 304f, 99f, 370f, 88f) },
            Path().apply { moveTo(116f, -8f); cubicTo(139f, 34f, 92f, 84f, 142f, 158f) },
            Path().apply { moveTo(225f, -10f); cubicTo(202f, 37f, 264f, 92f, 232f, 160f) },
            Path().apply { moveTo(20f, 98f); cubicTo(72f, 91f, 111f, 108f, 157f, 102f) }
        )
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = 0xFFD4DDD4.toInt()
        paint.strokeWidth = 9f
        sideRoads.forEach { canvas.drawPath(it, paint) }
        paint.color = 0xFFFFFEFA.toInt()
        paint.strokeWidth = 6f
        sideRoads.forEach { canvas.drawPath(it, paint) }

        // Main avenue with a soft shadow edge and center markings.
        val avenue = Path().apply {
            moveTo(-12f, 35f); cubicTo(74f, 47f, 117f, 19f, 191f, 42f)
            cubicTo(252f, 61f, 292f, 30f, 370f, 42f)
        }
        paint.color = 0xFFD0D9D1.toInt()
        paint.strokeWidth = 14f
        canvas.drawPath(avenue, paint)
        paint.color = 0xFFFFFEFA.toInt()
        paint.strokeWidth = 10f
        canvas.drawPath(avenue, paint)
        paint.color = 0xFFE5DCC6.toInt()
        paint.strokeWidth = 0.9f
        paint.pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
        canvas.drawPath(avenue, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL

        // Small street labels.
        paint.color = 0xFF728278.toInt()
        paint.textSize = 6.5f
        paint.textAlign = Paint.Align.CENTER
        canvas.save(); canvas.rotate(-8f, 188f, 60f)
        canvas.drawText("Community Road", 188f, 60f, paint)
        canvas.restore()

        // Map header chip and compass.
        paint.color = 0xEFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(9f, 8f, 132f, 25f), 9f, 9f, paint)
        paint.color = ContextCompat.getColor(context, R.color.pantry_primary)
        canvas.drawCircle(19f, 16.5f, 3f, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 7.5f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        canvas.drawText("3 ACTIVE HUBS  ·  COLOMBO", 27f, 19f, paint)

        paint.color = 0xEFFFFFFF.toInt()
        canvas.drawCircle(339f, 22f, 13f, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 8f
        canvas.drawText("N", 339f, 16f, paint)
        val north = Path().apply { moveTo(339f, 18f); lineTo(334f, 28f); lineTo(339f, 25f); lineTo(344f, 28f); close() }
        paint.color = ContextCompat.getColor(context, R.color.pantry_primary)
        canvas.drawPath(north, paint)

        // Route highlight and pins for the seeded hubs.
        val route = Path().apply { moveTo(88f, 62f); cubicTo(132f, 66f, 179f, 76f, 246f, 103f); lineTo(291f, 82f) }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x993D8B75.toInt()
        paint.pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        canvas.drawPath(route, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL

        drawHub(canvas, 88f, 62f, "Colombo Kitchen", true)
        drawHub(canvas, 246f, 103f, "Green Table", false)
        drawHub(canvas, 291f, 82f, "Maple Street", false)

        // Scale indicator.
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawRoundRect(RectF(9f, 127f, 63f, 143f), 7f, 7f, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 7f
        canvas.drawText("—  1 km", 17f, 138f, paint)

        canvas.restore()
    }

    private fun drawHub(canvas: Canvas, x: Float, y: Float, label: String, featured: Boolean) {
        // Pin halo and shadow.
        paint.color = 0x33406E5D
        canvas.drawCircle(x, y + 1f, 12f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, y, 11f, paint)
        paint.color = if (featured) ContextCompat.getColor(context, R.color.pantry_primary) else 0xFF4A8771.toInt()
        canvas.drawCircle(x, y, 8f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 10f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText("+", x, y + 3.6f, paint)

        val labelWidth = paint.measureText(label) + 13f
        paint.color = 0xF7FFFFFF.toInt()
        canvas.drawRoundRect(RectF(x - labelWidth / 2, y + 11f, x + labelWidth / 2, y + 25f), 7f, 7f, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textSize = 7.1f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        canvas.drawText(label, x, y + 20.5f, paint)
    }
}
