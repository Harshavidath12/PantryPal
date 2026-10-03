package com.example.pantrypal.data.repository

import com.example.pantrypal.data.model.AlertUrgency
import com.example.pantrypal.data.model.NotificationItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Notification & Alert operations (CRUD).
 */
interface NotificationRepository {
    fun getNotifications(): Flow<List<NotificationItem>>
    suspend fun markItemConsumed(alertId: String): Boolean
    suspend fun toggleRestockStatus(alertId: String): Boolean
    suspend fun deleteNotification(alertId: String): Boolean
    suspend fun addCustomAlert(
        title: String,
        categoryAndLocation: String,
        expiryText: String,
        urgency: AlertUrgency
    ): Boolean
    suspend fun refreshData()
}
