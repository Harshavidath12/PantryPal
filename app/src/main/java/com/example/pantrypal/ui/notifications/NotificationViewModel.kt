package com.example.pantrypal.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantrypal.data.model.AlertUrgency
import com.example.pantrypal.data.model.FilterType
import com.example.pantrypal.data.model.NotificationItem
import com.example.pantrypal.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(FilterType.ALL)
    private val _userMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<NotificationUiState> = combine(
        repository.getNotifications(),
        _activeFilter,
        _userMessage
    ) { notifications, filter, message ->

        val expiryAlertsCount = notifications.count { it is NotificationItem.ExpiryAlert }
        val filteredList = applyFilter(notifications, filter)

        NotificationUiState(
            items = filteredList,
            activeFilter = filter,
            totalCount = expiryAlertsCount + notifications.count { it is NotificationItem.SharedUpdate },
            expiryCount = expiryAlertsCount,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationUiState()
    )

    fun setFilter(filterType: FilterType) {
        _activeFilter.value = filterType
    }

    fun markConsumed(alertId: String, itemName: String) {
        viewModelScope.launch {
            val success = repository.markItemConsumed(alertId)
            if (success) {
                _userMessage.value = "Marked $itemName as consumed"
            }
        }
    }

    fun toggleRestock(alertId: String, itemName: String) {
        viewModelScope.launch {
            val success = repository.toggleRestockStatus(alertId)
            if (success) {
                _userMessage.value = "Updated restock status for $itemName"
            }
        }
    }

    fun deleteNotification(alertId: String, itemName: String) {
        viewModelScope.launch {
            val success = repository.deleteNotification(alertId)
            if (success) {
                _userMessage.value = "Dismissed alert for $itemName"
            }
        }
    }

    fun addNewAlert(title: String, categoryAndLocation: String, expiryText: String, urgency: AlertUrgency) {
        viewModelScope.launch {
            val success = repository.addCustomAlert(title, categoryAndLocation, expiryText, urgency)
            if (success) {
                _userMessage.value = "Added alert for $title"
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshData()
            _userMessage.value = "Alerts & Reminders synced"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    private fun applyFilter(list: List<NotificationItem>, filter: FilterType): List<NotificationItem> {
        return when (filter) {
            FilterType.ALL -> list
            FilterType.EXPIRY -> {
                list.filter {
                    it is NotificationItem.SectionHeader && (it.id == "sec_today" || it.id == "sec_soon") ||
                            it is NotificationItem.ExpiryAlert
                }
            }
            FilterType.SYNC_SHARED -> {
                list.filter {
                    it is NotificationItem.SectionHeader && it.id == "sec_shared" ||
                            it is NotificationItem.SharedUpdate
                }
            }
        }
    }
}
