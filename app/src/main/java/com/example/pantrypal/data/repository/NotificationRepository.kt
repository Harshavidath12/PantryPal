package com.example.pantrypal.data.repository

import com.example.pantrypal.data.model.NotificationDto
import com.example.pantrypal.data.model.PantryItemDto
import com.example.pantrypal.data.model.SharedUpdateDto
import com.example.pantrypal.data.model.ShoppingItemDto
import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository {

    private val pantryPostgrest = SupabaseProvider.client.from("pantry_items")
    
    private val notificationPostgrest = SupabaseProvider.client.from("notifications")

    // 1. READ: Fetch all active alerts from Supabase (from 'notifications' table instead of 'pantry_items')
    suspend fun getActiveAlerts(userId: Long): List<NotificationDto> = withContext(Dispatchers.IO) {
        notificationPostgrest.select {
            filter {
                eq("user_id", userId)
                eq("is_read", false)
            }
        }.decodeList<NotificationDto>()
    }

    // 2. UPDATE: Button "[Mark Consumed]" updates status to 'CONSUMED'
    suspend fun markItemAsConsumed(itemId: String, itemTitle: String) = withContext(Dispatchers.IO) {
        // Update pantry item
        pantryPostgrest.update({
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

    // 4. DELETE: Dismiss an alert from the 'notifications' table
    suspend fun dismissAlert(notificationId: Long) = withContext(Dispatchers.IO) {
        notificationPostgrest.delete {
            filter {
                eq("id", notificationId)
            }
        }
    }

    // 5. UPDATE: Mark a notification as read
    suspend fun markNotificationAsRead(notificationId: Long) = withContext(Dispatchers.IO) {
        notificationPostgrest.update({
            set("is_read", true)
        }) {
            filter {
                eq("id", notificationId)
            }
        }
    }

    // 3. CREATE: Button "[Add to Restock]" adds row to shopping_items table
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
