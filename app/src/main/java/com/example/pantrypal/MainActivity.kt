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
import com.example.pantrypal.data.remote.SupabaseProvider
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
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
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
    private var impactHubNames: Map<String, String> = emptyMap()
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
        setupSurplusDatePicker()
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
        findViewById<View>(R.id.btnTestAuthSignIn).setOnClickListener { signInForSurplusTest() }
        findViewById<View>(R.id.btnTestAuthSignOut).setOnClickListener { signOutFromSurplusTest() }
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
                findViewById<View>(R.id.profileScreenContainer).visibility = View.GONE
                binding.ivNavPantry.setColorFilter(primaryColor)
                binding.tvNavPantry.setTextColor(primaryColor)
                binding.tvNavPantry.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.SHOPPING -> {
                binding.pantryScreenContainer.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.VISIBLE
                binding.surplusScreenContainer.visibility = View.GONE
                binding.surplusDetailContainer.visibility = View.GONE
                findViewById<View>(R.id.profileScreenContainer).visibility = View.GONE
                binding.ivNavShopping.setColorFilter(primaryColor)
                binding.tvNavShopping.setTextColor(primaryColor)
                binding.tvNavShopping.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.SURPLUS -> {
                binding.pantryScreenContainer.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.surplusScreenContainer.visibility = View.VISIBLE
                binding.surplusDetailContainer.visibility = View.GONE
                findViewById<View>(R.id.profileScreenContainer).visibility = View.GONE
                binding.ivNavSurplus.setColorFilter(primaryColor)
                binding.tvNavSurplus.setTextColor(primaryColor)
                binding.tvNavSurplus.typeface = Typeface.DEFAULT_BOLD
                loadDonationImpact()
            }
            NavTab.PROFILE -> {
                binding.pantryScreenContainer.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.surplusScreenContainer.visibility = View.GONE
                binding.surplusDetailContainer.visibility = View.GONE
                findViewById<View>(R.id.profileScreenContainer).visibility = View.VISIBLE
                updateTestAuthProfile()
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
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        hubs.forEach { hub ->
            val selected = selectedHubId == hub.id
            val card = com.google.android.material.card.MaterialCardView(this@MainActivity).apply {
                radius = dp(16).toFloat()
                cardElevation = dp(2).toFloat()
                strokeWidth = dp(if (selected) 2 else 1)
                strokeColor = ContextCompat.getColor(this@MainActivity, if (selected) R.color.pantry_primary else R.color.pantry_primary_light)
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_card_bg))
                val content = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                }
                val titleRow = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val icon = android.widget.TextView(this@MainActivity).apply {
                    text = "⌂"
                    textSize = 17f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary))
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                }
                titleRow.addView(icon, android.widget.LinearLayout.LayoutParams(dp(34), dp(34)))
                val titleBlock = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dp(9), 0, 0, 0)
                }
                titleBlock.addView(android.widget.TextView(this@MainActivity).apply {
                    text = hub.name
                    textSize = 13f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                titleBlock.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "Community food hub"
                    textSize = 9f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                })
                titleRow.addView(titleBlock, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                titleRow.addView(android.widget.TextView(this@MainActivity).apply {
                    text = if (selected) "✓" else "•"
                    textSize = 19f
                    setTextColor(ContextCompat.getColor(this@MainActivity, if (selected) R.color.pantry_primary else R.color.text_secondary))
                })
                content.addView(titleRow)

                val metaRow = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(43), dp(5), 0, dp(7))
                }
                metaRow.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "⌖ ${"%.1f".format(hub.distanceKm)} km away"
                    textSize = 9f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                })
                metaRow.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "  ·  Open until ${hub.openUntil}"
                    textSize = 9f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                })
                content.addView(metaRow)

                val footer = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                footer.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "✓  ${hub.acceptedFoods}"
                    textSize = 8f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary_dark))
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                }, android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                footer.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "● Open now"
                    textSize = 8f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary_dark))
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                })
                content.addView(footer)
                addView(content)
                setOnClickListener {
                    selectedHubId = hub.id
                    selectedHubName = hub.name
                    Toast.makeText(this@MainActivity, "$selectedHubName selected", Toast.LENGTH_SHORT).show()
                    showSurplusDetail("flag")
                }
            }
            val params = android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
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
        val rescuedText = findViewById<android.widget.TextView>(R.id.tvImpactFood)
        val mealsText = findViewById<android.widget.TextView>(R.id.tvImpactMeals)
        val co2Text = findViewById<android.widget.TextView>(R.id.tvImpactCo2)
        val headline = findViewById<android.widget.TextView>(R.id.tvImpactHeadline)
        val activeCard = findViewById<View>(R.id.tvImpactActiveFood).parent.parent as View
        val communityCard = findViewById<View>(R.id.tvImpactBeneficiaryTitle).parent.parent.parent as View
        val chart = binding.surplusDetailContainer.findViewById<com.example.pantrypal.ui.surplus.SurplusImpactChart>(R.id.impactChart)
        if (surplusRepository.currentDonorId().isNullOrBlank()) {
            rescuedText.text = "0.0kg"
            mealsText.text = "0"
            co2Text.text = "0.0kg"
            headline.text = "Sign in to see your donations"
            findViewById<android.widget.TextView>(R.id.tvImpactHubName).text = "Community hub"
            findViewById<android.widget.TextView>(R.id.tvImpactLevel).text = "NEW RESCUER"
            activeCard.visibility = View.GONE
            communityCard.visibility = View.GONE
            impactHubNames = emptyMap()
            renderDonationHistory(emptyList())
            chart?.setDonations(emptyList())
            return
        }
        lifecycleScope.launch {
            try {
                val history = surplusRepository.getDonations()
                impactHubNames = try {
                    surplusRepository.getHubs().associate { it.id to it.name }
                } catch (_: Exception) {
                    emptyMap()
                }
                val rescuedKg = history.filter { it.status.equals("PICKED_UP", true) }.sumOf { donation ->
                    Regex("[0-9]+(?:\\.[0-9]+)?").find(donation.quantity)?.value?.toDoubleOrNull() ?: 0.0
                }
                val meals = (rescuedKg * 8.3).toInt()
                rescuedText.text = "${"%.1f".format(rescuedKg)}kg"
                mealsText.text = meals.toString()
                co2Text.text = "${"%.1f".format(rescuedKg * 2.0)}kg"
                headline.text = "${history.size} donation${if (history.size == 1) "" else "s"} · ${history.count { it.status == "PICKED_UP" }} rescued"
                findViewById<android.widget.TextView>(R.id.tvImpactLevel).text = when {
                    history.size >= 10 -> "LEVEL 3 RESCUER"
                    history.size >= 5 -> "LEVEL 2 RESCUER"
                    history.isNotEmpty() -> "LEVEL 1 RESCUER"
                    else -> "NEW RESCUER"
                }
                findViewById<android.widget.TextView>(R.id.tvFoodSaved).text = "${"%.1f".format(rescuedKg)} kg"
                findViewById<android.widget.TextView>(R.id.tvDonations).text = history.size.toString()
                val latest = history.firstOrNull()
                activeCard.visibility = if (latest == null) View.GONE else View.VISIBLE
                communityCard.visibility = if (latest == null) View.GONE else View.VISIBLE
                if (latest != null) {
                    val hubName = impactHubNames[latest.hubId] ?: "Community hub"
                    findViewById<android.widget.TextView>(R.id.tvImpactHubName).text = hubName
                    findViewById<android.widget.TextView>(R.id.tvImpactBeneficiaryTitle).text = hubName
                    findViewById<android.widget.TextView>(R.id.tvImpactBeneficiaryCaption).text = "${latest.foodName} · ${latest.quantity}"
                    findViewById<android.widget.TextView>(R.id.tvImpactActiveFood).text = "${latest.foodName} · ${latest.quantity}"
                    val state = latest.status.uppercase()
                    findViewById<android.widget.TextView>(R.id.tvImpactProgress).text = when (state) {
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
                } else {
                    findViewById<android.widget.TextView>(R.id.tvImpactHubName).text = "Community hub"
                }
                renderDonationHistory(history)
                chart?.setDonations(history)
            } catch (error: Exception) {
                headline.text = "Could not load your donations: ${error.message ?: "check Supabase connection"}"
                activeCard.visibility = View.GONE
                communityCard.visibility = View.GONE
                renderDonationHistory(emptyList())
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
        anchor.visibility = View.GONE // Hide the old mock donation card; history below is loaded from the signed-in donor.
        var insertIndex = impactLayout.indexOfChild(anchor) + 1
        if (history.isEmpty()) {
            val empty = android.widget.TextView(this).apply {
                tag = "surplus-history-item"
                text = if (surplusRepository.currentDonorId().isNullOrBlank()) "Sign in to view your donation history." else "No donations yet. Add a surplus donation to see it here."
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(dp(14), dp(14), dp(14), dp(14))
            }
            impactLayout.addView(empty, insertIndex, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            return
        }
        history.forEach { donation ->
            val date = donation.createdAt?.take(10)?.takeIf { it.isNotBlank() } ?: "Recent donation"
            val pending = donation.status.equals("PENDING", true)
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                tag = "surplus-history-item"
                radius = dp(17).toFloat()
                cardElevation = dp(1).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_card_bg))
                val body = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(11))
                }
                val titleRow = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                titleRow.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "${donation.foodName} · ${donation.quantity}"
                    textSize = 14f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                titleRow.addView(android.widget.TextView(this@MainActivity).apply {
                    text = donation.status.replace('_', ' ')
                    textSize = 9f
                    setTextColor(ContextCompat.getColor(this@MainActivity, if (pending) R.color.pantry_primary_dark else R.color.text_secondary))
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                })
                body.addView(titleRow)
                body.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "${impactHubNames[donation.hubId] ?: "Community hub"} · Best before ${donation.bestBefore}"
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    setPadding(0, dp(5), 0, 0)
                })
                body.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "Pickup: ${donation.pickupWindow} · $date"
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    setPadding(0, dp(3), 0, 0)
                })
                if (pending) {
                    val actions = android.widget.LinearLayout(this@MainActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.END
                        setPadding(0, dp(7), 0, 0)
                    }
                    actions.addView(android.widget.TextView(this@MainActivity).apply {
                        text = "Edit"
                        textSize = 11f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary))
                        setPadding(dp(12), dp(7), dp(12), dp(7))
                        isClickable = true
                        setOnClickListener { showEditDonationDialog(donation) }
                    })
                    actions.addView(android.widget.TextView(this@MainActivity).apply {
                        text = "Delete"
                        textSize = 11f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.rgb(174, 69, 60))
                        setPadding(dp(12), dp(7), dp(4), dp(7))
                        isClickable = true
                        setOnClickListener { confirmDeleteDonation(donation) }
                    })
                    body.addView(actions)
                }
                addView(body)
            }
            impactLayout.addView(card, insertIndex++, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(7)
            })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showEditDonationDialog(donation: com.example.pantrypal.data.repository.DonationHistoryDto) {
        if (!donation.status.equals("PENDING", true)) return
        val form = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        fun field(hintText: String, value: String, type: Int = android.text.InputType.TYPE_CLASS_TEXT): android.widget.EditText {
            return android.widget.EditText(this).apply {
                hint = hintText
                setText(value)
                inputType = type
                setSingleLine(true)
                textSize = 14f
            }.also { edit ->
                form.addView(edit, android.widget.LinearLayout.LayoutParams(-1, dp(48)))
            }
        }
        val food = field("Food description", donation.foodName)
        val quantity = field("Quantity", donation.quantity)
        val bestBefore = field("Best before", donation.bestBefore)
        val pickup = field("Pickup window", donation.pickupWindow)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit donation")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save changes", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val foodValue = food.text.toString().trim()
                val quantityValue = quantity.text.toString().trim()
                val dateValue = bestBefore.text.toString().trim()
                val pickupValue = pickup.text.toString().trim()
                if (foodValue.isBlank() || quantityValue.isBlank() || dateValue.isBlank() || pickupValue.isBlank()) {
                    Toast.makeText(this, "Please complete all donation fields.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    try {
                        val updated = surplusRepository.updateDonation(donation, foodValue, quantityValue, dateValue, pickupValue)
                        if (updated) Toast.makeText(this@MainActivity, "Donation updated", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(this@MainActivity, "This donation is no longer editable.", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        loadDonationImpact()
                    } catch (error: Exception) {
                        Toast.makeText(this@MainActivity, "Could not update donation: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteDonation(donation: com.example.pantrypal.data.repository.DonationHistoryDto) {
        if (!donation.status.equals("PENDING", true)) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete donation?")
            .setMessage("${donation.foodName} · ${donation.quantity} will be removed from your donation history.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val deleted = surplusRepository.deleteDonation(donation)
                        Toast.makeText(
                            this@MainActivity,
                            if (deleted) "Donation deleted" else "This donation is no longer available to delete.",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadDonationImpact()
                    } catch (error: Exception) {
                        Toast.makeText(this@MainActivity, "Could not delete donation: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
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
        val quantityField = findViewById<android.widget.EditText>(R.id.etSurplusQuantity)
        quantityField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCommunityImpactEstimate(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        updateCommunityImpactEstimate(quantityField.text.toString())
        val insertAt = form.indexOfChild(submitButton).coerceAtLeast(0)
        form.addView(checklist, insertAt, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 12
        })
    }

    private fun updateCommunityImpactEstimate(quantity: String) {
        val kilograms = parseKilograms(quantity)
        val communityCheck = foodSafetyChecks.getOrNull(3) ?: return
        communityCheck.text = if (kilograms == null || kilograms <= 0.0) {
            "Community impact\nEnter a quantity to see your estimate"
        } else {
            val co2Kg = kilograms * 2.48
            val meals = (kilograms * 1.74 + 0.5).toInt()
            val points = (kilograms * 19.5 + 0.5).toInt()
            "Community impact\n~${"%.1f".format(co2Kg)} kg CO₂ prevented · $meals meals · +$points pts"
        }
    }

    /** Temporary email/password sign-in surface for surplus CRUD testing; the group can replace it with its final auth flow. */
    private fun updateTestAuthProfile() {
        val status = findViewById<android.widget.TextView>(R.id.tvTestAuthStatus)
        val signIn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTestAuthSignIn)
        val signOut = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTestAuthSignOut)
        val emailField = findViewById<android.widget.EditText>(R.id.etTestAuthEmail)
        val passwordField = findViewById<android.widget.EditText>(R.id.etTestAuthPassword)
        val currentUser = SupabaseProvider.client.auth.currentUserOrNull()
        if (currentUser == null) {
            status.text = "Not signed in · use your Supabase test account"
            signIn.visibility = View.VISIBLE
            signOut.visibility = View.GONE
            emailField.visibility = View.VISIBLE
            passwordField.visibility = View.VISIBLE
        } else {
            status.text = "Signed in\n${currentUser.email ?: "Supabase user"}"
            signIn.visibility = View.GONE
            signOut.visibility = View.VISIBLE
            emailField.visibility = View.GONE
            passwordField.visibility = View.GONE
        }
    }

    private fun signInForSurplusTest() {
        val emailField = findViewById<android.widget.EditText>(R.id.etTestAuthEmail)
        val passwordField = findViewById<android.widget.EditText>(R.id.etTestAuthPassword)
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        val status = findViewById<android.widget.TextView>(R.id.tvTestAuthStatus)
        if (email.isBlank() || password.isBlank()) {
            status.text = "Enter the email and password for your Supabase test user."
            return
        }
        lifecycleScope.launch {
            val signIn = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTestAuthSignIn)
            signIn.isEnabled = false
            status.text = "Signing in…"
            try {
                SupabaseProvider.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                passwordField.text?.clear()
                updateTestAuthProfile()
                Toast.makeText(this@MainActivity, "Signed in. You can submit a test donation.", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                status.text = error.message?.takeIf { it.isNotBlank() }
                    ?: "Sign-in failed. Check the email, password, and invitation status."
            } finally {
                signIn.isEnabled = true
            }
        }
    }

    private fun signOutFromSurplusTest() {
        lifecycleScope.launch {
            val status = findViewById<android.widget.TextView>(R.id.tvTestAuthStatus)
            try {
                SupabaseProvider.client.auth.signOut()
                updateTestAuthProfile()
                Toast.makeText(this@MainActivity, "Signed out", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) {
                status.text = error.message ?: "Could not sign out."
            }
        }
    }

    private fun setupSurplusDatePicker() {
        val expiryField = findViewById<android.widget.EditText>(R.id.etSurplusExpiry)
        expiryField.contentDescription = "Choose best before date"
        expiryField.setOnClickListener {
            val today = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    val selected = java.util.Calendar.getInstance().apply {
                        set(year, month, day)
                    }
                    val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                    expiryField.setText(formatter.format(selected.time))
                },
                today.get(java.util.Calendar.YEAR),
                today.get(java.util.Calendar.MONTH),
                today.get(java.util.Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = today.timeInMillis
            }.show()
        }
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
