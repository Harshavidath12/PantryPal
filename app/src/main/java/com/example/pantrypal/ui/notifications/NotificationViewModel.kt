package com.example.pantrypal.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantrypal.data.model.NotificationDto
import com.example.pantrypal.data.model.PantryItemDto
import com.example.pantrypal.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<NotificationDto>>(emptyList())
    val alerts = _alerts.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()

    // Assuming user ID 1 for now
    private val currentUserId = 1L

    fun fetchAlerts() {
        viewModelScope.launch {
            try {
                // Now fetching from the notifications table
                _alerts.value = repository.getActiveAlerts(currentUserId).sortedByDescending { it.createdAt }
            } catch (e: Exception) {
                _message.emit("Error fetching: ${e.message}")
            }
        }
    }

    fun onDismissAlertClicked(notificationId: Long?) {
        if (notificationId == null) return
        viewModelScope.launch {
            try {
                repository.dismissAlert(notificationId)
                // Remove from local state immediately for fast UI feedback
                _alerts.value = _alerts.value.filter { it.id != notificationId }
            } catch (e: Exception) {
                _message.emit("Failed to dismiss: ${e.message}")
            }
        }
    }

    fun onMarkConsumedClicked(item: NotificationDto) {
        val notificationId = item.id ?: return
        
        viewModelScope.launch {
            try {
                // Update the notification in Supabase to be read
                repository.markNotificationAsRead(notificationId)
                
                // If we have a reference ID, update the actual pantry item too
                item.referenceId?.let { refId ->
                    repository.markItemAsConsumed(refId, item.message)
                }
                
                // Update UI state immediately by removing the read notification
                _alerts.value = _alerts.value.filter { it.id != notificationId }
                
                _message.emit("Item marked as consumed")
            } catch (e: Exception) {
                _message.emit("Failed to mark consumed: ${e.message}")
                e.printStackTrace() // Log error to logcat
            }
        }
    }

    fun onAddToRestockClicked(item: NotificationDto) {
        viewModelScope.launch {
            try {
                // Just use the message as the item name for now, and a default category
                repository.addToRestockList(item.message, "Pantry")
                _message.emit("Added to Restock List")
                
                // Also dismiss the notification
                item.id?.let { 
                    repository.dismissAlert(it) 
                    _alerts.value = _alerts.value.filter { n -> n.id != it }
                }
            } catch (e: Exception) {
                _message.emit("Failed to add: ${e.message}")
            }
        }
    }
}
