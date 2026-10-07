package com.example.pantrypal

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
    private var selectedSurplusPhotoUri: Uri? = null
    private val surplusPhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedSurplusPhotoUri = uri
            findViewById<android.widget.ImageView>(R.id.ivSurplusPhoto).apply {
                setImageURI(uri)
                visibility = View.VISIBLE
            }
            findViewById<View>(R.id.tvSurplusPhotoPrompt).visibility = View.GONE
            Toast.makeText(this, "Photo added", Toast.LENGTH_SHORT).show()
        }
    }

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
        findViewById<View>(R.id.btnFlagTab).setOnClickListener { showSurplusDetail("flag") }
        findViewById<View>(R.id.btnFindHubTab).setOnClickListener { showSurplusDetail("hubs") }
        findViewById<View>(R.id.btnDirections).setOnClickListener {
            openSelectedHubDirections()
        }
        findViewById<View>(R.id.tvHubMapPreview).setOnClickListener { openSelectedHubDirections() }
        findViewById<View>(R.id.btnSubmitDonation).setOnClickListener { submitSurplusDonation() }
        findViewById<View>(R.id.btnAddSurplusPhoto).setOnClickListener { surplusPhotoPicker.launch("image/*") }
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

    private fun openSelectedHubDirections() {
        val route = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(selectedHubName)}")
        )
        try {
            startActivity(route)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No maps app is available for directions.", Toast.LENGTH_SHORT).show()
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
        val tabs = findViewById<View>(R.id.surplusDetailTabs)
        impact.visibility = if (page == "impact") View.VISIBLE else View.GONE
        hubs.visibility = if (page == "hubs") View.VISIBLE else View.GONE
        form.visibility = if (page == "flag") View.VISIBLE else View.GONE
        submitted.visibility = if (page == "submitted") View.VISIBLE else View.GONE
        tabs.visibility = if (page == "flag" || page == "hubs") View.VISIBLE else View.GONE
        val title = when (page) {
            "submitted" -> "Surplus Feed"
            "impact" -> "Community Impact"
            else -> "Surplus"
        }
        val header = findViewById<android.widget.LinearLayout>(R.id.surplusDetailHeader)
        val titleView = findViewById<android.widget.TextView>(R.id.tvDetailTitle)
        val backButton = findViewById<android.widget.TextView>(R.id.btnSurplusBack)
        val headerAction = findViewById<android.widget.TextView>(R.id.btnSurplusHeaderAction)
        titleView.text = title
        backButton.visibility = if (page == "flag" || page == "submitted") View.INVISIBLE else View.VISIBLE
        headerAction.text = if (page == "impact") "▣" else "♙"
        val darkHeader = page == "impact"
        header.setBackgroundColor(if (darkHeader) android.graphics.Color.rgb(37, 94, 81) else android.graphics.Color.TRANSPARENT)
        titleView.setTextColor(ContextCompat.getColor(this, if (darkHeader) R.color.white else R.color.text_primary))
        backButton.setTextColor(ContextCompat.getColor(this, if (darkHeader) R.color.white else R.color.text_primary))
        headerAction.setTextColor(ContextCompat.getColor(this, if (darkHeader) R.color.white else R.color.pantry_primary))
        binding.surplusDetailContainer.setBackgroundColor(
            ContextCompat.getColor(this, if (page == "impact" || page == "submitted") R.color.pantry_primary_light else R.color.pantry_bg)
        )
        val activeTint = ContextCompat.getColor(this, R.color.pantry_primary)
        val inactiveTint = android.graphics.Color.TRANSPARENT
        val flagTab = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFlagTab)
        val hubTab = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFindHubTab)
        flagTab.backgroundTintList = android.content.res.ColorStateList.valueOf(if (page == "flag") activeTint else inactiveTint)
        hubTab.backgroundTintList = android.content.res.ColorStateList.valueOf(if (page == "hubs") activeTint else inactiveTint)
        flagTab.setTextColor(ContextCompat.getColor(this, if (page == "flag") R.color.white else R.color.text_primary))
        hubTab.setTextColor(ContextCompat.getColor(this, if (page == "hubs") R.color.white else R.color.text_primary))
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
            val selected = selectedHubId == hub.id
            val card = com.google.android.material.card.MaterialCardView(this@MainActivity).apply {
                radius = 17f
                cardElevation = 1f
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
                strokeColor = ContextCompat.getColor(this@MainActivity, if (selected) R.color.pantry_primary else R.color.pantry_primary_light)
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_card_bg))
                val row = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val stripe = View(this@MainActivity).apply {
                        setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary))
                    }
                    addView(stripe, android.widget.LinearLayout.LayoutParams((3 * resources.displayMetrics.density).toInt(), -1))
                    val content = android.widget.LinearLayout(this@MainActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(12, 10, 12, 10)
                        val titleRow = android.widget.LinearLayout(this@MainActivity).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            addView(android.widget.TextView(this@MainActivity).apply {
                                text = hub.name
                                textSize = 13f
                                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                            }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                            addView(android.widget.TextView(this@MainActivity).apply {
                                text = if (selected) "✓" else "●"
                                textSize = 14f
                                setTextColor(ContextCompat.getColor(this@MainActivity, if (selected) R.color.pantry_primary else R.color.pantry_primary_light))
                            })
                        }
                        addView(titleRow)
                        addView(android.widget.TextView(this@MainActivity).apply {
                            text = "⌖ ${"%.1f".format(hub.distanceKm)} km away   ·   ◷ Open until ${hub.openUntil}"
                            textSize = 9f
                            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                            setPadding(0, 2, 0, 5)
                        })
                        val footer = android.widget.LinearLayout(this@MainActivity).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            addView(android.widget.TextView(this@MainActivity).apply {
                                text = "✓ Accepts: ${hub.acceptedFoods}"
                                textSize = 8f
                                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary_dark))
                                setPadding(8, 5, 8, 5)
                                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                            }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                            addView(android.widget.TextView(this@MainActivity).apply {
                                text = "● Open Now"
                                textSize = 8f
                                setTypeface(typeface, android.graphics.Typeface.BOLD)
                                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary_dark))
                                setPadding(8, 5, 8, 5)
                                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                            })
                        }
                        addView(footer)
                    }
                    addView(content, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                }
                addView(row)
                setOnClickListener {
                    selectedHubId = hub.id
                    selectedHubName = hub.name
                    Toast.makeText(this@MainActivity, "$selectedHubName selected", Toast.LENGTH_SHORT).show()
                    showSurplusDetail("flag")
                }
            }
            val params = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
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
            findViewById<android.widget.TextView>(R.id.tvImpactHeadline).text = "Active since March 2024"
            return
        }
        lifecycleScope.launch {
            try {
                val impact = surplusRepository.getImpact()
                findViewById<android.widget.TextView>(R.id.tvImpactFood).text = "${"%.1f".format(impact.foodSavedKg)}kg"
                findViewById<android.widget.TextView>(R.id.tvImpactMeals).text = impact.meals.toString()
                findViewById<android.widget.TextView>(R.id.tvImpactCo2).text = "${"%.0f".format(impact.co2SavedKg)}kg"
                findViewById<android.widget.TextView>(R.id.tvImpactHeadline).text = "Active since March 2024 · ${impact.donationCount} donations"
                findViewById<android.widget.TextView>(R.id.tvFoodSaved).text = "${"%.1f".format(impact.foodSavedKg)} kg"
                findViewById<android.widget.TextView>(R.id.tvDonations).text = impact.donationCount.toString()
                val history = surplusRepository.getDonations()
                if (history.isNotEmpty()) {
                    val latest = history.first()
                    findViewById<android.widget.TextView>(R.id.tvLatestDonation).text = "${latest.foodName} · ${latest.quantity} · ${latest.status}"
                    findViewById<android.widget.TextView>(R.id.tvImpactActiveFood).text = "${latest.foodName} · ${latest.quantity}"
                    val progress = findViewById<android.widget.TextView>(R.id.tvImpactProgress)
                    val state = latest.status.uppercase()
                    progress.text = when (state) {
                        "PICKED_UP" -> "●──────●──────●\nFlagged          Claimed          Picked up"
                        "CLAIMED" -> "●──────●──────○\nFlagged          Claimed          Pickup next"
                        "CANCELLED" -> "Donation cancelled · You can list another item"
                        else -> "●──────○──────○\nFlagged          Claim pending          Pickup next"
                    }
                    findViewById<android.widget.TextView>(R.id.tvImpactClaim).text = when (state) {
                        "CLAIMED" -> "A community hub claimed this donation · Pickup window: ${latest.pickupWindow}"
                        "PICKED_UP" -> "Donation picked up · Your food helped a nearby community"
                        "CANCELLED" -> "This donation was cancelled. Add another donation to continue helping."
                        else -> "Waiting for a nearby hub to claim this donation · ${latest.pickupWindow}"
                    }
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
            setPadding(0, 4, 0, 2)
        }
        val safetyItems = listOf(
            "Not expired\nHarvested within a safe consumption window",
            "Properly stored & sealed\nFood-grade container, good quality produce",
            "Meets 2 kg courier minimum\nEligible for free green courier pickup",
            "Community impact\n~5.7 kg CO₂ prevented · 4 meals"
        )
        foodSafetyChecks = safetyItems.map { label ->
            android.widget.CheckBox(this).apply {
                text = label
                textSize = 10f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                setPadding((10 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt(), 0)
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this@MainActivity, R.color.pantry_primary)
                )
                isChecked = false
            }
        }
        foodSafetyChecks.forEach { checkbox ->
            checklist.addView(checkbox, android.widget.LinearLayout.LayoutParams(-1, (42 * resources.displayMetrics.density).toInt()).apply {
                bottomMargin = (4 * resources.displayMetrics.density).toInt()
            })
        }
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
        val pickupWindow = if (findViewById<android.widget.RadioButton>(R.id.rbSurplusDropoff).isChecked) {
            "Drop off at $selectedHubName"
        } else {
            "Today, 2:00 PM – 5:00 PM"
        }
        submitButton.isEnabled = false
        lifecycleScope.launch {
            try {
                surplusRepository.createDonation(
                    com.example.pantrypal.data.model.SurplusDonationDto(
                        hubId = hubId,
                        foodName = name,
                        quantity = quantity,
                        bestBefore = expiry,
                        pickupWindow = pickupWindow
                    )
                )
                findViewById<android.widget.TextView>(R.id.tvSubmittedFood).text = "$name · $quantity"
                findViewById<android.widget.TextView>(R.id.tvSubmittedWeight).text = quantity
                findViewById<android.widget.TextView>(R.id.tvSubmittedWindow).text = pickupWindow
                findViewById<android.widget.TextView>(R.id.tvSubmittedStatus).text = if (pickupWindow.startsWith("Drop off")) "Hub drop off" else "Queued for pickup"
                findViewById<android.widget.TextView>(R.id.tvSubmissionSummary).text = "$name · $quantity has been added to your pickup queue."
                findViewById<android.widget.TextView>(R.id.tvSubmissionCo2).text = "♻  Est. ${"%.1f".format((kilograms ?: 0.0) * 2.9)} kg CO₂ emissions prevented"
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
