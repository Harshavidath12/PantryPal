package com.example.pantrypal.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pantrypal.data.model.PantryCategory
import com.example.pantrypal.data.model.PantryItem
import com.example.pantrypal.data.repository.PantryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PantryViewModel(
    private val repository: PantryRepository = PantryRepository()
) : ViewModel() {

    private val _allItems = MutableStateFlow<List<PantryItem>>(emptyList())
    val allItems: StateFlow<List<PantryItem>> = _allItems.asStateFlow()

    private val _categories = MutableStateFlow<List<PantryCategory>>(emptyList())
    val categories: StateFlow<List<PantryCategory>> = _categories.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    // Expiring soon items
    val expiringSoonItems: StateFlow<List<PantryItem>> = combine(_allItems) { items ->
        items[0].filter { it.isExpiringSoon || it.daysUntilExpiry <= 3 }
    }.toStateFlow(viewModelScope, emptyList())

    // Filtered items based on search query and selected category
    val filteredPantryItems: StateFlow<List<PantryItem>> = combine(
        _allItems,
        _searchQuery,
        _selectedCategory
    ) { items, query, category ->
        var result = items
        if (!category.isNullOrBlank()) {
            result = result.filter { it.category.equals(category, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                        it.category.lowercase().contains(q) ||
                        it.location.lowercase().contains(q)
            }
        }
        result
    }.toStateFlow(viewModelScope, emptyList())

    init {
        loadData()
    }

    fun loadData() {
        val items = repository.getAllPantryItems()
        _allItems.value = items
        _categories.value = repository.getCategories()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryName: String?) {
        if (categoryName.isNullOrBlank() || categoryName.equals("All", ignoreCase = true)) {
            _selectedCategory.value = null
        } else if (_selectedCategory.value.equals(categoryName, ignoreCase = true)) {
            _selectedCategory.value = null
        } else {
            _selectedCategory.value = categoryName
        }
    }

    fun deleteItem(id: String) {
        repository.deletePantryItem(id)
        loadData()
        viewModelScope.launch {
            _uiMessage.emit("Item deleted from Pantry")
        }
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
