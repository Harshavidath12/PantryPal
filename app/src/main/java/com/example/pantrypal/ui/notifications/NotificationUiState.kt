package com.example.pantrypal.ui.notifications

import com.example.pantrypal.data.model.FilterType
import com.example.pantrypal.data.model.NotificationItem

data class NotificationUiState(
    val items: List<NotificationItem> = emptyList(),
    val activeFilter: FilterType = FilterType.ALL,
    val totalCount: Int = 0,
    val expiryCount: Int = 0,
    val isLoading: Boolean = false,
    val userMessage: String? = null
)
