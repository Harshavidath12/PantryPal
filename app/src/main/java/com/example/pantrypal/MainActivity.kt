package com.example.pantrypal

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
import com.example.pantrypal.data.repository.SurplusRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
    private var selectedHubName = "Colombo Community Kitchen"
    private var selectedHubId: String? = null
    private val surplusRepository = SurplusRepository()
    private lateinit var foodSafetyChecks: List<android.widget.CheckBox>
    private var availableSurplusHubs: List<com.example.pantrypal.data.model.SurplusHubDto> = emptyList()
    private var hubSearchWatcher: android.text.TextWatcher? = null
    private var selectedSurplusPhotoUri: Uri? = null
    private var pendingPhotoDonationId: String? = null
    private var impactHubNames: Map<String, String> = emptyMap()
    private val surplusPhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val donationId = pendingPhotoDonationId
            if (donationId != null) {
                pendingPhotoDonationId = null
                lifecycleScope.launch {
                    try {
                        saveDonationPhoto(uri, donationId)
                        loadDonationImpact()
                        Toast.makeText(this@MainActivity, "Donation photo updated", Toast.LENGTH_SHORT).show()
                    } catch (error: Exception) {
                        Toast.makeText(this@MainActivity, "Could not save photo: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
                return@registerForActivityResult
            }
            selectedSurplusPhotoUri = uri
            findViewById<android.widget.ImageView>(R.id.ivSurplusPhoto).apply {
                setImageURI(uri)
                visibility = View.VISIBLE
            }
            findViewById<View>(R.id.tvSurplusPhotoPrompt).visibility = View.GONE
            Toast.makeText(this, "Photo added", Toast.LENGTH_SHORT).show()
        }
    }

    private val profilePhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ivProfilePicture = findViewById<android.widget.ImageView>(R.id.ivProfilePicture)
            val tvInitials = findViewById<android.widget.TextView>(R.id.tvInitials)
            
            // Set image and hide initials
            ivProfilePicture?.setImageURI(uri)
            tvInitials?.visibility = View.GONE
            
            // Save to database
            val profileRepository = com.example.pantrypal.data.repository.ProfileRepository()
            lifecycleScope.launch {
                val success = profileRepository.updateProfileDetails(1L, "Tharushi Malvenna", uri.toString())
                if (success) {
                    Toast.makeText(this@MainActivity, "Profile photo updated in Database!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to save photo to Database.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
        setupSurplusSafetyChecklist()
        setupSurplusDatePicker()
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

        binding.surplusScreenContainer.root.findViewById<View>(R.id.btnFlagSurplus).setOnClickListener {
            showSurplusDetail("flag")
        }
        binding.surplusScreenContainer.root.findViewById<View>(R.id.btnFindHub).setOnClickListener {
            showSurplusDetail("hubs")
        }
        binding.surplusScreenContainer.root.findViewById<View>(R.id.btnCommunityImpact).setOnClickListener { showSurplusDetail("impact") }
        findViewById<View>(R.id.btnSurplusBack).setOnClickListener {
            binding.surplusDetailContainer.root.visibility = View.GONE
            binding.surplusScreenContainer.root.visibility = View.VISIBLE
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

        findViewById<View>(R.id.btnAddProfilePhoto)?.setOnClickListener {
            profilePhotoPicker.launch("image/*")
        }

        val profileRepository = com.example.pantrypal.data.repository.ProfileRepository()

        findViewById<android.widget.TextView>(R.id.btnDeletePicture)?.setOnClickListener {
            lifecycleScope.launch {
                val success = profileRepository.deleteProfilePicture(1L) // Assuming user ID 1
                if (success) {
                    Toast.makeText(this@MainActivity, "Profile picture removed in Database!", Toast.LENGTH_SHORT).show()
                    findViewById<android.widget.ImageView>(R.id.ivProfilePicture)?.setImageDrawable(null)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to delete profile picture.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<android.widget.TextView>(R.id.btnUpdateProfile)?.setOnClickListener {
            showEditProfileDialog()
        }

        findViewById<android.widget.TextView>(R.id.btnLogOut)?.setOnClickListener {
            val prefs = getSharedPreferences("pantry_pal_prefs", MODE_PRIVATE)
            prefs.edit().clear().apply()
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        loadUserProfile()
    }

    private fun loadUserProfile() {
        val prefs = getSharedPreferences("pantry_pal_prefs", MODE_PRIVATE)
        val userName = prefs.getString("user_name", null)
        val userEmail = prefs.getString("user_email", null)

        val displayName = when {
            !userName.isNullOrEmpty() -> userName
            !userEmail.isNullOrEmpty() -> userEmail.substringBefore("@")
            else -> "User"
        }

        val tvProfileName = findViewById<android.widget.TextView>(R.id.tvProfileName)
        val tvInitials = findViewById<android.widget.TextView>(R.id.tvInitials)
        val tvMemberYouName = findViewById<android.widget.TextView>(R.id.tvMemberYouName)
        val tvMemberYouInitials = findViewById<android.widget.TextView>(R.id.tvMemberYouInitials)

        tvProfileName?.text = displayName

        val initials = displayName.trim().split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifEmpty { "U" }

        tvInitials?.text = initials
        tvMemberYouInitials?.text = initials

        val firstName = displayName.split(" ").firstOrNull() ?: displayName
        tvMemberYouName?.text = "$firstName (You)"
    }

    private fun showEditProfileDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_profile)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etEditName = dialog.findViewById<android.widget.EditText>(R.id.etEditName)
        val btnSaveProfile = dialog.findViewById<View>(R.id.btnSaveProfile)
        val btnCancelEdit = dialog.findViewById<View>(R.id.btnCancelEdit)
        val ivDialogProfilePic = dialog.findViewById<android.widget.ImageView>(R.id.ivDialogProfilePic)
        val tvDialogInitials = dialog.findViewById<android.widget.TextView>(R.id.tvDialogInitials)

        val currentName = findViewById<android.widget.TextView>(R.id.tvProfileName)?.text?.toString() ?: "User"
        etEditName.setText(currentName)

        val profileRepository = com.example.pantrypal.data.repository.ProfileRepository()

        // Extract initials
        val initials = currentName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
        tvDialogInitials.text = initials

        val mainProfilePic = findViewById<android.widget.ImageView>(R.id.ivProfilePicture)
        if (mainProfilePic?.drawable != null) {
            ivDialogProfilePic.setImageDrawable(mainProfilePic.drawable)
            tvDialogInitials.visibility = View.GONE
        }

        btnCancelEdit.setOnClickListener {
            dialog.dismiss()
        }

        btnSaveProfile.setOnClickListener {
            val newName = etEditName.text.toString().trim()
            if (newName.isNotEmpty()) {
                lifecycleScope.launch {
                    val prefs = getSharedPreferences("pantry_pal_prefs", MODE_PRIVATE)
                    prefs.edit().putString("user_name", newName).apply()
                    profileRepository.updateProfileDetails(1L, newName, null)
                    loadUserProfile()
                    Toast.makeText(this@MainActivity, "Profile name updated!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
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
                if (binding.fullPantryItemsScreenContainer.root.visibility != View.VISIBLE) {
                    binding.pantryHomeScreenContainer.root.visibility = View.VISIBLE
                }
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.surplusScreenContainer.root.visibility = View.GONE
                binding.surplusDetailContainer.root.visibility = View.GONE
                findViewById<View>(R.id.profileScreenContainer).visibility = View.GONE
                binding.notificationsScreenContainer.visibility = View.GONE
                binding.ivNavPantry.setColorFilter(primaryColor)
                binding.tvNavPantry.setTextColor(primaryColor)
                binding.tvNavPantry.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.SHOPPING -> {
                binding.pantryHomeScreenContainer.root.visibility = View.GONE
                binding.fullPantryItemsScreenContainer.root.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.VISIBLE
                binding.surplusScreenContainer.root.visibility = View.GONE
                binding.surplusDetailContainer.root.visibility = View.GONE
                findViewById<View>(R.id.profileScreenContainer).visibility = View.GONE
                binding.notificationsScreenContainer.visibility = View.GONE
                binding.ivNavShopping.setColorFilter(primaryColor)
                binding.tvNavShopping.setTextColor(primaryColor)
                binding.tvNavShopping.typeface = Typeface.DEFAULT_BOLD
            }
            NavTab.SURPLUS -> {
                binding.pantryHomeScreenContainer.root.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.surplusScreenContainer.root.visibility = View.VISIBLE
                binding.surplusDetailContainer.root.visibility = View.GONE
                findViewById<View>(R.id.profileScreenContainer).visibility = View.GONE
                Toast.makeText(this, "Surplus tab selected", Toast.LENGTH_SHORT).show()
                binding.fullPantryItemsScreenContainer.root.visibility = View.GONE
                binding.notificationsScreenContainer.visibility = View.GONE
                binding.ivNavSurplus.setColorFilter(primaryColor)
                binding.tvNavSurplus.setTextColor(primaryColor)
                binding.tvNavSurplus.typeface = Typeface.DEFAULT_BOLD
                loadDonationImpact()
            }
            NavTab.PROFILE -> {
                binding.pantryHomeScreenContainer.root.visibility = View.GONE
                binding.shoppingScreenContainer.root.visibility = View.GONE
                binding.surplusScreenContainer.root.visibility = View.GONE
                binding.surplusDetailContainer.root.visibility = View.GONE
                findViewById<View>(R.id.profileScreenContainer).visibility = View.VISIBLE
                loadUserProfile()
                binding.fullPantryItemsScreenContainer.root.visibility = View.GONE
                binding.notificationsScreenContainer.visibility = View.GONE
                binding.ivNavProfile.setColorFilter(primaryColor)
                binding.tvNavProfile.setTextColor(primaryColor)
                binding.tvNavProfile.typeface = Typeface.DEFAULT_BOLD
            }
        }
    }

    private fun showSurplusDetail(page: String) {
        binding.pantryHomeScreenContainer.root.visibility = View.GONE
        binding.shoppingScreenContainer.root.visibility = View.GONE
        binding.surplusScreenContainer.root.visibility = View.GONE
        binding.surplusDetailContainer.root.visibility = View.VISIBLE
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
        binding.surplusDetailContainer.root.setBackgroundColor(
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
                    textSize = 14f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                titleBlock.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "Community food hub"
                    textSize = 11f
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
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                })
                metaRow.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "  ·  Open until ${hub.openUntil}"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                })
                content.addView(metaRow)

                val footer = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                footer.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "✓  ${hub.acceptedFoods}"
                    textSize = 10f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary_dark))
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                }, android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                footer.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "● Open now"
                    textSize = 10f
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
                textSize = 14f
                setPadding(10, 16, 10, 16)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            })
        }
    }

    private fun loadDonationImpact() {
        val rescuedText = findViewById<android.widget.TextView>(R.id.tvImpactFood)
        val mealsText = findViewById<android.widget.TextView>(R.id.tvImpactMeals)
        val co2Text = findViewById<android.widget.TextView>(R.id.tvImpactCo2)
        val headline = findViewById<android.widget.TextView>(R.id.tvImpactHeadline)
        val activeCard = findViewById<View>(R.id.tvImpactActiveFood).parent.parent as View
        val communityCard = findViewById<View>(R.id.tvImpactBeneficiaryTitle).parent.parent.parent as View
        val chart = findViewById<com.example.pantrypal.ui.surplus.SurplusImpactChart>(R.id.impactChart)
        lifecycleScope.launch {
            try {
                val history = surplusRepository.getDonations()
                val hubs = try {
                    surplusRepository.getHubs()
                } catch (_: Exception) {
                    emptyList()
                }
                impactHubNames = hubs.associate { it.id to it.name }
                renderHomeHubs(hubs)
                val rescuedKg = history.filter { it.status.equals("PICKED_UP", true) || it.status.equals("DROPPED_OFF", true) }.sumOf { donation ->
                    parseKilograms(donation.quantity) ?: 0.0
                }
                val meals = (rescuedKg * 8.3).toInt()
                rescuedText.text = "${"%.1f".format(rescuedKg)}kg"
                mealsText.text = meals.toString()
                co2Text.text = "${"%.1f".format(rescuedKg * 2.0)}kg"
                val completedCount = history.count { it.status in setOf("PICKED_UP", "DROPPED_OFF") }
                headline.text = "${history.size} community donation${if (history.size == 1) "" else "s"} · $completedCount completed"
                findViewById<android.widget.TextView>(R.id.tvImpactLevel).text = when {
                    history.size >= 10 -> "LEVEL 3 RESCUER"
                    history.size >= 5 -> "LEVEL 2 RESCUER"
                    history.isNotEmpty() -> "LEVEL 1 RESCUER"
                    else -> "NEW RESCUER"
                }
                findViewById<android.widget.TextView>(R.id.tvFoodSaved).text = "${"%.1f".format(rescuedKg)} kg"
                findViewById<android.widget.TextView>(R.id.tvDonations).text = completedCount.toString()
                renderHomeRecentDonations(history)
                val latest = history.firstOrNull()
                val activeDonation = history.firstOrNull {
                    it.status.equals("PENDING", true) || it.status.equals("CLAIMED", true)
                }
                activeCard.visibility = if (activeDonation == null) View.GONE else View.VISIBLE
                communityCard.visibility = if (latest == null) View.GONE else View.VISIBLE
                if (latest != null) {
                    val latestHubName = impactHubNames[latest.hubId] ?: "Community hub"
                    findViewById<android.widget.TextView>(R.id.tvImpactBeneficiaryTitle).text = latestHubName
                    findViewById<android.widget.TextView>(R.id.tvImpactBeneficiaryCaption).text = "${latest.foodName} · ${latest.quantity}"
                }
                if (activeDonation != null) {
                    val hubName = impactHubNames[activeDonation.hubId] ?: "Community hub"
                    findViewById<android.widget.TextView>(R.id.tvImpactHubName).text = hubName
                    showDonationPhoto(activeDonation.id)
                    findViewById<android.widget.TextView>(R.id.tvImpactActiveFood).text = "${activeDonation.foodName} · ${activeDonation.quantity}"
                    val state = activeDonation.status.uppercase()
                    findViewById<android.widget.TextView>(R.id.tvImpactProgress).text = when (state) {
                        "PICKED_UP" -> "●──────●──────●\nFlagged          Claimed          Picked up"
                        "DROPPED_OFF" -> "●────────────●\nFlagged          Dropped off at hub"
                        "CLAIMED" -> "●──────●──────○\nFlagged          Claimed          Pickup next"
                        "CANCELLED" -> "Donation cancelled · You can list another item"
                        else -> "●──────○──────○\nFlagged          Claim pending          Pickup next"
                    }
                    findViewById<android.widget.TextView>(R.id.tvImpactClaim).text = when (state) {
                        "CLAIMED" -> "A community hub claimed this donation · Pickup window: ${activeDonation.pickupWindow}"
                        "PICKED_UP" -> "Donation picked up · Your food helped a nearby community"
                        else -> "Waiting for a nearby hub to claim this donation · ${activeDonation.pickupWindow}"
                    }
                } else {
                    findViewById<android.widget.TextView>(R.id.tvImpactActiveFood).text = "No courier pickup in progress"
                    findViewById<android.widget.TextView>(R.id.tvImpactProgress).text = "Flagged          Claimed          Picked up"
                    findViewById<android.widget.TextView>(R.id.tvImpactClaim).text = "Courier donations will appear here while they are pending or claimed."
                    if (latest != null) showDonationPhoto(latest.id) else {
                        findViewById<android.widget.TextView>(R.id.tvImpactHubName).text = "Community hub"
                        findViewById<android.widget.ImageView>(R.id.ivImpactDonationPhoto).setImageDrawable(null)
                    }
                }
                renderDonationHistory(history)
                chart.setDonations(history)
            } catch (error: Exception) {
                headline.text = "Could not load community donations: ${error.message ?: "check Supabase connection"}"
                findViewById<android.widget.TextView>(R.id.tvFoodSaved).text = "0.0 kg"
                findViewById<android.widget.TextView>(R.id.tvDonations).text = "0"
                renderHomeRecentDonations(emptyList())
                renderHomeHubs(emptyList())
                activeCard.visibility = View.GONE
                communityCard.visibility = View.GONE
                renderDonationHistory(emptyList())
                chart.setDonations(emptyList())
            }
        }
    }

    private fun renderHomeHubs(hubs: List<com.example.pantrypal.data.model.SurplusHubDto>) {
        val container = findViewById<android.widget.LinearLayout>(R.id.homeHubList)
        container.removeAllViews()
        if (hubs.isEmpty()) {
            container.addView(android.widget.TextView(this).apply {
                text = "No active community hubs are available."
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
            })
            return
        }

        hubs.take(2).forEach { hub ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = dp(17).toFloat()
                cardElevation = dp(1).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_card_bg))
            }
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(11), dp(13), dp(11))
            }
            row.addView(android.widget.TextView(this).apply {
                text = "⌂"
                textSize = 21f
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
            }, android.widget.LinearLayout.LayoutParams(dp(42), dp(42)))
            val details = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(6), 0)
            }
            details.addView(android.widget.TextView(this).apply {
                text = hub.name
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            })
            details.addView(android.widget.TextView(this).apply {
                text = "Open until ${hub.openUntil} · ${"%.1f".format(hub.distanceKm)} km"
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(details, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(android.widget.TextView(this).apply {
                text = "›"
                textSize = 24f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary))
            })
            card.addView(row)
            card.setOnClickListener {
                selectedHubId = hub.id
                selectedHubName = hub.name
                showSurplusDetail("hubs")
            }
            container.addView(card, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(7)
            })
        }
    }

    private fun renderHomeRecentDonations(history: List<com.example.pantrypal.data.repository.DonationHistoryDto>) {
        val container = findViewById<android.widget.LinearLayout>(R.id.homeRecentDonationList)
        container.removeAllViews()
        if (history.isEmpty()) {
            container.addView(android.widget.TextView(this).apply {
                text = "No community donations yet. Be the first to share surplus food."
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
            })
            return
        }

        history.take(3).forEach { donation ->
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = dp(17).toFloat()
                cardElevation = dp(1).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_card_bg))
                isClickable = true
                isFocusable = true
            }
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(11), dp(13), dp(11))
            }
            val photoFile = getSharedPreferences("surplus_donation_photos", MODE_PRIVATE)
                .getString("photo_${donation.id}", null)?.let(::File)?.takeIf { it.isFile }
            if (photoFile != null) {
                row.addView(android.widget.ImageView(this).apply {
                    setImageURI(Uri.fromFile(photoFile))
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    contentDescription = "Photo of ${donation.foodName}"
                }, android.widget.LinearLayout.LayoutParams(dp(50), dp(50)))
            }
            val details = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                if (photoFile != null) setPadding(dp(10), 0, 0, 0)
            }
            details.addView(android.widget.TextView(this).apply {
                text = "${donation.foodName} · ${donation.quantity}"
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            })
            details.addView(android.widget.TextView(this).apply {
                val hubName = impactHubNames[donation.hubId] ?: "Community hub"
                text = "$hubName · ${donation.status.replace('_', ' ')}"
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(0, dp(3), 0, 0)
            })
            details.addView(android.widget.TextView(this).apply {
                val date = donation.createdAt?.take(10)?.takeIf { it.isNotBlank() } ?: "Date unavailable"
                text = "Pickup: ${donation.pickupWindow} · $date"
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(details, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)
            card.setOnClickListener { showSurplusDetail("impact") }
            container.addView(card, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(7)
            })
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
                text = "No community donations yet. Add the first surplus donation to get started."
                textSize = 14f
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
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@MainActivity, if (pending) R.color.pantry_primary_dark else R.color.text_secondary))
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_sync_card)
                })
                body.addView(titleRow)
                body.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "${impactHubNames[donation.hubId] ?: "Community hub"} · Best before ${donation.bestBefore}"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    setPadding(0, dp(5), 0, 0)
                })
                body.addView(android.widget.TextView(this@MainActivity).apply {
                    text = "Pickup: ${donation.pickupWindow} · $date"
                    textSize = 12f
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
                        val savedPhoto = getSharedPreferences("surplus_donation_photos", MODE_PRIVATE)
                            .getString("photo_${donation.id}", null)?.let(::File)?.isFile == true
                        text = if (savedPhoto) "Change photo" else "Add photo"
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary))
                        setPadding(dp(8), dp(7), dp(8), dp(7))
                        isClickable = true
                        setOnClickListener {
                            pendingPhotoDonationId = donation.id
                            surplusPhotoPicker.launch("image/*")
                        }
                    })
                    actions.addView(android.widget.TextView(this@MainActivity).apply {
                        text = "Edit"
                        textSize = 13f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pantry_primary))
                        setPadding(dp(12), dp(7), dp(12), dp(7))
                        isClickable = true
                        setOnClickListener { showEditDonationDialog(donation) }
                    })
                    actions.addView(android.widget.TextView(this@MainActivity).apply {
                        text = "Delete"
                        textSize = 13f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.rgb(174, 69, 60))
                        setPadding(dp(12), dp(7), dp(4), dp(7))
                        isClickable = true
                        setOnClickListener { confirmDeleteDonation(donation) }
                    })
                    actions.addView(android.widget.TextView(this@MainActivity).apply {
                        val eligible = meetsCourierMinimum(donation.quantity)
                        text = if (eligible) "Mark claimed" else "Edit · 10 kg / 15 units min"
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(this@MainActivity, if (eligible) R.color.white else R.color.pantry_primary))
                        setPadding(dp(9), dp(7), dp(9), dp(7))
                        background = ContextCompat.getDrawable(this@MainActivity, if (eligible) R.drawable.bg_chip_selected else R.drawable.bg_sync_card)
                        isClickable = true
                        setOnClickListener {
                            if (eligible) confirmAdvanceDonation(donation) else showEditDonationDialog(donation)
                        }
                    })
                    body.addView(actions)
                } else if (donation.status.equals("CLAIMED", true)) {
                    val advance = android.widget.TextView(this@MainActivity).apply {
                        val eligible = meetsCourierMinimum(donation.quantity)
                        text = if (eligible) "Mark picked up" else "Pickup minimum not met"
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setTextColor(ContextCompat.getColor(this@MainActivity, if (eligible) R.color.white else R.color.text_secondary))
                        setPadding(dp(10), dp(7), dp(10), dp(7))
                        background = ContextCompat.getDrawable(this@MainActivity, if (eligible) R.drawable.bg_chip_selected else R.drawable.bg_sync_card)
                        isClickable = eligible
                        setOnClickListener { if (eligible) confirmAdvanceDonation(donation) }
                    }
                    body.addView(advance, android.widget.LinearLayout.LayoutParams(-2, -2).apply {
                        gravity = android.view.Gravity.END
                        topMargin = dp(8)
                    })
                }
                addView(body)
            }
            impactLayout.addView(card, insertIndex++, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(7)
            })
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun meetsCourierMinimum(quantity: String): Boolean {
        val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(kg|kgs|units?)", RegexOption.IGNORE_CASE)
            .find(quantity) ?: return false
        val amount = match.groupValues[1].toDoubleOrNull() ?: return false
        return if (match.groupValues[2].startsWith("unit", true)) amount >= 15 && amount % 1.0 == 0.0 else amount >= 10.0
    }

    private fun showDonationPhoto(donationId: String) {
        val photoView = findViewById<android.widget.ImageView>(R.id.ivImpactDonationPhoto)
        val storedPath = getSharedPreferences("surplus_donation_photos", MODE_PRIVATE)
            .getString("photo_$donationId", null)
        val photoFile = storedPath?.let(::File)
        if (photoFile?.isFile == true) {
            photoView.setImageURI(Uri.fromFile(photoFile))
        } else {
            photoView.setImageDrawable(null)
        }
    }

    private suspend fun saveDonationPhoto(uri: Uri, donationId: String) = withContext(Dispatchers.IO) {
        val folder = File(filesDir, "surplus_donation_photos").apply { mkdirs() }
        val photoFile = File(folder, "$donationId.img")
        val input = contentResolver.openInputStream(uri) ?: error("The selected photo could not be opened.")
        input.use { source -> photoFile.outputStream().use(source::copyTo) }
        getSharedPreferences("surplus_donation_photos", MODE_PRIVATE)
            .edit().putString("photo_$donationId", photoFile.absolutePath).apply()
    }

    private fun removeDonationPhoto(donationId: String) {
        val preferences = getSharedPreferences("surplus_donation_photos", MODE_PRIVATE)
        preferences.getString("photo_$donationId", null)?.let(::File)?.delete()
        preferences.edit().remove("photo_$donationId").apply()
    }

    private fun showEditDonationDialog(donation: com.example.pantrypal.data.repository.DonationHistoryDto) {
        if (!donation.status.equals("PENDING", true)) return
        val primary = ContextCompat.getColor(this, R.color.pantry_primary)
        val primaryDark = ContextCompat.getColor(this, R.color.pantry_primary_dark)
        val pale = ContextCompat.getColor(this, R.color.pantry_primary_light)
        val muted = ContextCompat.getColor(this, R.color.text_secondary)
        fun rounded(fill: Int, stroke: Int = fill, width: Int = 0): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(14).toFloat()
                if (width > 0) setStroke(dp(width), stroke)
            }
        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), dp(4))
        }
        fun label(text: String) {
            content.addView(android.widget.TextView(this).apply {
                this.text = text
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(muted)
                setPadding(dp(2), dp(10), 0, dp(4))
            })
        }
        fun field(hintText: String, value: String, type: Int = android.text.InputType.TYPE_CLASS_TEXT): android.widget.EditText {
            val edit = android.widget.EditText(this).apply {
                hint = hintText
                setText(value)
                inputType = type
                setSingleLine(true)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                background = rounded(ContextCompat.getColor(this@MainActivity, R.color.pantry_card_bg), ContextCompat.getColor(this@MainActivity, R.color.pantry_primary_container), 1)
                setPadding(dp(13), 0, dp(13), 0)
            }
            content.addView(edit, android.widget.LinearLayout.LayoutParams(-1, dp(48)))
            return edit
        }
        content.addView(android.widget.TextView(this).apply {
            text = "Fine-tune your donation"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, 0, 0, dp(8))
        })
        val food = field("Food description", donation.foodName)
        label("Quantity type")
        val quantityMode = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.HORIZONTAL
            background = rounded(pale)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val kgOption = android.widget.RadioButton(this).apply {
            id = View.generateViewId(); text = "KG"; gravity = android.view.Gravity.CENTER
            buttonDrawable = null; setPadding(dp(8), dp(9), dp(8), dp(9)); textSize = 12f
        }
        val unitsOption = android.widget.RadioButton(this).apply {
            id = View.generateViewId(); text = "Units"; gravity = android.view.Gravity.CENTER
            buttonDrawable = null; setPadding(dp(8), dp(9), dp(8), dp(9)); textSize = 12f
        }
        quantityMode.addView(kgOption, android.widget.RadioGroup.LayoutParams(0, -2, 1f))
        quantityMode.addView(unitsOption, android.widget.RadioGroup.LayoutParams(0, -2, 1f))
        val oldQuantityMatch = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(kg|kgs|units?)?", RegexOption.IGNORE_CASE).find(donation.quantity)
        val oldAmount = oldQuantityMatch?.groupValues?.get(1).orEmpty()
        val startsAsUnits = oldQuantityMatch?.groupValues?.get(2)?.startsWith("unit", true) == true
        quantityMode.check(if (startsAsUnits) unitsOption.id else kgOption.id)
        content.addView(quantityMode)
        label("Amount")
        val quantity = field(
            if (startsAsUnits) "Number of items" else "Weight in kg",
            oldAmount,
            if (startsAsUnits) android.text.InputType.TYPE_CLASS_NUMBER
            else android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        fun refreshQuantityMode() {
            val units = quantityMode.checkedRadioButtonId == unitsOption.id
            listOf(kgOption, unitsOption).forEach { option ->
                val selected = option.isChecked
                option.background = rounded(if (selected) primary else android.graphics.Color.TRANSPARENT)
                option.setTextColor(if (selected) android.graphics.Color.WHITE else primaryDark)
            }
            quantity.inputType = if (units) android.text.InputType.TYPE_CLASS_NUMBER
                else android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            quantity.hint = if (units) "Number of items" else "Weight in kg"
        }
        refreshQuantityMode()
        quantityMode.setOnCheckedChangeListener { _, checkedId ->
            refreshQuantityMode()
        }
        label("Best before")
        val bestBefore = field("Best before", donation.bestBefore)
        label("Pickup preference")
        val pickupMode = android.widget.RadioGroup(this).apply { orientation = android.widget.RadioGroup.VERTICAL }
        fun pickupOption(title: String, detail: String): android.widget.RadioButton = android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            text = "$title\n$detail"
            textSize = 12f
            setLineSpacing(dp(2).toFloat(), 1f)
            buttonTintList = android.content.res.ColorStateList.valueOf(primary)
            setPadding(dp(11), dp(6), dp(11), dp(6))
        }
        val courierOption = pickupOption("Volunteer / courier pickup", "10 kg or 15 units minimum")
        val dropoffOption = pickupOption("Self drop-off at hub", "Completes the donation immediately")
        val isDropoff = donation.pickupWindow.startsWith("Drop off at", ignoreCase = true)
        pickupMode.addView(courierOption, android.widget.LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        pickupMode.addView(dropoffOption, android.widget.LinearLayout.LayoutParams(-1, -2))
        pickupMode.check(if (isDropoff) dropoffOption.id else courierOption.id)
        fun refreshPickupMode() {
            listOf(courierOption, dropoffOption).forEach { option ->
                option.background = rounded(
                    if (option.isChecked) ContextCompat.getColor(this, R.color.pantry_primary_light) else ContextCompat.getColor(this, R.color.pantry_card_bg),
                    if (option.isChecked) ContextCompat.getColor(this, R.color.pantry_primary) else ContextCompat.getColor(this, R.color.pantry_primary_container),
                    1
                )
            }
        }
        refreshPickupMode()
        pickupMode.setOnCheckedChangeListener { _, _ -> refreshPickupMode() }
        content.addView(pickupMode)
        content.addView(android.widget.TextView(this).apply {
            text = "Self drop-off has no courier minimum. Choosing it marks this donation complete."
            textSize = 11f
            setTextColor(primaryDark)
            background = rounded(pale)
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Edit donation")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save changes", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(primary)
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(muted)
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val foodValue = food.text.toString().trim()
                val amountText = quantity.text.toString().trim()
                val dateValue = bestBefore.text.toString().trim()
                if (foodValue.isBlank() || amountText.isBlank() || dateValue.isBlank()) {
                    Toast.makeText(this, "Please complete all donation fields.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val units = quantityMode.checkedRadioButtonId == unitsOption.id
                val dropoff = pickupMode.checkedRadioButtonId == dropoffOption.id
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0.0 || (units && amount % 1.0 != 0.0)) {
                    Toast.makeText(this, if (units) "Enter a whole number of units." else "Enter a valid weight greater than zero.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!dropoff && (if (units) amount < 15.0 else amount < 10.0)) {
                    Toast.makeText(this, if (units) "Courier pickup requires at least 15 units." else "Courier pickup requires at least 10 kg.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val quantityValue = if (units) "${amount.toInt()} units" else "${amount.toString().trimEnd('0').trimEnd('.')} kg"
                val pickupValue = if (dropoff) {
                    val hubName = impactHubNames[donation.hubId] ?: "community hub"
                    "Drop off at $hubName"
                } else "Today, 2:00 PM – 5:00 PM"
                val updatedStatus = if (dropoff) "DROPPED_OFF" else "PENDING"
                lifecycleScope.launch {
                    try {
                        val updated = surplusRepository.updateDonation(donation, foodValue, quantityValue, dateValue, pickupValue, updatedStatus)
                        if (updated) Toast.makeText(this@MainActivity, "Donation updated", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(this@MainActivity, "This donation is no longer editable.", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        loadDonationImpact()
                    } catch (error: Exception) {
                        val detail = error.message.orEmpty()
                        val message = if (detail.contains("row-level security", ignoreCase = true)) {
                            "Supabase blocked this edit. Run surplus_guest_mode.sql in SQL Editor, then retry."
                        } else "Could not update donation: ${detail.ifBlank { "Please try again." }}"
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
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
            .setMessage("${donation.foodName} · ${donation.quantity} will be removed from the shared community history.")
            .setNegativeButton("Keep", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val deleted = surplusRepository.deleteDonation(donation)
                        if (deleted) removeDonationPhoto(donation.id)
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

    private fun confirmAdvanceDonation(donation: com.example.pantrypal.data.repository.DonationHistoryDto) {
        val nextStage = when (donation.status.uppercase()) {
            "PENDING" -> "CLAIMED"
            "CLAIMED" -> "PICKED_UP"
            else -> return
        }
        val stageMessage = if (nextStage == "CLAIMED") {
            "Use this when a community hub has accepted your courier pickup donation."
        } else {
            "Use this when the volunteer has collected your donation."
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Mark as ${nextStage.replace('_', ' ').lowercase()}?")
            .setMessage(stageMessage)
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Update stage") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val updated = surplusRepository.advanceDonationStatus(donation)
                        Toast.makeText(
                            this@MainActivity,
                            if (updated) "Donation stage updated" else "No row changed. Run the latest surplus_schema.sql in Supabase SQL Editor, then retry.",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadDonationImpact()
                    } catch (error: Exception) {
                        Toast.makeText(this@MainActivity, "Could not update stage: ${error.message}", Toast.LENGTH_LONG).show()
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
                textSize = 12f
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
            checklist.addView(checkbox, android.widget.LinearLayout.LayoutParams(-1, (48 * resources.displayMetrics.density).toInt()).apply {
                bottomMargin = (4 * resources.displayMetrics.density).toInt()
            })
        }
        val quantityField = findViewById<android.widget.EditText>(R.id.etSurplusQuantity)
        findViewById<android.widget.RadioGroup>(R.id.rgQuantityMode).setOnCheckedChangeListener { _, checkedId ->
            val unitsMode = checkedId == R.id.rbQuantityUnits
            quantityField.setText("")
            quantityField.inputType = if (unitsMode) {
                android.text.InputType.TYPE_CLASS_NUMBER
            } else {
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            quantityField.hint = if (unitsMode) "1" else "2.3"
            findViewById<android.widget.TextView>(R.id.tvQuantitySuffix).text = if (unitsMode) "units" else "kg"
            updatePickupRequirementChecklist()
            updateCommunityImpactEstimate(quantityField.text.toString())
        }
        findViewById<android.widget.RadioGroup>(R.id.rgSurplusPickup).setOnCheckedChangeListener { _, _ ->
            updatePickupRequirementChecklist()
        }
        quantityField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCommunityImpactEstimate(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        updatePickupRequirementChecklist()
        updateCommunityImpactEstimate(quantityField.text.toString())
        val insertAt = form.indexOfChild(submitButton).coerceAtLeast(0)
        form.addView(checklist, insertAt, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 12
        })
    }

    private fun updatePickupRequirementChecklist() {
        val checklist = foodSafetyChecks.getOrNull(2) ?: return
        val courier = findViewById<android.widget.RadioButton>(R.id.rbSurplusCourier).isChecked
        val units = findViewById<android.widget.RadioButton>(R.id.rbQuantityUnits).isChecked
        checklist.text = when {
            !courier -> "Self drop-off selected\nNo courier minimum applies"
            units -> "Courier pickup minimum: 15 units\nEnter a whole number of items"
            else -> "Courier pickup minimum: 10 kg\nEligible for volunteer pickup"
        }
    }

    private fun updateCommunityImpactEstimate(quantity: String) {
        val communityCheck = foodSafetyChecks.getOrNull(3) ?: return
        if (findViewById<android.widget.RadioButton>(R.id.rbQuantityUnits).isChecked) {
            val count = quantity.trim().toIntOrNull()
            communityCheck.text = if (count == null || count <= 0) {
                "Community impact\nEnter a whole number of units"
            } else {
                "Community impact\n$count food units · weight-based impact unavailable"
            }
            return
        }
        val kilograms = parseKilograms(quantity)
        communityCheck.text = if (kilograms == null || kilograms <= 0.0) {
            "Community impact\nEnter a quantity to see your estimate"
        } else {
            val co2Kg = kilograms * 2.48
            val meals = (kilograms * 1.74 + 0.5).toInt()
            val points = (kilograms * 19.5 + 0.5).toInt()
            "Community impact\n~${"%.1f".format(co2Kg)} kg CO₂ prevented · $meals meals · +$points pts"
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

    private fun submitSurplusDonation() {
        val name = findViewById<android.widget.EditText>(R.id.etSurplusName).text.toString().trim()
        val rawQuantity = findViewById<android.widget.EditText>(R.id.etSurplusQuantity).text.toString().trim()
        val expiry = findViewById<android.widget.EditText>(R.id.etSurplusExpiry).text.toString().trim()
        if (name.isBlank() || rawQuantity.isBlank() || expiry.isBlank()) {
            Toast.makeText(this, "Complete the food, quantity and best before fields", Toast.LENGTH_SHORT).show()
            return
        }
        val hubId = selectedHubId
        if (hubId.isNullOrBlank()) {
            Toast.makeText(this, "Select a hub before submitting.", Toast.LENGTH_SHORT).show()
            showSurplusDetail("hubs")
            return
        }
        val selfDropoff = findViewById<android.widget.RadioButton>(R.id.rbSurplusDropoff).isChecked
        val courierPickup = !selfDropoff
        val unitsMode = findViewById<android.widget.RadioButton>(R.id.rbQuantityUnits).isChecked
        val quantity: String
        val kilograms: Double?
        if (unitsMode) {
            val units = rawQuantity.toIntOrNull()
            if (units == null || units < 1) {
                Toast.makeText(this, "Enter a whole number of units (at least 1).", Toast.LENGTH_LONG).show()
                return
            }
            if (courierPickup && units < 15) {
                Toast.makeText(this, "Volunteer pickup requires at least 15 units.", Toast.LENGTH_LONG).show()
                return
            }
            quantity = "$units units"
            kilograms = null
        } else {
            kilograms = parseKilograms("$rawQuantity kg")
            if (kilograms == null || kilograms <= 0.0) {
                Toast.makeText(this, "Enter a valid weight greater than zero.", Toast.LENGTH_LONG).show()
                return
            }
            if (courierPickup && kilograms < 10.0) {
                Toast.makeText(this, "Volunteer pickup requires at least 10 kg.", Toast.LENGTH_LONG).show()
                return
            }
            quantity = "$rawQuantity kg"
        }
        if (foodSafetyChecks.any { !it.isChecked }) {
            Toast.makeText(this, "Confirm every food safety item before submitting.", Toast.LENGTH_LONG).show()
            return
        }
        val submitButton = findViewById<View>(R.id.btnSubmitDonation)
        val pickupWindow = if (selfDropoff) {
            "Drop off at $selectedHubName"
        } else {
            "Today, 2:00 PM – 5:00 PM"
        }
        submitButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val donationId = surplusRepository.createDonation(
                    com.example.pantrypal.data.model.SurplusDonationDto(
                        hubId = hubId,
                        foodName = name,
                        quantity = quantity,
                        bestBefore = expiry,
                        pickupWindow = pickupWindow,
                        status = if (selfDropoff) "DROPPED_OFF" else "PENDING"
                    )
                )
                val photoUri = selectedSurplusPhotoUri
                if (photoUri != null) {
                    try {
                        saveDonationPhoto(photoUri, donationId)
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, "Donation saved, but its photo could not be retained on this device.", Toast.LENGTH_LONG).show()
                    }
                }
                selectedSurplusPhotoUri = null
                findViewById<android.widget.ImageView>(R.id.ivSurplusPhoto).apply {
                    setImageDrawable(null)
                    visibility = View.GONE
                }
                findViewById<android.widget.TextView>(R.id.tvSubmittedFood).text = "$name · $quantity"
                findViewById<android.widget.TextView>(R.id.tvSubmittedWeight).text = quantity
                findViewById<android.widget.TextView>(R.id.tvSubmittedWindow).text = pickupWindow
                findViewById<android.widget.TextView>(R.id.tvSubmittedStatus).text = if (selfDropoff) "Dropped off at hub" else "Pending volunteer pickup"
                findViewById<android.widget.TextView>(R.id.tvSubmissionSummary).text = if (selfDropoff) {
                    "$name · $quantity recorded as dropped off at $selectedHubName."
                } else {
                    "$name · $quantity has been added to your volunteer pickup queue."
                }
                findViewById<android.widget.TextView>(R.id.tvSubmissionCo2).text = if (kilograms == null) {
                    "♻  ${quantity} recorded · weight-based impact estimate unavailable"
                } else {
                    "♻  Est. ${"%.1f".format(kilograms * 2.9)} kg CO₂ emissions prevented"
                }
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
        val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(kg|kgs|g|grams?)\\b", RegexOption.IGNORE_CASE).find(quantity.trim())
            ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "g", "gram", "grams" -> value / 1000.0
            else -> value
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
            setViewTreeLifecycleOwner(this@MainActivity)
            setViewTreeViewModelStoreOwner(this@MainActivity)
            setViewTreeSavedStateRegistryOwner(this@MainActivity)
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
