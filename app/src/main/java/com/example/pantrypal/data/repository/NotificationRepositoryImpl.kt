package com.example.pantrypal.data.repository

import com.example.pantrypal.R
import com.example.pantrypal.data.model.AlertUrgency
import com.example.pantrypal.data.model.NotificationItem
import com.example.pantrypal.data.model.PrimaryButtonType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class NotificationRepositoryImpl : NotificationRepository {

    private val initialItems: MutableList<NotificationItem> = mutableListOf(
        NotificationItem.SectionHeader(
            id = "sec_today",
            title = "EXPIRING TODAY",
            tagText = "• Action Needed",
            isRedTag = true,
            itemCountText = "1 item"
        ),
        NotificationItem.ExpiryAlert(
            id = "alert_yogurt",
            title = "Greek Yogurt (500g)",
            categoryAndLocation = "Dairy & Refrigerated • Shelf 2",
            expiryText = "• Expires: Today at 11:59 PM",
            urgency = AlertUrgency.EXPIRING_TODAY,
            imageResId = R.drawable.ic_food_yogurt,
            primaryButtonType = PrimaryButtonType.RECIPE_IDEAS
        ),
        NotificationItem.SectionHeader(
            id = "sec_soon",
            title = "EXPIRING IN 2–3 DAYS",
            itemCountText = "2 items"
        ),
        NotificationItem.ExpiryAlert(
            id = "alert_bread",
            title = "Sliced Brown Bread",
            categoryAndLocation = "Bakery & Grains • Breadbox",
            expiryText = "• Expires: 2 days left",
            urgency = AlertUrgency.EXPIRING_SOON,
            imageResId = R.drawable.ic_food_bread,
            primaryButtonType = PrimaryButtonType.ADD_TO_RESTOCK
        ),
        NotificationItem.ExpiryAlert(
            id = "alert_strawberries",
            title = "Fresh Strawberries (400g)",
            categoryAndLocation = "Produce & Fruits • Crisper",
            expiryText = "• Expires: 3 days left",
            urgency = AlertUrgency.EXPIRING_SOON,
            imageResId = R.drawable.ic_food_strawberry,
            primaryButtonType = PrimaryButtonType.RECIPE_IDEAS
        ),
        NotificationItem.SectionHeader(
            id = "sec_shared",
            title = "SHARED UPDATES",
            tagText = "👥 Household Feed",
            isRedTag = false
        ),
        NotificationItem.SharedUpdate(
            id = "shared_1",
            userName = "Tharushi",
            userInitials = "TM",
            actionText = "finished whole milk",
            timeAgo = "12m ago",
            detailsText = "➕ Added to restock list",
            linkText = "View List →",
            avatarBgColorRes = R.color.avatar_tm_bg
        ),
        NotificationItem.SharedUpdate(
            id = "shared_2",
            userName = "Alex",
            userInitials = "AK",
            actionText = "cooked Pasta Dinner",
            timeAgo = "2h ago",
            detailsText = "Marked 3 pantry items consumed",
            isSynced = true,
            avatarBgColorRes = R.color.avatar_ak_bg
        )
    )

    private val _notifications = MutableStateFlow<List<NotificationItem>>(initialItems.toList())

    override fun getNotifications(): Flow<List<NotificationItem>> = _notifications.asStateFlow()

    override suspend fun markItemConsumed(alertId: String): Boolean {
        val currentList = _notifications.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == alertId }
        if (index != -1) {
            currentList.removeAt(index)
            recalculateSectionCounts(currentList)
            _notifications.value = currentList
            return true
        }
        return false
    }

    override suspend fun toggleRestockStatus(alertId: String): Boolean {
        val currentList = _notifications.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == alertId }
        if (index != -1 && currentList[index] is NotificationItem.ExpiryAlert) {
            val alert = currentList[index] as NotificationItem.ExpiryAlert
            currentList[index] = alert.copy(isRestockAdded = !alert.isRestockAdded)
            _notifications.value = currentList
            return true
        }
        return false
    }

    override suspend fun deleteNotification(alertId: String): Boolean {
        val currentList = _notifications.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == alertId }
        if (index != -1) {
            currentList.removeAt(index)
            recalculateSectionCounts(currentList)
            _notifications.value = currentList
            return true
        }
        return false
    }

    override suspend fun addCustomAlert(
        title: String,
        categoryAndLocation: String,
        expiryText: String,
        urgency: AlertUrgency
    ): Boolean {
        val currentList = _notifications.value.toMutableList()
        val newAlert = NotificationItem.ExpiryAlert(
            id = UUID.randomUUID().toString(),
            title = title,
            categoryAndLocation = categoryAndLocation,
            expiryText = "• Expires: $expiryText",
            urgency = urgency,
            imageResId = R.drawable.ic_food_yogurt,
            primaryButtonType = PrimaryButtonType.RECIPE_IDEAS
        )

        // Insert into proper section
        val targetSectionId = if (urgency == AlertUrgency.EXPIRING_TODAY) "sec_today" else "sec_soon"
        val sectionIndex = currentList.indexOfFirst { it.id == targetSectionId }
        if (sectionIndex != -1) {
            currentList.add(sectionIndex + 1, newAlert)
        } else {
            currentList.add(0, newAlert)
        }
        recalculateSectionCounts(currentList)
        _notifications.value = currentList
        return true
    }

    override suspend fun refreshData() {
        // Reset or refresh notifications list
        _notifications.value = initialItems.toList()
    }

    private fun recalculateSectionCounts(list: MutableList<NotificationItem>) {
        val todayCount = list.count { it is NotificationItem.ExpiryAlert && it.urgency == AlertUrgency.EXPIRING_TODAY }
        val soonCount = list.count { it is NotificationItem.ExpiryAlert && it.urgency == AlertUrgency.EXPIRING_SOON }

        val todayHeaderIndex = list.indexOfFirst { it.id == "sec_today" }
        if (todayHeaderIndex != -1 && list[todayHeaderIndex] is NotificationItem.SectionHeader) {
            val header = list[todayHeaderIndex] as NotificationItem.SectionHeader
            list[todayHeaderIndex] = header.copy(itemCountText = if (todayCount > 0) "$todayCount item${if (todayCount > 1) "s" else ""}" else "0 items")
        }

        val soonHeaderIndex = list.indexOfFirst { it.id == "sec_soon" }
        if (soonHeaderIndex != -1 && list[soonHeaderIndex] is NotificationItem.SectionHeader) {
            val header = list[soonHeaderIndex] as NotificationItem.SectionHeader
            list[soonHeaderIndex] = header.copy(itemCountText = if (soonCount > 0) "$soonCount item${if (soonCount > 1) "s" else ""}" else "0 items")
        }
    }
}
