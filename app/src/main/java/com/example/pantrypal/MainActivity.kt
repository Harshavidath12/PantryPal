package com.example.pantrypal

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
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
import com.example.pantrypal.data.model.FilterType
import com.example.pantrypal.data.model.NotificationItem
import com.example.pantrypal.data.model.PantryItemDto
import com.example.pantrypal.data.model.PrimaryButtonType
import com.example.pantrypal.data.repository.NotificationRepository
import com.example.pantrypal.databinding.ActivityMainBinding
import com.example.pantrypal.databinding.DialogAddAlertBinding
import com.example.pantrypal.databinding.DialogRecipeIdeasBinding
import com.example.pantrypal.ui.notifications.NotificationAdapter
import com.example.pantrypal.ui.notifications.NotificationViewModel
import com.example.pantrypal.ui.notifications.NotificationViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: NotificationViewModel
    private lateinit var adapter: NotificationAdapter

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
        setupClickListeners()
        observeUiState()
    }

    private fun setupArchitecture() {
        val repository = NotificationRepository()
        val factory = NotificationViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[NotificationViewModel::class.java]
    }

    @OptIn(InternalSerializationApi::class)
    private fun setupRecyclerView() {
        adapter = NotificationAdapter(
            onPrimaryActionClicked = { alert ->
                if (alert.primaryButtonType == PrimaryButtonType.RECIPE_IDEAS) {
                    showRecipeIdeasDialog(alert)
                } else {
                    viewModel.onAddToRestockClicked(
                        PantryItemDto(
                            id = alert.id,
                            title = alert.title,
                            categoryZone = alert.categoryAndLocation,
                            expiryText = alert.expiryText,
                            urgency = alert.urgency.name
                        )
                    )
                }
            },
            onMarkConsumedClicked = { alert ->
                viewModel.onMarkConsumedClicked(
                    PantryItemDto(
                        id = alert.id,
                        title = alert.title,
                        categoryZone = alert.categoryAndLocation,
                        expiryText = alert.expiryText,
                        urgency = alert.urgency.name
                    )
                )
            },
            onDeleteAlertClicked = { alert ->
                // Not supported yet
            },
            onSharedUpdateClicked = { update ->
                Toast.makeText(this, "Opening details for ${update.userName}'s update", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter
    }

    private fun setupClickListeners() {
        viewModel.fetchAlerts()
        
        binding.btnSync.setOnClickListener {
            viewModel.fetchAlerts()
        }

        binding.btnBack.setOnClickListener {
            Toast.makeText(this, "Back pressed", Toast.LENGTH_SHORT).show()
        }

        binding.btnBell.setOnClickListener {
            Toast.makeText(this, "Notifications menu", Toast.LENGTH_SHORT).show()
        }

        binding.btnProfileAvatar.setOnClickListener {
            Toast.makeText(this, "Opening Profile", Toast.LENGTH_SHORT).show()
        }

        binding.fabAddAlert.setOnClickListener {
            showAddAlertDialog()
        }

        binding.navPantry.setOnClickListener {
            Toast.makeText(this, "Pantry tab active", Toast.LENGTH_SHORT).show()
        }

        binding.navShopping.setOnClickListener {
            Toast.makeText(this, "Shopping tab selected", Toast.LENGTH_SHORT).show()
        }

        binding.navSurplus.setOnClickListener {
            Toast.makeText(this, "Surplus tab selected", Toast.LENGTH_SHORT).show()
        }

        binding.navProfile.setOnClickListener {
            Toast.makeText(this, "Profile tab selected", Toast.LENGTH_SHORT).show()
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
                                    id = dto.id ?: "",
                                    title = dto.title,
                                    categoryAndLocation = dto.categoryZone,
                                    expiryText = dto.expiryText,
                                    urgency = if (dto.urgency == "EXPIRING_TODAY") AlertUrgency.EXPIRING_TODAY else AlertUrgency.EXPIRING_SOON,
                                    imageResId = R.drawable.ic_food_yogurt,
                                    primaryButtonType = if (dto.urgency == "EXPIRING_TODAY") PrimaryButtonType.RECIPE_IDEAS else PrimaryButtonType.ADD_TO_RESTOCK
                                )
                            )
                        }

                        // Submit items to adapter
                        adapter.submitList(items)

                        // Update Chip Counts
                        binding.tvChipAllCount.text = alertsDto.size.toString()
                        binding.tvChipExpiryCount.text = alertsDto.size.toString()
                    }
                }
                
                launch {
                    viewModel.message.collect { msg ->
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

            val finalCategory = if (category.isEmpty()) "Pantry • Storage" else category
            val finalExpiry = if (expiry.isEmpty()) "Soon" else expiry

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
}
