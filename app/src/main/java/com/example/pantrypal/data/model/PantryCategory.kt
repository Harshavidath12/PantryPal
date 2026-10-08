package com.example.pantrypal.data.model

import androidx.annotation.DrawableRes

data class PantryCategory(
    val id: String,
    val name: String,
    val count: Int,
    @DrawableRes val iconResId: Int
)
