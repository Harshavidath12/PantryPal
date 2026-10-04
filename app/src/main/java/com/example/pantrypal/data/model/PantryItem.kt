package com.example.pantrypal.data.model

data class PantryItem(
    val id: String,
    val title: String,
    val category: String,
    val expiryText: String,
    val ownerName: String,
    val imageUrl: String? = null,
    val progress: Float = 0.5f // 0.0 to 1.0
)
