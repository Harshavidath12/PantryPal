package com.example.pantrypal.data.model

data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: String,
    val statusText: String,
    val statusType: ShoppingStatusType,
    val secondaryText: String = "",
    val isSelected: Boolean = false,
    val isBought: Boolean = false
)

enum class ShoppingStatusType {
    EXPIRED,
    LOW_STOCK,
    MANUAL,
    PANTRY_EMPTY
}
