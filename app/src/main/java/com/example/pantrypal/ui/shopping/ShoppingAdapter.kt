package com.example.pantrypal.ui.shopping

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantrypal.R
import com.example.pantrypal.data.model.ShoppingItem
import com.example.pantrypal.data.model.ShoppingStatusType
import com.example.pantrypal.databinding.ItemShoppingCardBinding

class ShoppingAdapter(
    private val onItemClick: (ShoppingItem) -> Unit
) : ListAdapter<ShoppingItem, ShoppingAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShoppingCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemShoppingCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShoppingItem) {
            val context = binding.root.context

            binding.tvItemTitle.text = item.name
            binding.tvQuantity.text = item.quantity

            // Checkbox icon
            if (item.isSelected) {
                binding.ivCheckbox.setImageResource(R.drawable.ic_checkbox_checked)
            } else {
                binding.ivCheckbox.setImageResource(R.drawable.ic_checkbox_unchecked)
            }

            // Status Badge configuration
            binding.tvStatusBadge.text = item.statusText
            when (item.statusType) {
                ShoppingStatusType.EXPIRED -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_status_expired)
                    binding.tvStatusBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.shopping_status_expired_text)
                    )
                }
                ShoppingStatusType.LOW_STOCK -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_status_low_stock)
                    binding.tvStatusBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.shopping_status_low_stock_text)
                    )
                }
                ShoppingStatusType.MANUAL -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_status_manual)
                    binding.tvStatusBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.shopping_status_manual_text)
                    )
                }
                ShoppingStatusType.PANTRY_EMPTY -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_status_empty)
                    binding.tvStatusBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.shopping_status_empty_text)
                    )
                }
            }

            // Secondary text
            val secText = item.secondaryText.trim()
            if (secText.isNotBlank()) {
                binding.tvSecondaryText.visibility = View.VISIBLE
                binding.tvSecondaryText.text = if (secText.startsWith("•")) secText else "• $secText"
            } else {
                binding.tvSecondaryText.visibility = View.GONE
            }

            // Click listener on card to toggle selection
            binding.cardShoppingItem.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ShoppingItem>() {
        override fun areItemsTheSame(oldItem: ShoppingItem, newItem: ShoppingItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ShoppingItem, newItem: ShoppingItem): Boolean {
            return oldItem == newItem
        }
    }
}
