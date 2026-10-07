package com.example.pantrypal

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import com.example.pantrypal.data.model.PrimaryButtonType
import com.example.pantrypal.data.model.ShoppingFilter
import com.example.pantrypal.data.repository.NotificationRepository
import com.example.pantrypal.data.repository.ShoppingRepository
import com.example.pantrypal.data.repository.SurplusRepository
import com.example.pantrypal.databinding.ActivityMainBinding
import com.example.pantrypal.databinding.DialogAddAlertBinding
import com.example.pantrypal.databinding.DialogRecipeIdeasBinding
import com.example.pantrypal.databinding.DialogShoppingFilterBinding
import com.example.pantrypal.ui.notifications.NotificationAdapter
import com.example.pantrypal.ui.notifications.NotificationViewModel
import com.example.pantrypal.ui.notifications.NotificationViewModelFactory
import com.example.pantrypal.ui.shopping.RecentlyPurchasedAdapter
import com.example.pantrypal.ui.shopping.ShoppingAdapter
import com.example.pantrypal.ui.shopping.ShoppingViewModel
import com.example.pantrypal.ui.shopping.ShoppingViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
class MainActivity : AppCompatActivity() {

    private enum class NavTab {
        PANTRY, SHOPPING, SURPLUS, PROFILE
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: NotificationViewModel
    private lateinit var shoppingViewModel: ShoppingViewModel

    private lateinit var adapter: NotificationAdapter
    private lateinit var shoppingAdapter: ShoppingAdapter
    private lateinit var recentlyPurchasedAdapter: RecentlyPurchasedAdapter
    private var selectedHubName = "Colombo Community Kitchen"
    private var selectedHubId: String? = null
    private val surplusRepository = SurplusRepository()
    private lateinit var foodSafetyChecks: List<android.widget.CheckBox>
    private var availableSurplusHubs: List<com.example.pantrypal.data.model.SurplusHubDto> = emptyList()
    private var hubSearchWatcher: android.text.TextWatcher? = null

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
        setupRecyclerView()
        setupShoppingUI()
        setupClickListeners()
        setupSurplusSafetyChecklist()
        observeUiState()
    }

    private fun setupArchitecture() {
        val repository = NotificationRepository()
        val factory = NotificationViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[NotificationViewModel::class.java]

        val shoppingRepository = ShoppingRepository()
        val shoppingFactory = ShoppingViewModelFactory(shoppingRepository)
        shoppingViewModel = ViewModelProvider(this, shoppingFactory)[ShoppingViewModel::class.java]
    }

    @OptIn(InternalSerializationApi::class)
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

        binding.pantryScreenContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications).layoutManager = LinearLayoutManager(this)
        binding.pantryScreenContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications).adapter = adapter
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

        val btnSync = binding.pantryScreenContainer.findViewById<View>(R.id.btnSync)
        btnSync?.setOnClickListener {
            viewModel.fetchAlerts()
        }

        val btnBack = binding.pantryScreenContainer.findViewById<View>(R.id.btnBack)
        btnBack?.setOnClickListener {
            Toast.makeText(this, "Back pressed", Toast.LENGTH_SHORT).show()
        }

        val btnBell = binding.pantryScreenContainer.findViewById<View>(R.id.btnBell)
        btnBell?.setOnClickListener {
            Toast.makeText(this, "Notifications menu", Toast.LENGTH_SHORT).show()
        }

        val btnProfileAvatar = binding.pantryScreenContainer.findViewById<View>(R.id.btnProfileAvatar)
        btnProfileAvatar?.setOnClickListener {
            Toast.makeText(this, "Opening Profile", Toast.LENGTH_SHORT).show()
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

        binding.surplusScreenContainer.findViewById<View>(R.id.btnFlagSurplus).setOnClickListener {
            showSurplusDetail("flag")
        }
        binding.surplusScreenContainer.findViewById<View>(R.id.btnFindHub).setOnClickListener {
            showSurplusDetail("hubs")
        }
        binding.surplusScreenContainer.findViewById<View>(R.id.btnCommunityImpact).setOnClickListener { showSurplusDetail("impact") }
        findViewById<View>(R.id.btnSurplusBack).setOnClickListener {
            binding.surplusDetailContainer.visibility = View.GONE
            binding.surplusScreenContainer.visibility = View.VISIBLE
        }
        findViewById<View>(R.id.btnChooseHub).setOnClickListener { showSurplusDetail("hubs") }
        findViewById<View>(R.id.btnDirections).setOnClickListener {
            val route = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(selectedHubName)}")
            )
            try {
                startActivity(route)
            } catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "No maps app is available for directions.", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.btnSubmitDonation).setOnClickListener { submitSurplusDonation() }
        findViewById<View>(R.id.btnTrackDonation).setOnClickListener { showSurplusDetail("impact") }
        findViewById<View>(R.id.btnAnotherDonation).setOnClickListener { showSurplusDetail("flag") }
        findViewById<View>(R.id.btnShareImpact).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "I've helped share 14.2 kg of food with my community through PantryPal!")
            }
            startActivity(Intent.createChooser(share, "Share my impact"))
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
                binding.pantryScreenContainer.visibility = View.VISIBLE
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.surplusScreenContainer.visibility = View.GONE
                binding.surplusDetailContainer.visibility = View.GONE
                binding.ivNavPantry.setColorFilter(primaryColor)
                binding.tvNavPantry.setTextColor(primaryColor)
                binding.tvNavPantry.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.SHOPPING -> {
                binding.pantryScreenContainer.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.VISIBLE
                binding.surplusScreenContainer.visibility = View.GONE
                binding.surplusDetailContainer.visibility = View.GONE
                binding.ivNavShopping.setColorFilter(primaryColor)
                binding.tvNavShopping.setTextColor(primaryColor)
                binding.tvNavShopping.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.SURPLUS -> {
                binding.pantryScreenContainer.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.surplusScreenContainer.visibility = View.VISIBLE
                binding.surplusDetailContainer.visibility = View.GONE
                binding.ivNavSurplus.setColorFilter(primaryColor)
                binding.tvNavSurplus.setTextColor(primaryColor)
                binding.tvNavSurplus.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.PROFILE -> {
                binding.pantryScreenContainer.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.surplusScreenContainer.visibility = View.GONE
                binding.surplusDetailContainer.visibility = View.GONE
                Toast.makeText(this, "Profile tab selected", Toast.LENGTH_SHORT).show()
                binding.ivNavProfile.setColorFilter(primaryColor)
                binding.tvNavProfile.setTextColor(primaryColor)
                binding.tvNavProfile.typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun showSurplusDetail(page: String) {
        binding.pantryScreenContainer.visibility = View.GONE
        binding.shoppingScreenContainer.root.visibility = View.GONE
        binding.surplusScreenContainer.visibility = View.GONE
        binding.surplusDetailContainer.visibility = View.VISIBLE
        val impact = findViewById<View>(R.id.layoutImpact)
        val hubs = findViewById<View>(R.id.layoutHubFinder)
        val form = findViewById<View>(R.id.layoutFlagForm)
        val submitted = findViewById<View>(R.id.layoutSubmission)
        impact.visibility = if (page == "impact") View.VISIBLE else View.GONE
        hubs.visibility = if (page == "hubs") View.VISIBLE else View.GONE
        form.visibility = if (page == "flag") View.VISIBLE else View.GONE
        submitted.visibility = if (page == "submitted") View.VISIBLE else View.GONE
        val title = when (page) {
            "hubs" -> "Find a Hub"
            "flag" -> "Flag Surplus"
            "submitted" -> "Donation submitted"
            else -> "Community Impact"
        }
        findViewById<android.widget.TextView>(R.id.tvDetailTitle).text = title
        if (page == "hubs") loadSurplusHubs()
        if (page == "impact") loadDonationImpact()
    }

    @OptIn(InternalSerializationApi::class)
    private fun loadSurplusHubs() {
        lifecycleScope.launch {
            try {
                availableSurplusHubs = surplusRepository.getHubs()
                if (availableSurplusHubs.isEmpty()) {
                    addHubMessage("No hubs are available yet. Add active hubs in Supabase.")
                    return@launch
                }
                renderSurplusHubs(availableSurplusHubs)
                val search = findViewById<android.widget.EditText>(R.id.etHubSearch)
                hubSearchWatcher?.let(search::removeTextChangedListener)
                hubSearchWatcher = object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s?.toString()?.trim().orEmpty()
                        renderSurplusHubs(availableSurplusHubs.filter {
                            it.name.contains(query, ignoreCase = true) || it.address.contains(query, ignoreCase = true)
                        })
                    }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                }
                search.addTextChangedListener(hubSearchWatcher)
            } catch (error: Exception) {
                addHubMessage("Could not load hubs from Supabase: ${error.message ?: "check connection and table policies"}")
            }
        }
    }

    private fun renderSurplusHubs(hubs: List<com.example.pantrypal.data.model.SurplusHubDto>) {
        val list = findViewById<android.widget.LinearLayout>(R.id.hubListContainer)
        list.removeAllViews()
        findViewById<android.widget.TextView>(R.id.tvHubResultCount).text = "Nearby hubs · ${hubs.size} active"
        if (hubs.isEmpty()) {
            addHubMessage("No hubs match your search.")
            return
        }
        hubs.forEach { hub ->
                    val card = com.google.android.material.card.MaterialCardView(this@MainActivity).apply {
                        radius = 18f
                        cardElevation = 1f
                        setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_card_bg))
                        val content = android.widget.LinearLayout(this@MainActivity).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(18, 16, 18, 14)
                            addView(android.widget.TextView(this@MainActivity).apply {
                                text = hub.name
                                textSize = 15f
                                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                            })
                            addView(android.widget.TextView(this@MainActivity).apply {
                                text = "${hub.distanceKm} km · Open until ${hub.openUntil}\nAccepts: ${hub.acceptedFoods}\n${hub.address}"
                                textSize = 12f
                                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                                setPadding(0, 8, 0, 10)
                            })
                            addView(android.widget.TextView(this@MainActivity).apply {
                                text = if (selectedHubId == hub.id) "Selected ✓" else "Select this hub"
                                textSize = 13f
                                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary))
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                gravity = android.view.Gravity.END
                            })
                        }
                        addView(content)
                        setOnClickListener {
                            selectedHubId = hub.id
                            selectedHubName = hub.name
                            Toast.makeText(this@MainActivity, "$selectedHubName selected", Toast.LENGTH_SHORT).show()
                            showSurplusDetail("flag")
                        }
                    }
                    val params = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
                    list.addView(card, params)
        }
    }

    private fun addHubMessage(message: String) {
        findViewById<android.widget.LinearLayout>(R.id.hubListContainer).apply {
            removeAllViews()
            addView(android.widget.TextView(this@MainActivity).apply {
                text = message
                textSize = 13f
                setPadding(10, 16, 10, 16)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            })
        }
    }

    @OptIn(InternalSerializationApi::class)
    private fun loadDonationImpact() {
        if (surplusRepository.currentDonorId().isNullOrBlank()) {
            findViewById<android.widget.TextView>(R.id.tvImpactHeadline).text = "Sign in to view your donation history"
            return
        }
        lifecycleScope.launch {
            try {
                val impact = surplusRepository.getImpact()
                findViewById<android.widget.TextView>(R.id.tvImpactFood).text = "${"%.1f".format(impact.foodSavedKg)}kg"
                findViewById<android.widget.TextView>(R.id.tvImpactMeals).text = impact.meals.toString()
                findViewById<android.widget.TextView>(R.id.tvImpactHeadline).text = "${impact.donationCount} donations · ${impact.completedCount} completed"
                findViewById<android.widget.TextView>(R.id.tvFoodSaved).text = "${"%.1f".format(impact.foodSavedKg)} kg"
                findViewById<android.widget.TextView>(R.id.tvDonations).text = impact.donationCount.toString()
                val history = surplusRepository.getDonations()
                if (history.isNotEmpty()) {
                    val latest = history.first()
                    findViewById<android.widget.TextView>(R.id.tvLatestDonation).text = "${latest.foodName} · ${latest.quantity} · ${latest.status}"
                    renderDonationHistory(history)
                }
            } catch (error: Exception) {
                findViewById<android.widget.TextView>(R.id.tvImpactHeadline).text = "Could not load impact: ${error.message ?: "check Supabase setup"}"
            }
        }
    }

    private fun renderDonationHistory(history: List<com.example.pantrypal.data.repository.DonationHistoryDto>) {
        val impactLayout = findViewById<android.widget.LinearLayout>(R.id.layoutImpact)
        for (index in impactLayout.childCount - 1 downTo 0) {
            if (impactLayout.getChildAt(index).tag == "surplus-history-item") {
                impactLayout.removeViewAt(index)
            }
        }
        val anchor = findViewById<View>(R.id.tvLatestDonation).parent.parent as View
        var insertIndex = impactLayout.indexOfChild(anchor) + 1
        history.forEach { donation ->
            val date = donation.createdAt?.take(10)?.takeIf { it.isNotBlank() } ?: "Recent donation"
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                tag = "surplus-history-item"
                radius = 18f
                cardElevation = 1f
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_card_bg))
                val text = android.widget.TextView(this@MainActivity).apply {
                    this.text = "🥬  ${donation.foodName} · ${donation.quantity}\n${donation.status.replace('_', ' ')} · $date"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                    setPadding(18, 15, 18, 15)
                }
                addView(text)
            }
            impactLayout.addView(card, insertIndex++, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 8
            })
        }
    }

    private fun setupSurplusSafetyChecklist() {
        val form = findViewById<android.widget.LinearLayout>(R.id.layoutFlagForm)
        val submitButton = findViewById<View>(R.id.btnSubmitDonation)
        val checklist = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
        }
        val safetyItems = listOf(
            "Food is not expired and is safe to eat",
            "Food is properly stored and sealed",
            "Donation meets the 2 kg minimum",
            "I understand this supports community impact"
        )
        foodSafetyChecks = safetyItems.map { label ->
            android.widget.CheckBox(this).apply {
                text = label
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.pantry_primary)
                )
                isChecked = false
            }
        }
        foodSafetyChecks.forEach(checklist::addView)
        val insertAt = form.indexOfChild(submitButton).coerceAtLeast(0)
        form.addView(checklist, insertAt, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 12
        })
    }

    @OptIn(InternalSerializationApi::class)
    private fun submitSurplusDonation() {
        val name = findViewById<android.widget.EditText>(R.id.etSurplusName).text.toString().trim()
        val quantity = findViewById<android.widget.EditText>(R.id.etSurplusQuantity).text.toString().trim()
        val expiry = findViewById<android.widget.EditText>(R.id.etSurplusExpiry).text.toString().trim()
        if (name.isBlank() || quantity.isBlank() || expiry.isBlank()) {
            Toast.makeText(this, "Complete the food, quantity and best before fields", Toast.LENGTH_SHORT).show()
            return
        }
        val authUserId = surplusRepository.currentDonorId()
        val hubId = selectedHubId
        if (authUserId.isNullOrBlank()) {
            Toast.makeText(this, "Sign in first to submit a donation.", Toast.LENGTH_LONG).show()
            return
        }
        if (hubId.isNullOrBlank()) {
            Toast.makeText(this, "Select a hub before submitting.", Toast.LENGTH_SHORT).show()
            showSurplusDetail("hubs")
            return
        }
        val kilograms = parseKilograms(quantity)
        if (kilograms == null || kilograms < 2.0) {
            Toast.makeText(this, "Surplus donations must be at least 2 kg.", Toast.LENGTH_LONG).show()
            return
        }
        if (foodSafetyChecks.any { !it.isChecked }) {
            Toast.makeText(this, "Confirm every food safety item before submitting.", Toast.LENGTH_LONG).show()
            return
        }
        val submitButton = findViewById<View>(R.id.btnSubmitDonation)
        submitButton.isEnabled = false
        lifecycleScope.launch {
            try {
                surplusRepository.createDonation(
                    com.example.pantrypal.data.model.SurplusDonationDto(
                        hubId = hubId,
                        foodName = name,
                        quantity = quantity,
                        bestBefore = expiry,
                        pickupWindow = "Today, 2:00 PM – 5:00 PM"
                    )
                )
                findViewById<android.widget.TextView>(R.id.tvSubmittedFood).text = "$name · $quantity"
                findViewById<android.widget.TextView>(R.id.tvSubmittedHub).text = selectedHubName
                findViewById<android.widget.TextView>(R.id.tvSubmissionSummary).text = "Your donation is listed with $selectedHubName. Pantry courier pickup: today, 2:00 PM – 5:00 PM."
                findViewById<android.widget.TextView>(R.id.tvLatestDonation).text = "🥕  $name · $quantity"
                showSurplusDetail("submitted")
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, "Donation could not be saved: ${error.message ?: "check Supabase"}", Toast.LENGTH_LONG).show()
            } finally {
                submitButton.isEnabled = true
            }
        }
    }

    private fun parseKilograms(quantity: String): Double? {
        val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(kg|kgs|g|grams?)?", RegexOption.IGNORE_CASE).find(quantity.trim())
            ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "g", "gram", "grams" -> value / 1000.0
            else -> value
        }
    }

    @OptIn(InternalSerializationApi::class)
    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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

                        // Update Chip Counts
                        val tvChipAllCount = binding.pantryScreenContainer.findViewById<android.widget.TextView>(R.id.tvChipAllCount)
                        val tvChipExpiryCount = binding.pantryScreenContainer.findViewById<android.widget.TextView>(R.id.tvChipExpiryCount)
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
            val category = dialogBinding.etCategoryLocation.text.toString().trim()
            val expiry = dialogBinding.etExpiryText.text.toString().trim()

            if (title.isEmpty()) {
                dialogBinding.etItemTitle.error = "Please enter item name"
                return@setOnClickListener
            }

            val urgency = if (dialogBinding.rbExpiringToday.isChecked) {
                AlertUrgency.EXPIRING_TODAY
            } else {
                AlertUrgency.EXPIRING_SOON
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
