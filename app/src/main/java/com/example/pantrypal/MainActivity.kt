package com.example.pantrypal

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pantrypal.data.model.AlertUrgency
import com.example.pantrypal.data.model.NotificationDto
import com.example.pantrypal.data.model.NotificationItem
import com.example.pantrypal.data.model.PantryItem
import com.example.pantrypal.data.model.PrimaryButtonType
import com.example.pantrypal.data.model.ShoppingFilter
import com.example.pantrypal.data.repository.NotificationRepository
import com.example.pantrypal.data.repository.PantryRepository
import com.example.pantrypal.data.repository.ShoppingRepository
import com.example.pantrypal.databinding.ActivityMainBinding
import com.example.pantrypal.databinding.DialogAddAlertBinding
import com.example.pantrypal.databinding.DialogRecipeIdeasBinding
import com.example.pantrypal.databinding.DialogShoppingFilterBinding
import com.example.pantrypal.ui.components.ItemDetailsDialog
import com.example.pantrypal.ui.notifications.NotificationAdapter
import com.example.pantrypal.ui.notifications.NotificationViewModel
import com.example.pantrypal.ui.notifications.NotificationViewModelFactory
import com.example.pantrypal.ui.pantry.CategoryChipHorizontalAdapter
import com.example.pantrypal.ui.pantry.ExpiringSoonAdapter
import com.example.pantrypal.ui.pantry.ExpiringSoonFullAdapter
import com.example.pantrypal.ui.pantry.PantryOverviewAdapter
import com.example.pantrypal.ui.pantry.PantryViewModel
import com.example.pantrypal.ui.pantry.PantryViewModelFactory
import com.example.pantrypal.ui.shopping.RecentlyPurchasedAdapter
import com.example.pantrypal.ui.shopping.ShoppingAdapter
import com.example.pantrypal.ui.shopping.ShoppingViewModel
import com.example.pantrypal.ui.shopping.ShoppingViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private enum class NavTab {
        PANTRY, SHOPPING, SURPLUS, PROFILE
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: NotificationViewModel
    private lateinit var shoppingViewModel: ShoppingViewModel
    private lateinit var pantryViewModel: PantryViewModel

    private lateinit var adapter: NotificationAdapter
    private lateinit var shoppingAdapter: ShoppingAdapter
    private lateinit var recentlyPurchasedAdapter: RecentlyPurchasedAdapter

    private lateinit var expiringSoonAdapter: ExpiringSoonAdapter
    private lateinit var pantryOverviewAdapter: PantryOverviewAdapter

    private lateinit var fullExpiringSoonAdapter: ExpiringSoonFullAdapter
    private lateinit var categoryChipHorizontalAdapter: CategoryChipHorizontalAdapter
    private lateinit var fullPantryOverviewAdapter: PantryOverviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupArchitecture()
        setupPantryHomeUI()
        setupFullPantryItemsUI()
        setupRecyclerView()
        setupShoppingUI()
        setupClickListeners()
        observeUiState()
    }

    private fun setupArchitecture() {
        val notificationRepository = NotificationRepository()
        val notificationFactory = NotificationViewModelFactory(notificationRepository)
        viewModel = ViewModelProvider(this, notificationFactory)[NotificationViewModel::class.java]

        val shoppingRepository = ShoppingRepository()
        val shoppingFactory = ShoppingViewModelFactory(shoppingRepository)
        shoppingViewModel = ViewModelProvider(this, shoppingFactory)[ShoppingViewModel::class.java]

        val pantryRepository = PantryRepository()
        val pantryFactory = PantryViewModelFactory(pantryRepository)
        pantryViewModel = ViewModelProvider(this, pantryFactory)[PantryViewModel::class.java]
    }

    private fun setupPantryHomeUI() {
        // Expiring Soon Adapter
        expiringSoonAdapter = ExpiringSoonAdapter { item ->
            showItemDetailsDialog(item)
        }
        binding.pantryHomeScreenContainer.rvExpiringSoon.layoutManager = LinearLayoutManager(this)
        binding.pantryHomeScreenContainer.rvExpiringSoon.adapter = expiringSoonAdapter

        // Pantry Overview Adapter
        pantryOverviewAdapter = PantryOverviewAdapter { item ->
            showItemDetailsDialog(item)
        }
        binding.pantryHomeScreenContainer.rvAllPantryItems.layoutManager = LinearLayoutManager(this)
        binding.pantryHomeScreenContainer.rvAllPantryItems.adapter = pantryOverviewAdapter

        // Search bar text watcher
        binding.pantryHomeScreenContainer.etSearchPantry.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pantryViewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Quick Action Buttons
        binding.pantryHomeScreenContainer.btnAddItemQuick.setOnClickListener {
            showAddAlertDialog()
        }

        binding.pantryHomeScreenContainer.btnShoppingListQuick.setOnClickListener {
            selectTab(NavTab.SHOPPING)
        }

        binding.pantryHomeScreenContainer.btnSurplusHubQuick.setOnClickListener {
            selectTab(NavTab.SURPLUS)
        }

        // View All Expiring -> navigate to Full Pantry Items Screen
        binding.pantryHomeScreenContainer.tvViewAllExpiring.setOnClickListener {
            showFullPantryItemsScreen()
        }

        // View All Pantry Button -> navigate to Full Pantry Items Screen
        binding.pantryHomeScreenContainer.btnViewAllPantry.setOnClickListener {
            showFullPantryItemsScreen()
        }

        // Bell Icon on Home Dashboard Header
        binding.pantryHomeScreenContainer.btnHomeBell.setOnClickListener {
            binding.notificationsScreenContainer.visibility = View.VISIBLE
        }
    }

    private fun setupFullPantryItemsUI() {
        // Expiring Soon Full Adapter
        fullExpiringSoonAdapter = ExpiringSoonFullAdapter(
            onItemClick = { item -> showItemDetailsDialog(item) },
            onUsedClick = { item ->
                pantryViewModel.deleteItem(item.id)
                Toast.makeText(this, "Marked ${item.title} as used", Toast.LENGTH_SHORT).show()
            }
        )
        binding.fullPantryItemsScreenContainer.rvExpiringSoonFull.layoutManager = LinearLayoutManager(this)
        binding.fullPantryItemsScreenContainer.rvExpiringSoonFull.adapter = fullExpiringSoonAdapter

        // Horizontal Category Chips Adapter
        categoryChipHorizontalAdapter = CategoryChipHorizontalAdapter { category ->
            pantryViewModel.selectCategory(category.name)
        }
        binding.fullPantryItemsScreenContainer.rvCategoriesChipsHorizontal.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.fullPantryItemsScreenContainer.rvCategoriesChipsHorizontal.adapter = categoryChipHorizontalAdapter

        // Full Pantry Overview Adapter
        fullPantryOverviewAdapter = PantryOverviewAdapter { item ->
            showItemDetailsDialog(item)
        }
        binding.fullPantryItemsScreenContainer.rvFullPantryItems.layoutManager = LinearLayoutManager(this)
        binding.fullPantryItemsScreenContainer.rvFullPantryItems.adapter = fullPantryOverviewAdapter

        // Full Search Bar Text Watcher
        binding.fullPantryItemsScreenContainer.etSearchFullPantry.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pantryViewModel.setSearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Barcode Scan Button
        binding.fullPantryItemsScreenContainer.btnScanBarcode.setOnClickListener {
            Toast.makeText(this, "Barcode Scanner (Ready for Integration)", Toast.LENGTH_SHORT).show()
        }

        // Quick Action Buttons
        binding.fullPantryItemsScreenContainer.btnAddItemFull.setOnClickListener {
            showAddAlertDialog()
        }

        binding.fullPantryItemsScreenContainer.btnShoppingListFull.setOnClickListener {
            selectTab(NavTab.SHOPPING)
        }

        binding.fullPantryItemsScreenContainer.btnSurplusHubFull.setOnClickListener {
            selectTab(NavTab.SURPLUS)
        }

        // View All Expiring
        binding.fullPantryItemsScreenContainer.tvViewAllExpiringFull.setOnClickListener {
            pantryViewModel.selectCategory(null)
            pantryViewModel.setSearchQuery("")
            Toast.makeText(this, "Showing expiring items", Toast.LENGTH_SHORT).show()
        }

        // Filter / Sort Button
        binding.fullPantryItemsScreenContainer.btnFilterSortPantry.setOnClickListener {
            showPantryFilterSortDialog()
        }

        // Bell Icon on Full Pantry Header
        binding.fullPantryItemsScreenContainer.btnFullBell.setOnClickListener {
            binding.notificationsScreenContainer.visibility = View.VISIBLE
        }
    }

    private fun showFullPantryItemsScreen() {
        binding.pantryHomeScreenContainer.root.visibility = View.GONE
        binding.shoppingScreenContainer.root.visibility = View.GONE
        binding.notificationsScreenContainer.visibility = View.GONE
        binding.fullPantryItemsScreenContainer.root.visibility = View.VISIBLE
        selectTab(NavTab.PANTRY)
    }

    private fun hideFullPantryItemsScreen() {
        binding.fullPantryItemsScreenContainer.root.visibility = View.GONE
        binding.pantryHomeScreenContainer.root.visibility = View.VISIBLE
        selectTab(NavTab.PANTRY)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.notificationsScreenContainer.visibility == View.VISIBLE) {
            binding.notificationsScreenContainer.visibility = View.GONE
        } else if (binding.fullPantryItemsScreenContainer.root.visibility == View.VISIBLE) {
            hideFullPantryItemsScreen()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(
            onPrimaryActionClicked = { alert ->
                if (alert.primaryButtonType == PrimaryButtonType.RECIPE_IDEAS) {
                    showRecipeIdeasDialog(alert)
                } else {
                    viewModel.onAddToRestockClicked(
                        NotificationDto(
                            id = alert.id.toLongOrNull() ?: 0L,
                            userId = 1L,
                            message = alert.title,
                            type = alert.urgency.name
                        )
                    )
                }
            },
            onMarkConsumedClicked = { alert ->
                viewModel.onMarkConsumedClicked(
                    NotificationDto(
                        id = alert.id.toLongOrNull() ?: 0L,
                        userId = 1L,
                        message = alert.title,
                        type = alert.urgency.name
                    )
                )
            },
            onDeleteAlertClicked = { alert ->
                viewModel.onDismissAlertClicked(alert.id.toLongOrNull())
            },
            onSharedUpdateClicked = { update ->
                Toast.makeText(this, "Opening details for ${update.userName}'s update", Toast.LENGTH_SHORT).show()
            }
        )

        binding.notificationsScreenContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications).layoutManager = LinearLayoutManager(this)
        binding.notificationsScreenContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications).adapter = adapter
    }

    private fun setupShoppingUI() {
        shoppingAdapter = ShoppingAdapter { item ->
            shoppingViewModel.toggleItemSelection(item.id)
        }
        binding.shoppingScreenContainer.rvShoppingToBuy.layoutManager = LinearLayoutManager(this)
        binding.shoppingScreenContainer.rvShoppingToBuy.adapter = shoppingAdapter

        recentlyPurchasedAdapter = RecentlyPurchasedAdapter { item ->
            shoppingViewModel.moveToPantry(item)
        }
        binding.shoppingScreenContainer.rvRecentlyPurchased.layoutManager = LinearLayoutManager(this)
        binding.shoppingScreenContainer.rvRecentlyPurchased.adapter = recentlyPurchasedAdapter

        // Add item button click
        binding.shoppingScreenContainer.btnAddItem.setOnClickListener {
            val input = binding.shoppingScreenContainer.etAddItem.text.toString()
            if (shoppingViewModel.addItem(input)) {
                binding.shoppingScreenContainer.etAddItem.text?.clear()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.shoppingScreenContainer.etAddItem.windowToken, 0)
            }
        }

        // IME action done on edit text
        binding.shoppingScreenContainer.etAddItem.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val input = binding.shoppingScreenContainer.etAddItem.text.toString()
                if (shoppingViewModel.addItem(input)) {
                    binding.shoppingScreenContainer.etAddItem.text?.clear()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(binding.shoppingScreenContainer.etAddItem.windowToken, 0)
                }
                true
            } else {
                false
            }
        }

        // Restock Selected button
        binding.shoppingScreenContainer.btnRestockSelected.setOnClickListener {
            shoppingViewModel.restockSelected()
        }

        // Share button
        binding.shoppingScreenContainer.btnShare.setOnClickListener {
            val items = shoppingViewModel.filteredToBuyItems.value
            if (items.isEmpty()) {
                Toast.makeText(this, "Shopping list is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sb = StringBuilder()
            sb.append("Household Shopping List (Maple Apt 4B):\n")
            items.forEach {
                sb.append("• ").append(it.name).append(" (").append(it.quantity).append(")\n")
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Household Shopping List")
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            startActivity(Intent.createChooser(shareIntent, "Share Shopping List"))
        }

        // Filter button
        binding.shoppingScreenContainer.btnFilter.setOnClickListener {
            showShoppingFilterDialog()
        }
    }

    private fun setupClickListeners() {
        viewModel.fetchAlerts()

        val btnSync = binding.notificationsScreenContainer.findViewById<View>(R.id.btnSync)
        btnSync?.setOnClickListener {
            viewModel.fetchAlerts()
        }

        val btnBack = binding.notificationsScreenContainer.findViewById<View>(R.id.btnBack)
        btnBack?.setOnClickListener {
            binding.notificationsScreenContainer.visibility = View.GONE
        }

        binding.fabAddAlert.setOnClickListener {
            showAddAlertDialog()
        }

        // Navigation Tabs
        binding.navPantry.setOnClickListener {
            selectTab(NavTab.PANTRY)
        }

        binding.navShopping.setOnClickListener {
            selectTab(NavTab.SHOPPING)
        }

        binding.navSurplus.setOnClickListener {
            selectTab(NavTab.SURPLUS)
        }

        binding.navProfile.setOnClickListener {
            selectTab(NavTab.PROFILE)
        }
    }

    private fun selectTab(tab: NavTab) {
        val primaryColor = ContextCompat.getColor(this, R.color.pantry_primary)
        val secondaryColor = ContextCompat.getColor(this, R.color.text_secondary)

        // Reset all tabs to inactive styling
        binding.ivNavPantry.setColorFilter(secondaryColor)
        binding.tvNavPantry.setTextColor(secondaryColor)
        binding.tvNavPantry.typeface = Typeface.DEFAULT

        binding.ivNavShopping.setColorFilter(secondaryColor)
        binding.tvNavShopping.setTextColor(secondaryColor)
        binding.tvNavShopping.typeface = Typeface.DEFAULT

        binding.ivNavSurplus.setColorFilter(secondaryColor)
        binding.tvNavSurplus.setTextColor(secondaryColor)
        binding.tvNavSurplus.typeface = Typeface.DEFAULT

        binding.ivNavProfile.setColorFilter(secondaryColor)
        binding.tvNavProfile.setTextColor(secondaryColor)
        binding.tvNavProfile.typeface = Typeface.DEFAULT

        when (tab) {
            NavTab.PANTRY -> {
                if (binding.fullPantryItemsScreenContainer.root.visibility != View.VISIBLE) {
                    binding.pantryHomeScreenContainer.root.visibility = View.VISIBLE
                }
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.notificationsScreenContainer.visibility = View.GONE
                binding.ivNavPantry.setColorFilter(primaryColor)
                binding.tvNavPantry.setTextColor(primaryColor)
                binding.tvNavPantry.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.SHOPPING -> {
                binding.pantryHomeScreenContainer.root.visibility = View.GONE
                binding.fullPantryItemsScreenContainer.root.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.VISIBLE
                binding.notificationsScreenContainer.visibility = View.GONE
                binding.ivNavShopping.setColorFilter(primaryColor)
                binding.tvNavShopping.setTextColor(primaryColor)
                binding.tvNavShopping.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.SURPLUS -> {
                Toast.makeText(this, "Surplus tab selected", Toast.LENGTH_SHORT).show()
                binding.fullPantryItemsScreenContainer.root.visibility = View.GONE
                binding.notificationsScreenContainer.visibility = View.GONE
                binding.ivNavSurplus.setColorFilter(primaryColor)
                binding.tvNavSurplus.setTextColor(primaryColor)
                binding.tvNavSurplus.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.PROFILE -> {
                Toast.makeText(this, "Profile tab selected", Toast.LENGTH_SHORT).show()
                binding.fullPantryItemsScreenContainer.root.visibility = View.GONE
                binding.notificationsScreenContainer.visibility = View.GONE
                binding.ivNavProfile.setColorFilter(primaryColor)
                binding.tvNavProfile.setTextColor(primaryColor)
                binding.tvNavProfile.typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Home Dashboard Observations
                launch {
                    pantryViewModel.allItems.collect { items ->
                        binding.pantryHomeScreenContainer.btnViewAllPantry.text = getString(R.string.btn_view_all_items)
                        binding.fullPantryItemsScreenContainer.tvFullTotalItemsCount.text = items.size.toString()

                        val restockCount = items.count { it.stockPercent <= 25 }
                        binding.fullPantryItemsScreenContainer.tvFullToRestockCount.text = restockCount.toString()
                    }
                }

                launch {
                    pantryViewModel.expiringSoonItems.collect { items ->
                        expiringSoonAdapter.submitList(items)
                        fullExpiringSoonAdapter.submitList(items)

                        binding.fullPantryItemsScreenContainer.tvFullExpiringSoonCount.text = items.size.toString()

                        if (items.isEmpty()) {
                            binding.pantryHomeScreenContainer.tvNoExpiringItems.visibility = View.VISIBLE
                            binding.pantryHomeScreenContainer.rvExpiringSoon.visibility = View.GONE
                            binding.fullPantryItemsScreenContainer.tvNoFullExpiringItems.visibility = View.VISIBLE
                            binding.fullPantryItemsScreenContainer.rvExpiringSoonFull.visibility = View.GONE
                        } else {
                            binding.pantryHomeScreenContainer.tvNoExpiringItems.visibility = View.GONE
                            binding.pantryHomeScreenContainer.rvExpiringSoon.visibility = View.VISIBLE
                            binding.fullPantryItemsScreenContainer.tvNoFullExpiringItems.visibility = View.GONE
                            binding.fullPantryItemsScreenContainer.rvExpiringSoonFull.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    pantryViewModel.categories.collect { cats ->
                        categoryChipHorizontalAdapter.submitList(cats)
                        binding.fullPantryItemsScreenContainer.tvFullCategoriesCount.text =
                            getString(R.string.categories_count_format, cats.size)
                    }
                }

                launch {
                    pantryViewModel.selectedCategory.collect { selCat ->
                        categoryChipHorizontalAdapter.setSelectedCategory(selCat)
                    }
                }

                launch {
                    pantryViewModel.filteredPantryItems.collect { items ->
                        pantryOverviewAdapter.submitList(items.take(4))
                        fullPantryOverviewAdapter.submitList(items)

                        if (items.isEmpty()) {
                            binding.pantryHomeScreenContainer.tvNoItemsFound.visibility = View.VISIBLE
                            binding.pantryHomeScreenContainer.rvAllPantryItems.visibility = View.GONE
                            binding.fullPantryItemsScreenContainer.tvNoFullPantryItems.visibility = View.VISIBLE
                            binding.fullPantryItemsScreenContainer.rvFullPantryItems.visibility = View.GONE
                        } else {
                            binding.pantryHomeScreenContainer.tvNoItemsFound.visibility = View.GONE
                            binding.pantryHomeScreenContainer.rvAllPantryItems.visibility = View.VISIBLE
                            binding.fullPantryItemsScreenContainer.tvNoFullPantryItems.visibility = View.GONE
                            binding.fullPantryItemsScreenContainer.rvFullPantryItems.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    pantryViewModel.uiMessage.collect { msg ->
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                }

                // Notifications Observations
                launch {
                    viewModel.alerts.collect { alertsDto ->
                        val items = mutableListOf<NotificationItem>()

                        items.add(NotificationItem.SectionHeader(id = "sec_today", title = "ALERTS", itemCountText = "${alertsDto.size} items"))

                        alertsDto.forEach { dto ->
                            items.add(
                                NotificationItem.ExpiryAlert(
                                    id = dto.id?.toString() ?: "",
                                    title = dto.message,
                                    categoryAndLocation = "Pantry",
                                    expiryText = "Expiring soon",
                                    urgency = if (dto.type == "EXPIRING_TODAY") AlertUrgency.EXPIRING_TODAY else AlertUrgency.EXPIRING_SOON,
                                    imageResId = R.drawable.ic_food_yogurt,
                                    primaryButtonType = if (dto.type == "EXPIRING_TODAY") PrimaryButtonType.RECIPE_IDEAS else PrimaryButtonType.ADD_TO_RESTOCK
                                )
                            )
                        }

                        // Submit items to adapter
                        adapter.submitList(items)

                        // Update Chip Counts & Bell Badge
                        binding.pantryHomeScreenContainer.tvBellBadgeCount.text = alertsDto.size.toString()
                        binding.fullPantryItemsScreenContainer.tvFullBellBadgeCount.text = alertsDto.size.toString()

                        val tvChipAllCount = binding.notificationsScreenContainer.findViewById<android.widget.TextView>(R.id.tvChipAllCount)
                        val tvChipExpiryCount = binding.notificationsScreenContainer.findViewById<android.widget.TextView>(R.id.tvChipExpiryCount)
                        tvChipAllCount?.text = alertsDto.size.toString()
                        tvChipExpiryCount?.text = alertsDto.size.toString()
                    }
                }

                launch {
                    viewModel.message.collect { msg ->
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                }

                // Shopping Screen Observations
                launch {
                    shoppingViewModel.filteredToBuyItems.collect { items ->
                        shoppingAdapter.submitList(items)

                        binding.shoppingScreenContainer.tvToBuyTitle.text =
                            getString(R.string.shopping_to_buy_header, items.size)

                        val selectedCount = items.count { it.isSelected }
                        binding.shoppingScreenContainer.tvSelectedCount.text =
                            getString(R.string.shopping_items_selected, selectedCount)

                        if (selectedCount > 0) {
                            binding.shoppingScreenContainer.btnRestockSelected.isEnabled = true
                            binding.shoppingScreenContainer.btnRestockSelected.backgroundTintList =
                                ContextCompat.getColorStateList(this@MainActivity, R.color.shopping_restock_btn_enabled)
                            binding.shoppingScreenContainer.btnRestockSelected.setTextColor(
                                ContextCompat.getColor(this@MainActivity, R.color.shopping_restock_btn_text)
                            )
                        } else {
                            binding.shoppingScreenContainer.btnRestockSelected.isEnabled = false
                            binding.shoppingScreenContainer.btnRestockSelected.backgroundTintList =
                                ContextCompat.getColorStateList(this@MainActivity, R.color.shopping_restock_btn_disabled)
                            binding.shoppingScreenContainer.btnRestockSelected.setTextColor(
                                ContextCompat.getColor(this@MainActivity, R.color.shopping_restock_btn_disabled_text)
                            )
                        }
                    }
                }

                launch {
                    shoppingViewModel.recentlyPurchasedItems.collect { items ->
                        recentlyPurchasedAdapter.submitList(items)
                        if (items.isEmpty()) {
                            binding.shoppingScreenContainer.llRecentlyPurchasedHeader.visibility = View.GONE
                            binding.shoppingScreenContainer.rvRecentlyPurchased.visibility = View.GONE
                        } else {
                            binding.shoppingScreenContainer.llRecentlyPurchasedHeader.visibility = View.VISIBLE
                            binding.shoppingScreenContainer.rvRecentlyPurchased.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    shoppingViewModel.uiMessage.collect { msg ->
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showItemDetailsDialog(item: PantryItem) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = ComposeView(this).apply {
            setContent {
                ItemDetailsDialog(
                    item = item,
                    onDismiss = { dialog.dismiss() },
                    onMarkConsumed = { id ->
                        pantryViewModel.deleteItem(id)
                        Toast.makeText(this@MainActivity, "Marked as consumed", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    },
                    onAddToRestock = { _ ->
                        shoppingViewModel.addItem(item.title)
                        Toast.makeText(this@MainActivity, "Added to Restock List", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    },
                    onDonate = { _ ->
                        Toast.makeText(this@MainActivity, "Item marked for donation", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    },
                    onEdit = { _ ->
                        Toast.makeText(this@MainActivity, "Editing item details", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    },
                    onDelete = { id ->
                        pantryViewModel.deleteItem(id)
                        Toast.makeText(this@MainActivity, "Item deleted", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                )
            }
        }
        dialog.setContentView(composeView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showPantryFilterSortDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogShoppingFilterBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.rbFilterAll.text = "All Items"
        dialogBinding.rbFilterAutoQueued.text = "Low Stock (<= 25%)"
        dialogBinding.rbFilterManual.text = "Expiring Soon (<= 3 days)"
        dialogBinding.rbFilterLowStock.text = "Full Stock (100%)"
        dialogBinding.rbFilterExpired.visibility = View.GONE

        dialogBinding.btnCancelFilter.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnApplyFilter.setOnClickListener {
            when (dialogBinding.rgFilterOptions.checkedRadioButtonId) {
                R.id.rbFilterAutoQueued -> {
                    pantryViewModel.selectCategory(null)
                    pantryViewModel.setSearchQuery("")
                    val lowStockItems = pantryViewModel.allItems.value.filter { it.stockPercent <= 25 }
                    fullPantryOverviewAdapter.submitList(lowStockItems)
                }
                R.id.rbFilterManual -> {
                    pantryViewModel.selectCategory(null)
                    pantryViewModel.setSearchQuery("")
                    fullPantryOverviewAdapter.submitList(pantryViewModel.expiringSoonItems.value)
                }
                R.id.rbFilterLowStock -> {
                    pantryViewModel.selectCategory(null)
                    pantryViewModel.setSearchQuery("")
                    val fullStockItems = pantryViewModel.allItems.value.filter { it.stockPercent == 100 }
                    fullPantryOverviewAdapter.submitList(fullStockItems)
                }
                else -> {
                    pantryViewModel.selectCategory(null)
                    pantryViewModel.setSearchQuery("")
                    fullPantryOverviewAdapter.submitList(pantryViewModel.allItems.value)
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showAddAlertDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogAddAlertBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnAdd.setOnClickListener {
            val title = dialogBinding.etItemTitle.text.toString().trim()
            if (title.isEmpty()) {
                dialogBinding.etItemTitle.error = "Please enter item name"
                return@setOnClickListener
            }

            Toast.makeText(this, "Alert added locally. (Mocked)", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRecipeIdeasDialog(alert: NotificationItem.ExpiryAlert) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogRecipeIdeasBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.tvRecipeTitle.text = "Recipe Ideas for ${alert.title}"

        dialogBinding.btnCloseRecipe.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showShoppingFilterDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogShoppingFilterBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        when (shoppingViewModel.selectedFilter.value) {
            ShoppingFilter.ALL -> dialogBinding.rbFilterAll.isChecked = true
            ShoppingFilter.AUTO_QUEUED -> dialogBinding.rbFilterAutoQueued.isChecked = true
            ShoppingFilter.MANUAL_ENTRY -> dialogBinding.rbFilterManual.isChecked = true
            ShoppingFilter.LOW_STOCK -> dialogBinding.rbFilterLowStock.isChecked = true
            ShoppingFilter.EXPIRED -> dialogBinding.rbFilterExpired.isChecked = true
        }

        dialogBinding.btnCancelFilter.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnApplyFilter.setOnClickListener {
            val selectedFilter = when (dialogBinding.rgFilterOptions.checkedRadioButtonId) {
                R.id.rbFilterAutoQueued -> ShoppingFilter.AUTO_QUEUED
                R.id.rbFilterManual -> ShoppingFilter.MANUAL_ENTRY
                R.id.rbFilterLowStock -> ShoppingFilter.LOW_STOCK
                R.id.rbFilterExpired -> ShoppingFilter.EXPIRED
                else -> ShoppingFilter.ALL
            }
            shoppingViewModel.setFilter(selectedFilter)
            dialog.dismiss()
        }

        dialog.show()
    }
}
