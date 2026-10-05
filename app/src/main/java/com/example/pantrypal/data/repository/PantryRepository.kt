package com.example.pantrypal.data.repository

import com.example.pantrypal.R
import com.example.pantrypal.data.model.PantryCategory
import com.example.pantrypal.data.model.PantryItem

class PantryRepository {

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

    fun getAllPantryItems(): List<PantryItem> {
        return initialItems.toList()
    }

    fun deletePantryItem(id: String) {
        initialItems.removeAll { it.id == id }
    }

    fun addPantryItem(item: PantryItem) {
        initialItems.add(0, item)
    }

    fun getCategories(): List<PantryCategory> {
        val items = getAllPantryItems()
        return listOf(
            PantryCategory("c1", "Dairy", items.count { it.category.equals("Dairy", ignoreCase = true) }, R.drawable.ic_dairy),
            PantryCategory("c2", "Vegetables", items.count { it.category.equals("Vegetables", ignoreCase = true) }, R.drawable.ic_vegetable),
            PantryCategory("c3", "Fruits", items.count { it.category.equals("Fruits", ignoreCase = true) }, R.drawable.ic_fruit),
            PantryCategory("c4", "Grains", items.count { it.category.equals("Grains", ignoreCase = true) }, R.drawable.ic_grain),
            PantryCategory("c5", "Canned Goods", items.count { it.category.equals("Canned Goods", ignoreCase = true) }, R.drawable.ic_canned),
            PantryCategory("c6", "Snacks", items.count { it.category.equals("Snacks", ignoreCase = true) }, R.drawable.ic_snack)
        )
    }
}
