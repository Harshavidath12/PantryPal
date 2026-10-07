package com.example.pantrypal.ui.surplus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Paired monthly bars matching the Community Impact reference design. */
class SurplusImpactChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May")
    private val expired = listOf(64f, 57f, 46f, 29f, 15f)
    private val consumed = listOf(42f, 58f, 81f, 102f, 122f)
    private val density = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthPx = width.toFloat()
        val heightPx = height.toFloat()
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, widthPx, heightPx), 18f * density, 18f * density, paint)

        paint.textSize = 9f * density
        paint.textAlign = Paint.Align.LEFT
        paint.color = 0xFF9B6B13.toInt()
        canvas.drawCircle(18f * density, 18f * density, 3f * density, paint)
        paint.color = 0xFF33423D.toInt()
        canvas.drawText("Expired", 25f * density, 21f * density, paint)
        paint.color = 0xFF176853.toInt()
        canvas.drawCircle(82f * density, 18f * density, 3f * density, paint)
        paint.color = 0xFF33423D.toInt()
        canvas.drawText("Consumed", 89f * density, 21f * density, paint)

        val bottom = heightPx - 45f * density
        val chartTop = 38f * density
        val chartHeight = bottom - chartTop
        val groupWidth = widthPx / months.size
        for (index in months.indices) {
            val center = groupWidth * (index + .5f)
            val firstHeight = chartHeight * expired[index] / 140f
            val secondHeight = chartHeight * consumed[index] / 140f
            paint.color = 0xFF9B6B13.toInt()
            canvas.drawRoundRect(RectF(center - 12f * density, bottom - firstHeight, center - 2f * density, bottom), 5f * density, 5f * density, paint)
            paint.color = 0xFF176853.toInt()
            canvas.drawRoundRect(RectF(center + 2f * density, bottom - secondHeight, center + 12f * density, bottom), 5f * density, 5f * density, paint)
            paint.color = 0xFF49534E.toInt()
            paint.textSize = 9f * density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(months[index], center, bottom + 14f * density, paint)
        }

        paint.color = 0xFFE5F3EC.toInt()
        canvas.drawRoundRect(RectF(10f * density, heightPx - 27f * density, widthPx - 10f * density, heightPx - 5f * density), 11f * density, 11f * density, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textSize = 8f * density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("↘ 68% waste since Jan     ·     Optimal cadence", widthPx / 2f, heightPx - 12f * density, paint)
    }
}
