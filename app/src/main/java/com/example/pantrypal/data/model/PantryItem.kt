package com.example.pantrypal.data.model

import androidx.annotation.DrawableRes

data class PantryItem(
    val id: String,
    val title: String,
    val category: String,
    val location: String = "Pantry",
    val quantityText: String = "1",
    val expiryText: String,
    val daysUntilExpiry: Int = 30,
    val ownerName: String = "Taylor",
    val imageUrl: String? = null,
    @DrawableRes val iconResId: Int? = null,
    val stockPercent: Int = 50,
    val stockText: String = "50%",
    val isExpiringSoon: Boolean = false,
    val progress: Float = 0.5f
)
