package com.example.pantrypal.data.repository

import com.example.pantrypal.data.model.PantryItemDto
import com.example.pantrypal.data.model.SharedUpdateDto
import com.example.pantrypal.data.model.ShoppingItem
import com.example.pantrypal.data.model.ShoppingItemDto
import com.example.pantrypal.data.model.ShoppingStatusType
import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi

class ShoppingRepository {

    @OptIn(InternalSerializationApi::class)
    private val shoppingPostgrest = SupabaseProvider.client.from("shopping_items")

    @OptIn(InternalSerializationApi::class)
    private val pantryPostgrest = SupabaseProvider.client.from("pantry_items")

    // Default sample data matching Figma design
    fun getInitialToBuyItems(): List<ShoppingItem> {
        return listOf(
            ShoppingItem(
                id = "1",
                name = "Whole Milk 1L",
                quantity = "2 cartons",
                statusText = "Auto-queued (Expired in Pantry)",
                statusType = ShoppingStatusType.EXPIRED,
                secondaryText = "Triggered by Alex"
            ),
            ShoppingItem(
                id = "2",
                name = "Eggs (Dozen)",
                quantity = "1 carton",
                statusText = "Auto-queued (Low Stock < 3 eggs)",
                statusType = ShoppingStatusType.LOW_STOCK,
                secondaryText = "Triggered by Jamie"
            ),
            ShoppingItem(
                id = "3",
                name = "Extra Virgin Olive Oil",
                quantity = "1 bottle",
                statusText = "Manual entry",
                statusType = ShoppingStatusType.MANUAL,
                secondaryText = "Added by Taylor"
            ),
            ShoppingItem(
                id = "4",
                name = "Rolled Oats 1kg",
                quantity = "1 bag",
                statusText = "Auto-queued (Pantry Empty)",
                statusType = ShoppingStatusType.PANTRY_EMPTY,
                secondaryText = ""
            )
        )
    }

    fun getInitialRecentlyPurchasedItems(): List<ShoppingItem> {
        return listOf(
            ShoppingItem(
                id = "101",
                name = "Sourdough Bread",
                quantity = "1 loaf",
                statusText = "Bought today",
                statusType = ShoppingStatusType.MANUAL,
                secondaryText = "Bought today by Taylor",
                isBought = true
            )
        )
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun addShoppingItemRemote(item: ShoppingItem) = withContext(Dispatchers.IO) {
        try {
            shoppingPostgrest.insert(
                ShoppingItemDto(
                    itemName = item.name,
                    category = "Household",
                    isBought = false
                )
            )
            SupabaseProvider.client.from("shared_updates").insert(
                SharedUpdateDto(
                    actorInitials = "TY",
                    actionText = "Added ${item.name} to shopping list",
                    detailsText = "Manual entry"
                )
            )
        } catch (e: Exception) {
            // Log or fallback gracefully
            e.printStackTrace()
        }
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun moveToPantryRemote(item: ShoppingItem) = withContext(Dispatchers.IO) {
        try {
            pantryPostgrest.insert(
                PantryItemDto(
                    title = item.name,
                    categoryZone = "Pantry",
                    expiryText = "7 days",
                    urgency = "ACTIVE",
                    status = "ACTIVE"
                )
            )
            SupabaseProvider.client.from("shared_updates").insert(
                SharedUpdateDto(
                    actorInitials = "TY",
                    actionText = "Restocked ${item.name} into Pantry",
                    detailsText = "Moved from Shopping"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
