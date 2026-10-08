@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.example.pantrypal.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PantryItemDto(
    val id: String? = null,
    val title: String,
    @SerialName("category_zone") val categoryZone: String,
    @SerialName("expiry_text") val expiryText: String,
    val urgency: String,
    val status: String = "ACTIVE"
)

@Serializable
data class ShoppingItemDto(
    val id: String? = null,
    @SerialName("item_name") val itemName: String,
    val category: String,
    @SerialName("is_bought") val isBought: Boolean = false
)

@Serializable
data class SharedUpdateDto(
    val id: String? = null,
    @SerialName("actor_initials") val actorInitials: String,
    @SerialName("action_text") val actionText: String,
    @SerialName("details_text") val detailsText: String? = null
)

@Serializable
data class NotificationDto(
    val id: Long? = null,
    @SerialName("user_id") val userId: Long,
    val message: String,
    @SerialName("reference_id") val referenceId: String? = null,
    val type: String,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@InternalSerializationApi
@Serializable
data class SurplusHubDto(
    val id: String,
    val name: String,
    val address: String,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("open_until") val openUntil: String,
    @SerialName("accepted_foods") val acceptedFoods: String
)

@InternalSerializationApi
@Serializable
data class SurplusDonationDto(
    val id: String? = null,
    @SerialName("donor_id") val donorId: String? = null,
    @SerialName("hub_id") val hubId: String,
    @SerialName("food_name") val foodName: String,
    val quantity: String,
    @SerialName("best_before") val bestBefore: String,
    @SerialName("pickup_window") val pickupWindow: String,
    val status: String = "PENDING",
    @SerialName("created_at") val createdAt: String? = null
)
