package com.example.pantrypal.ui.notifications

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantrypal.R
import com.example.pantrypal.data.model.AlertUrgency
import com.example.pantrypal.data.model.NotificationItem
import com.example.pantrypal.data.model.PrimaryButtonType
import com.example.pantrypal.databinding.ItemExpiryAlertBinding
import com.example.pantrypal.databinding.ItemSectionHeaderBinding
import com.example.pantrypal.databinding.ItemSharedUpdateBinding

class NotificationAdapter(
    private val onPrimaryActionClicked: (NotificationItem.ExpiryAlert) -> Unit,
    private val onMarkConsumedClicked: (NotificationItem.ExpiryAlert) -> Unit,
    private val onDeleteAlertClicked: (NotificationItem.ExpiryAlert) -> Unit,
    private val onSharedUpdateClicked: (NotificationItem.SharedUpdate) -> Unit,
) : ListAdapter<NotificationItem, RecyclerView.ViewHolder>(NotificationDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_EXPIRY_ALERT = 1
        private const val TYPE_SHARED_UPDATE = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NotificationItem.SectionHeader -> TYPE_HEADER
            is NotificationItem.ExpiryAlert -> TYPE_EXPIRY_ALERT
            is NotificationItem.SharedUpdate -> TYPE_SHARED_UPDATE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemSectionHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            TYPE_EXPIRY_ALERT -> {
                val binding = ItemExpiryAlertBinding.inflate(inflater, parent, false)
                ExpiryAlertViewHolder(binding)
            }
            TYPE_SHARED_UPDATE -> {
                val binding = ItemSharedUpdateBinding.inflate(inflater, parent, false)
                SharedUpdateViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NotificationItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is NotificationItem.ExpiryAlert -> (holder as ExpiryAlertViewHolder).bind(item)
            is NotificationItem.SharedUpdate -> (holder as SharedUpdateViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(private val binding: ItemSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NotificationItem.SectionHeader) {
            binding.tvSectionTitle.text = item.title

            if (item.tagText != null) {
                binding.tvSectionTag.visibility = View.VISIBLE
                binding.tvSectionTag.text = item.tagText

                if (item.isRedTag) {
                    binding.tvSectionTag.setBackgroundResource(R.drawable.bg_tag_red)
                    binding.tvSectionTag.setTextColor(
                        ContextCompat.getColor(binding.root.context, R.color.pantry_tag_red_text)
                    )
                } else {
                    binding.tvSectionTag.setBackgroundResource(R.drawable.bg_tag_gray)
                    binding.tvSectionTag.setTextColor(
                        ContextCompat.getColor(binding.root.context, R.color.text_secondary)
                    )
                }
            } else {
                binding.tvSectionTag.visibility = View.GONE
            }

            if (item.itemCountText != null) {
                binding.tvSectionCount.visibility = View.VISIBLE
                binding.tvSectionCount.text = item.itemCountText
            } else {
                binding.tvSectionCount.visibility = View.GONE
            }
        }
    }

    inner class ExpiryAlertViewHolder(private val binding: ItemExpiryAlertBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem.ExpiryAlert) {
            val context = binding.root.context

            binding.tvItemTitle.text = item.title
            binding.tvCategoryLocation.text = item.categoryAndLocation
            binding.tvExpiryDate.text = item.expiryText
            binding.ivFoodImage.setImageResource(item.imageResId)

            // Expiry Urgency Color
            if (item.urgency == AlertUrgency.EXPIRING_TODAY) {
                binding.tvExpiryDate.setTextColor(
                    ContextCompat.getColor(context, R.color.pantry_tag_red_text)
                )
            } else {
                binding.tvExpiryDate.setTextColor(
                    ContextCompat.getColor(context, R.color.pantry_tag_orange_text)
                )
            }

            // Primary Action Button configuration
            if (item.primaryButtonType == PrimaryButtonType.RECIPE_IDEAS) {
                binding.btnPrimaryAction.text = context.getString(R.string.btn_recipe_ideas)
                binding.btnPrimaryAction.setIconResource(R.drawable.ic_sparkles)
            } else {
                if (item.isRestockAdded) {
                    binding.btnPrimaryAction.text = "Added to Restock"
                } else {
                    binding.btnPrimaryAction.text = context.getString(R.string.btn_add_to_restock)
                }
                binding.btnPrimaryAction.setIconResource(R.drawable.ic_cart_add)
            }

            // Listeners
            binding.btnPrimaryAction.setOnClickListener {
                onPrimaryActionClicked(item)
            }

            binding.btnMarkConsumed.setOnClickListener {
                onMarkConsumedClicked(item)
            }

            binding.btnMoreOptions.setOnClickListener { v ->
                showPopupMenu(v, item)
            }
        }

        private fun showPopupMenu(view: View, item: NotificationItem.ExpiryAlert) {
            val context = view.context
            val inflater = LayoutInflater.from(context)
            val popupView = inflater.inflate(R.layout.popup_dismiss_alert, null)

            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            // Make it dismissible by clicking outside
            popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            popupWindow.elevation = 16f

            // Handle the click on the custom pill layout
            popupView.setOnClickListener {
                onDeleteAlertClicked(item)
                popupWindow.dismiss()
            }

            // Offset the popup so it aligns nicely with the 3 dots
            // Negative X offset shifts it to the left to align right-edges roughly
            popupWindow.showAsDropDown(view, -320, -40)
        }
    }

    inner class SharedUpdateViewHolder(private val binding: ItemSharedUpdateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem.SharedUpdate) {
            binding.tvAvatarInitials.text = item.userInitials
            binding.flAvatarContainer.setBackgroundResource(item.avatarBgColorRes)

            val fullText = "${item.userName} ${item.actionText}"
            binding.tvSharedActionText.text = fullText
            binding.tvTimeAgo.text = item.timeAgo
            binding.tvDetailsText.text = item.detailsText

            if (item.linkText != null) {
                binding.tvLinkText.visibility = View.VISIBLE
                binding.tvLinkText.text = item.linkText
            } else {
                binding.tvLinkText.visibility = View.GONE
            }

            if (item.isSynced) {
                binding.tvSyncedBadge.visibility = View.VISIBLE
            } else {
                binding.tvSyncedBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onSharedUpdateClicked(item)
            }
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<NotificationItem>() {
        override fun areItemsTheSame(
            oldItem: NotificationItem,
            newItem: NotificationItem
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: NotificationItem,
            newItem: NotificationItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}
