package com.example.pantrypal.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantrypal.data.model.ShoppingFilter
import com.example.pantrypal.data.model.ShoppingItem
import com.example.pantrypal.data.model.ShoppingStatusType
import com.example.pantrypal.data.repository.ShoppingRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ShoppingViewModel(
    private val repository: ShoppingRepository = ShoppingRepository()
) : ViewModel() {

    private val _toBuyItems = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val toBuyItems: StateFlow<List<ShoppingItem>> = _toBuyItems.asStateFlow()

    private val _recentlyPurchasedItems = MutableStateFlow<List<ShoppingItem>>(emptyList())
    val recentlyPurchasedItems: StateFlow<List<ShoppingItem>> = _recentlyPurchasedItems.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ShoppingFilter.ALL)
    val selectedFilter: StateFlow<ShoppingFilter> = _selectedFilter.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    // Combined Flow for filtered To Buy items
    val filteredToBuyItems: StateFlow<List<ShoppingItem>> = combine(_toBuyItems, _selectedFilter) { items, filter ->
        when (filter) {
            ShoppingFilter.ALL -> items
            ShoppingFilter.AUTO_QUEUED -> items.filter { it.statusType != ShoppingStatusType.MANUAL }
            ShoppingFilter.MANUAL_ENTRY -> items.filter { it.statusType == ShoppingStatusType.MANUAL }
            ShoppingFilter.LOW_STOCK -> items.filter { it.statusType == ShoppingStatusType.LOW_STOCK }
            ShoppingFilter.EXPIRED -> items.filter { it.statusType == ShoppingStatusType.EXPIRED }
        }
    }.toStateFlow(viewModelScope, _toBuyItems.value)

    init {
        // Load initial data
        _toBuyItems.value = repository.getInitialToBuyItems()
        _recentlyPurchasedItems.value = repository.getInitialRecentlyPurchasedItems()
    }

    fun addItem(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch {
                _uiMessage.emit("Please enter an item name.")
            }
            return false
        }

        val newItem = ShoppingItem(
            id = System.currentTimeMillis().toString(),
            name = trimmed,
            quantity = "1 item",
            statusText = "Manual entry",
            statusType = ShoppingStatusType.MANUAL,
            secondaryText = "Added by Taylor"
        )

        _toBuyItems.value = _toBuyItems.value + newItem

        viewModelScope.launch {
            repository.addShoppingItemRemote(newItem)
            _uiMessage.emit("Added \"$trimmed\" to shopping list")
        }
        return true
    }

    fun toggleItemSelection(itemId: String) {
        _toBuyItems.value = _toBuyItems.value.map { item ->
            if (item.id == itemId) {
                item.copy(isSelected = !item.isSelected)
            } else {
                item
            }
        }
    }

    fun restockSelected() {
        val selectedItems = _toBuyItems.value.filter { it.isSelected }
        if (selectedItems.isEmpty()) return

        val count = selectedItems.size

        // Remove selected from To Buy
        _toBuyItems.value = _toBuyItems.value.filter { !it.isSelected }

        // Add to Recently Purchased (with updated state)
        val restocked = selectedItems.map {
            it.copy(
                isSelected = false,
                isBought = true,
                secondaryText = "Bought today by Taylor"
            )
        }
        _recentlyPurchasedItems.value = restocked + _recentlyPurchasedItems.value

        viewModelScope.launch {
            _uiMessage.emit("Restocked $count item(s) to Pantry!")
        }
    }

    fun moveToPantry(item: ShoppingItem) {
        _recentlyPurchasedItems.value = _recentlyPurchasedItems.value.filter { it.id != item.id }

        viewModelScope.launch {
            repository.moveToPantryRemote(item)
            _uiMessage.emit("Moved \"${item.name}\" to Pantry!")
        }
    }

    fun setFilter(filter: ShoppingFilter) {
        _selectedFilter.value = filter
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.toStateFlow(
        scope: kotlinx.coroutines.CoroutineScope,
        initialValue: T
    ): StateFlow<T> {
        val state = MutableStateFlow(initialValue)
        scope.launch {
            collect { state.value = it }
        }
        return state.asStateFlow()
    }
}
