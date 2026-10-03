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
import com.example.pantrypal.data.model.PrimaryButtonType
import com.example.pantrypal.data.repository.NotificationRepositoryImpl
import com.example.pantrypal.databinding.ActivityMainBinding
import com.example.pantrypal.databinding.DialogAddAlertBinding
import com.example.pantrypal.databinding.DialogRecipeIdeasBinding
import com.example.pantrypal.ui.notifications.NotificationAdapter
import com.example.pantrypal.ui.notifications.NotificationViewModel
import com.example.pantrypal.ui.notifications.NotificationViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

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
        setupFilterChips()
        setupClickListeners()
        observeUiState()
    }

    private fun setupArchitecture() {
        val repository = NotificationRepositoryImpl()
        val factory = NotificationViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[NotificationViewModel::class.java]
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(
            onPrimaryActionClicked = { alert ->
                if (alert.primaryButtonType == PrimaryButtonType.RECIPE_IDEAS) {
                    showRecipeIdeasDialog(alert)
                } else {
                    viewModel.toggleRestock(alert.id, alert.title)
                }
            },
            onMarkConsumedClicked = { alert ->
                viewModel.markConsumed(alert.id, alert.title)
            },
            onDeleteAlertClicked = { alert ->
                viewModel.deleteNotification(alert.id, alert.title)
            },
            onSharedUpdateClicked = { update ->
                Toast.makeText(this, "Opening details for ${update.userName}'s update", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            viewModel.setFilter(FilterType.ALL)
        }

        binding.chipExpiry.setOnClickListener {
            viewModel.setFilter(FilterType.EXPIRY)
        }

        binding.chipSyncShared.setOnClickListener {
            viewModel.setFilter(FilterType.SYNC_SHARED)
        }
    }

    private fun setupClickListeners() {
        binding.btnSync.setOnClickListener {
            viewModel.refreshData()
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

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Submit items to adapter
                    adapter.submitList(state.items)

                    // Update Chip Counts
                    binding.tvChipAllCount.text = state.totalCount.toString()
                    binding.tvChipExpiryCount.text = state.expiryCount.toString()

                    // Update Chip Selected Visual States
                    updateChipVisuals(state.activeFilter)

                    // Show snackbar message if present
                    state.userMessage?.let { msg ->
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                            .setAction("Dismiss") { viewModel.clearUserMessage() }
                            .show()
                        viewModel.clearUserMessage()
                    }
                }
            }
        }
    }

    private fun updateChipVisuals(filter: FilterType) {
        // Reset all
        binding.chipAll.setBackgroundResource(R.drawable.bg_chip_unselected)
        binding.tvChipAllText.setTextColor(ContextCompat.getColor(this, R.color.chip_unselected_text))
        binding.tvChipAllCount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        binding.chipExpiry.setBackgroundResource(R.drawable.bg_chip_unselected)
        binding.tvChipExpiryText.setTextColor(ContextCompat.getColor(this, R.color.chip_unselected_text))
        binding.tvChipExpiryCount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        binding.chipSyncShared.setBackgroundResource(R.drawable.bg_chip_unselected)
        binding.tvChipSyncText.setTextColor(ContextCompat.getColor(this, R.color.chip_unselected_text))

        when (filter) {
            FilterType.ALL -> {
                binding.chipAll.setBackgroundResource(R.drawable.bg_chip_selected)
                binding.tvChipAllText.setTextColor(ContextCompat.getColor(this, R.color.chip_selected_text))
                binding.tvChipAllCount.setTextColor(ContextCompat.getColor(this, R.color.pantry_primary))
            }
            FilterType.EXPIRY -> {
                binding.chipExpiry.setBackgroundResource(R.drawable.bg_chip_selected)
                binding.tvChipExpiryText.setTextColor(ContextCompat.getColor(this, R.color.chip_selected_text))
                binding.tvChipExpiryCount.setTextColor(ContextCompat.getColor(this, R.color.pantry_primary))
            }
            FilterType.SYNC_SHARED -> {
                binding.chipSyncShared.setBackgroundResource(R.drawable.bg_chip_selected)
                binding.tvChipSyncText.setTextColor(ContextCompat.getColor(this, R.color.chip_selected_text))
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

            viewModel.addNewAlert(title, finalCategory, finalExpiry, urgency)
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
