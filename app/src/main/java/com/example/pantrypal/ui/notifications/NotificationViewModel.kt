package com.example.pantrypal.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantrypal.data.model.PantryItemDto
import com.example.pantrypal.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

class NotificationViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {

    @OptIn(InternalSerializationApi::class)
    private val _alerts = MutableStateFlow<List<PantryItemDto>>(emptyList())
    
    @OptIn(InternalSerializationApi::class)
    val alerts = _alerts.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message = _message.asSharedFlow()

    @OptIn(InternalSerializationApi::class)
    fun fetchAlerts() {
        viewModelScope.launch {
            try {
                _alerts.value = repository.getActiveAlerts()
            } catch (e: Exception) {
                _message.emit("Error fetching: ${e.message}")
            }
        }
    }

    @OptIn(InternalSerializationApi::class)
    fun onMarkConsumedClicked(item: PantryItemDto) {
        val id = item.id ?: return
        viewModelScope.launch {
            try {
                repository.markItemAsConsumed(id, item.title)
                _message.emit("${item.title} marked as consumed")
                fetchAlerts() // Refresh list
            } catch (e: Exception) {
                _message.emit("Failed to update: ${e.message}")
            }
        }
    }

    @OptIn(InternalSerializationApi::class)
    fun onAddToRestockClicked(item: PantryItemDto) {
        viewModelScope.launch {
            try {
                repository.addToRestockList(item.title, item.categoryZone)
                _message.emit("${item.title} added to Restock List")
            } catch (e: Exception) {
                _message.emit("Failed to add: ${e.message}")
            }
        }
    }
}
