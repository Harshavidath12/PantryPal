package com.example.pantrypal.ui.surplus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.pantrypal.data.repository.DonationHistoryDto
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Compact paired monthly bars styled for the community impact screen. */
class SurplusImpactChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var months = emptyList<String>()
    private var inProgressKg = emptyList<Float>()
    private var rescuedKg = emptyList<Float>()
    private var footer = "Your donations will appear here"
    private val density = resources.displayMetrics.density

    fun setDonations(donations: List<DonationHistoryDto>) {
        val monthKeyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthLabelFormat = SimpleDateFormat("MMM", Locale.US)
        val monthKeys = mutableListOf<String>()
        val labels = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        repeat(5) {
            monthKeys.add(0, monthKeyFormat.format(calendar.time))
            labels.add(0, monthLabelFormat.format(calendar.time))
            calendar.add(Calendar.MONTH, -1)
        }
        val activeTotals = MutableList(5) { 0f }
        val rescuedTotals = MutableList(5) { 0f }
        donations.forEach { donation ->
            val key = donation.createdAt?.take(7) ?: return@forEach
            val index = monthKeys.indexOf(key)
            if (index < 0) return@forEach
            val kg = Regex("[0-9]+(?:\\.[0-9]+)?").find(donation.quantity)?.value?.toFloatOrNull() ?: 0f
            when (donation.status.uppercase()) {
                "PICKED_UP" -> rescuedTotals[index] += kg
                "PENDING", "CLAIMED" -> activeTotals[index] += kg
            }
        }
        months = if (donations.isEmpty()) emptyList() else labels
        inProgressKg = activeTotals
        rescuedKg = rescuedTotals
        footer = "${donations.size} donations  ·  ${"%.1f".format(rescuedTotals.sum())} kg rescued"
        invalidate()
    }

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
        canvas.drawText("In progress", 25f * density, 21f * density, paint)
        paint.color = 0xFF176853.toInt()
        canvas.drawCircle(82f * density, 18f * density, 3f * density, paint)
        paint.color = 0xFF33423D.toInt()
        canvas.drawText("Picked up", 89f * density, 21f * density, paint)

        val bottom = heightPx - 45f * density
        val chartTop = 38f * density
        val chartHeight = bottom - chartTop
        if (months.isEmpty()) {
            paint.color = 0xFF718078.toInt()
            paint.textSize = 11f * density
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("No donations in the last five months", widthPx / 2f, (chartTop + bottom) / 2f, paint)
        } else {
            val groupWidth = widthPx / months.size
            val maxValue = (inProgressKg + rescuedKg).maxOrNull()?.coerceAtLeast(1f) ?: 1f
            for (index in months.indices) {
                val center = groupWidth * (index + .5f)
                val firstHeight = chartHeight * inProgressKg[index] / maxValue
                val secondHeight = chartHeight * rescuedKg[index] / maxValue
                paint.color = 0xFF9B6B13.toInt()
                if (firstHeight > 0f) canvas.drawRoundRect(RectF(center - 12f * density, bottom - firstHeight, center - 2f * density, bottom), 5f * density, 5f * density, paint)
                paint.color = 0xFF176853.toInt()
                if (secondHeight > 0f) canvas.drawRoundRect(RectF(center + 2f * density, bottom - secondHeight, center + 12f * density, bottom), 5f * density, 5f * density, paint)
                paint.color = 0xFF49534E.toInt()
                paint.textSize = 9f * density
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(months[index], center, bottom + 14f * density, paint)
            }
        }

        paint.color = 0xFFE5F3EC.toInt()
        canvas.drawRoundRect(RectF(10f * density, heightPx - 27f * density, widthPx - 10f * density, heightPx - 5f * density), 11f * density, 11f * density, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textSize = 8f * density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(footer, widthPx / 2f, heightPx - 12f * density, paint)
    }
}
