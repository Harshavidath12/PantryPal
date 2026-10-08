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

/** Monthly chart populated from the public community donation history. */
class SurplusImpactChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var months = emptyList<String>()
    private var pendingKg = emptyList<Float>()
    private var completedKg = emptyList<Float>()
    private var footer = "Donation activity will appear here"
    private val density = resources.displayMetrics.density

    fun setDonations(donations: List<DonationHistoryDto>) {
        val keyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val labelFormat = SimpleDateFormat("MMM", Locale.US)
        val keys = mutableListOf<String>()
        val labels = mutableListOf<String>()
        val month = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(5) {
            keys.add(0, keyFormat.format(month.time))
            labels.add(0, labelFormat.format(month.time))
            month.add(Calendar.MONTH, -1)
        }

        val pending = MutableList(5) { 0f }
        val completed = MutableList(5) { 0f }
        donations.forEach { donation ->
            val monthKey = donation.createdAt?.take(7) ?: return@forEach
            val index = keys.indexOf(monthKey)
            if (index < 0) return@forEach
            val kg = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(?:kg|kgs)\\b", RegexOption.IGNORE_CASE)
                .find(donation.quantity)?.groupValues?.get(1)?.toFloatOrNull() ?: return@forEach
            when (donation.status.uppercase()) {
                "PENDING", "CLAIMED" -> pending[index] += kg
                "PICKED_UP", "DROPPED_OFF" -> completed[index] += kg
            }
        }
        months = labels
        pendingKg = pending
        completedKg = completed
        val completedTotal = completed.sum()
        footer = "${donations.size} donations  ·  ${"%.1f".format(completedTotal)} kg completed"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthPx = width.toFloat()
        val heightPx = height.toFloat()
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, widthPx, heightPx), 18f * density, 18f * density, paint)

        paint.textSize = 11f * density
        paint.textAlign = Paint.Align.LEFT
        paint.color = 0xFF9B6B13.toInt()
        canvas.drawCircle(18f * density, 18f * density, 3f * density, paint)
        paint.color = 0xFF33423D.toInt()
        canvas.drawText("Pending kg", 25f * density, 21f * density, paint)
        paint.color = 0xFF176853.toInt()
        canvas.drawCircle(94f * density, 18f * density, 3f * density, paint)
        paint.color = 0xFF33423D.toInt()
        canvas.drawText("Completed kg", 101f * density, 21f * density, paint)

        val bottom = heightPx - 45f * density
        val chartTop = 38f * density
        val chartHeight = bottom - chartTop
        val count = months.size
        if (count > 0) {
            val maxValue = (pendingKg + completedKg).maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val groupWidth = widthPx / count
            for (index in 0 until count) {
                val center = groupWidth * (index + .5f)
                val firstHeight = chartHeight * pendingKg[index] / maxValue
                val secondHeight = chartHeight * completedKg[index] / maxValue
                paint.color = 0xFF9B6B13.toInt()
                if (firstHeight > 0f) canvas.drawRoundRect(RectF(center - 12f * density, bottom - firstHeight, center - 2f * density, bottom), 5f * density, 5f * density, paint)
                paint.color = 0xFF176853.toInt()
                if (secondHeight > 0f) canvas.drawRoundRect(RectF(center + 2f * density, bottom - secondHeight, center + 12f * density, bottom), 5f * density, 5f * density, paint)
                paint.color = 0xFF49534E.toInt()
                paint.textSize = 11f * density
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(months[index], center, bottom + 14f * density, paint)
            }
        }

        paint.color = 0xFFE5F3EC.toInt()
        canvas.drawRoundRect(RectF(10f * density, heightPx - 27f * density, widthPx - 10f * density, heightPx - 5f * density), 11f * density, 11f * density, paint)
        paint.color = 0xFF285E50.toInt()
        paint.textSize = 10f * density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(footer, widthPx / 2f, heightPx - 12f * density, paint)
    }
}
