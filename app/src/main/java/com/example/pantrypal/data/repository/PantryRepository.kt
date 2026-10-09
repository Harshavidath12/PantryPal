package com.example.pantrypal.data.repository

import com.example.pantrypal.R
import com.example.pantrypal.data.model.AddGroceryItemDto
import com.example.pantrypal.data.model.PantryCategory
import com.example.pantrypal.data.model.PantryItem
import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class PantryRepository {

    companion object {
        // Dynamic list shared across repository instances for newly added or fetched items
        private val dynamicItems = mutableListOf<PantryItem>()

        fun addDynamicItem(item: PantryItem) {
            synchronized(dynamicItems) {
                dynamicItems.removeAll { it.id == item.id || it.title.equals(item.title, ignoreCase = true) }
                dynamicItems.add(0, item)
            }
        }
    }

    private val initialItems = mutableListOf(
        PantryItem(
            id = "p1",
            title = "Whole Milk 1L",
            category = "Dairy",
            location = "Zone: Fridge · Shelf 1 · Qty: 1 carton",
            expiryText = "Expires: Tomorrow",
            daysUntilExpiry = 1,
            stockPercent = 50,
            stockText = "50%",
            isExpiringSoon = true,
            iconResId = R.drawable.ic_dairy
        ),
        PantryItem(
            id = "p2",
            title = "Baby Spinach",
            category = "Vegetables",
            location = "Zone: Crisper Drawer · Qty: 1 bag",
            expiryText = "Expires: In 2 days",
            daysUntilExpiry = 2,
            stockPercent = 30,
            stockText = "30%",
            isExpiringSoon = true,
            iconResId = R.drawable.ic_vegetable
        ),
        PantryItem(
            id = "p3",
            title = "Greek Yogurt",
            category = "Dairy",
            location = "Zone: Fridge · Shelf 2 · Qty: 2 tubs",
            expiryText = "Expires: In 3 days",
            daysUntilExpiry = 3,
            stockPercent = 60,
            stockText = "60%",
            isExpiringSoon = true,
            iconResId = R.drawable.ic_dairy
        ),
        PantryItem(
            id = "p4",
            title = "Strawberries",
            category = "Fruits",
            location = "Zone: Fridge · Shelf 1 · Qty: 1 box",
            expiryText = "Expires: In 3 days",
            daysUntilExpiry = 3,
            stockPercent = 20,
            stockText = "20% (Low)",
            isExpiringSoon = true,
            iconResId = R.drawable.ic_fruit
        ),
        PantryItem(
            id = "p5",
            title = "Sourdough Bread",
            category = "Grains",
            location = "Pantry Shelf A · Expires in 4 days",
            expiryText = "Expires in 4 days",
            daysUntilExpiry = 4,
            stockPercent = 75,
            stockText = "75% (3/4 loaf)",
            isExpiringSoon = false,
            iconResId = R.drawable.ic_food_bread
        ),
        PantryItem(
            id = "p6",
            title = "Olive Oil Extra Virgin",
            category = "Canned Goods",
            location = "Cabinet 2 · Expires in 90 days",
            expiryText = "Expires in 90 days",
            daysUntilExpiry = 90,
            stockPercent = 25,
            stockText = "25% (Low)",
            isExpiringSoon = false,
            iconResId = R.drawable.ic_canned
        ),
        PantryItem(
            id = "p7",
            title = "Rolled Oats 1kg",
            category = "Grains",
            location = "Dry Storage Bin · Expires in 180 days",
            expiryText = "Expires in 180 days",
            daysUntilExpiry = 180,
            stockPercent = 100,
            stockText = "100% (Full)",
            isExpiringSoon = false,
            iconResId = R.drawable.ic_grain
        )
    )

    init {
        // Generate additional items to match total 48 count
        val categories = listOf("Dairy", "Vegetables", "Fruits", "Grains", "Canned Goods", "Snacks")
        for (i in 8..48) {
            val cat = categories[i % categories.size]
            initialItems.add(
                PantryItem(
                    id = "p$i",
                    title = "Pantry Item $i ($cat)",
                    category = cat,
                    location = "Storage Zone A · Shelf ${(i % 3) + 1}",
                    expiryText = "Expires in ${(i * 3)} days",
                    daysUntilExpiry = i * 3,
                    stockPercent = (i * 17) % 100 + 10,
                    stockText = "${(i * 17) % 100 + 10}%",
                    isExpiringSoon = false
                )
            )
        }
    }

    suspend fun fetchSupabaseItems(): List<PantryItem> = withContext(Dispatchers.IO) {
        try {
            val dtoList = SupabaseProvider.client
                .from("add_groceries_items")
                .select()
                .decodeList<AddGroceryItemDto>()

            val converted = dtoList.map { dto ->
                val idStr = runCatching { dto.id?.jsonPrimitive?.content }.getOrNull() ?: UUID.randomUUID().toString()
                val daysUntilExpiry: Int = if (!dto.expiryDate.isNullOrBlank()) {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                        val expiry = sdf.parse(dto.expiryDate)
                        if (expiry != null) {
                            val diff = expiry.time - System.currentTimeMillis()
                            (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                        } else {
                            30
                        }
                    } catch (e: Exception) {
                        30
                    }
                } else {
                    30
                }

                val expiryText = when {
                    daysUntilExpiry == 0 -> "Expires: Today"
                    daysUntilExpiry == 1 -> "Expires: Tomorrow"
                    daysUntilExpiry <= 3 -> "Expires: In $daysUntilExpiry days"
                    else -> "Expires in $daysUntilExpiry days"
                }

                val cat = dto.category?.trim().orEmpty().ifBlank { "Snacks" }
                val iconResId = when (cat.lowercase()) {
                    "dairy" -> R.drawable.ic_dairy
                    "vegetables" -> R.drawable.ic_vegetable
                    "fruits" -> R.drawable.ic_fruit
                    "grains" -> R.drawable.ic_food_bread
                    "canned goods" -> R.drawable.ic_canned
                    "snacks" -> R.drawable.ic_snack
                    else -> R.drawable.ic_pantry
                }

                val qtyInt = dto.quantity?.toInt() ?: 1
                PantryItem(
                    id = idStr,
                    title = dto.itemName.ifBlank { "Unknown Item" },
                    category = cat,
                    location = "Zone: ${dto.storageZone ?: "Pantry"} · Qty: $qtyInt",
                    quantityText = "$qtyInt",
                    expiryText = expiryText,
                    daysUntilExpiry = daysUntilExpiry,
                    ownerName = "You",
                    iconResId = iconResId,
                    stockPercent = 100,
                    stockText = "100% (Full)",
                    isExpiringSoon = daysUntilExpiry <= 3,
                    progress = 1.0f
                )
            }

            for (item in converted) {
                addDynamicItem(item)
            }
            getAllPantryItems()
        } catch (e: Exception) {
            android.util.Log.e("PantryRepository", "Failed to fetch from Supabase", e)
            getAllPantryItems()
        }
    }

    fun getAllPantryItems(): List<PantryItem> {
        val all = mutableListOf<PantryItem>()
        synchronized(dynamicItems) {
            all.addAll(dynamicItems)
        }
        all.addAll(initialItems)
        return all
    }

    fun deletePantryItem(id: String) {
        synchronized(dynamicItems) {
            dynamicItems.removeAll { it.id == id }
        }
        initialItems.removeAll { it.id == id }
    }

    fun addPantryItem(item: PantryItem) {
        addDynamicItem(item)
    }

    fun getCategories(): List<PantryCategory> {
        val items = getAllPantryItems()
        return listOf(
            PantryCategory("c0", "All", items.size, R.drawable.ic_pantry),
            PantryCategory("c1", "Dairy", items.count { it.category.equals("Dairy", ignoreCase = true) }, R.drawable.ic_dairy),
            PantryCategory("c2", "Vegetables", items.count { it.category.equals("Vegetables", ignoreCase = true) }, R.drawable.ic_vegetable),
            PantryCategory("c3", "Fruits", items.count { it.category.equals("Fruits", ignoreCase = true) }, R.drawable.ic_fruit),
            PantryCategory("c4", "Grains", items.count { it.category.equals("Grains", ignoreCase = true) }, R.drawable.ic_grain),
            PantryCategory("c5", "Canned Goods", items.count { it.category.equals("Canned Goods", ignoreCase = true) }, R.drawable.ic_canned),
            PantryCategory("c6", "Snacks", items.count { it.category.equals("Snacks", ignoreCase = true) }, R.drawable.ic_snack)
        )
    }
}
