package com.example.pantrypal.data.repository

import com.example.pantrypal.data.model.PantryItemDto
import com.example.pantrypal.data.model.SharedUpdateDto
import com.example.pantrypal.data.model.ShoppingItemDto
import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi

class NotificationRepository {

    @OptIn(InternalSerializationApi::class)
    private val postgrest = SupabaseProvider.client.from("pantry_items")

    // 1. READ: Fetch all active alerts from Supabase
    @OptIn(InternalSerializationApi::class)
    suspend fun getActiveAlerts(): List<PantryItemDto> = withContext(Dispatchers.IO) {
        postgrest.select {
            filter {
                eq("status", "ACTIVE")
            }
        }.decodeList<PantryItemDto>()
    }

    // 2. UPDATE: Button "[Mark Consumed]" updates status to 'CONSUMED'
    @OptIn(InternalSerializationApi::class)
    suspend fun markItemAsConsumed(itemId: String, itemTitle: String) = withContext(Dispatchers.IO) {
        // Update pantry item
        postgrest.update({
            set("status", "CONSUMED")
        }) {
            filter { eq("id", itemId) }
        }

        // Add to shared household activity log
        SupabaseProvider.client.from("shared_updates").insert(
            SharedUpdateDto(
                actorInitials = "VD",
                actionText = "Vidath consumed $itemTitle",
                detailsText = "Updated from Notifications"
            )
        )
    }

    // 3. CREATE: Button "[Add to Restock]" adds row to shopping_items table
    @OptIn(InternalSerializationApi::class)
    suspend fun addToRestockList(itemName: String, category: String) = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("shopping_items").insert(
            ShoppingItemDto(
                itemName = itemName,
                category = category
            )
        )

        // Log this event to shared updates table
        SupabaseProvider.client.from("shared_updates").insert(
            SharedUpdateDto(
                actorInitials = "VD",
                actionText = "Added $itemName to shopping list",
                detailsText = "Marked for restock"
            )
        )
    }
}
