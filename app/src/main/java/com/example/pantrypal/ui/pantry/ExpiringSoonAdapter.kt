package com.example.pantrypal.ui.pantry

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantrypal.R
import com.example.pantrypal.data.model.PantryItem
import com.example.pantrypal.databinding.ItemExpiringSoonCardBinding

class ExpiringSoonAdapter(
    private val onItemClick: (PantryItem) -> Unit
) : ListAdapter<PantryItem, ExpiringSoonAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExpiringSoonCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemExpiringSoonCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PantryItem) {
            val context = binding.root.context

            binding.tvItemTitle.text = item.title
            val exp = item.expiryText
            binding.tvExpiryBadge.text = if (exp.startsWith("•")) exp else "• $exp"
            val loc = item.location
            binding.tvItemDetails.text = if (loc.startsWith("•")) loc else "• $loc"

            val icon = item.iconResId ?: R.drawable.ic_leaf
            binding.ivItemIcon.setImageResource(icon)

            if (item.daysUntilExpiry <= 1) {
                binding.tvExpiryBadge.setBackgroundResource(R.drawable.bg_pill_status_expired)
                binding.tvExpiryBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.shopping_status_expired_text)
                )
            } else {
                binding.tvExpiryBadge.setBackgroundResource(R.drawable.bg_pill_status_low_stock)
                binding.tvExpiryBadge.setTextColor(
                    ContextCompat.getColor(context, R.color.shopping_status_low_stock_text)
                )
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PantryItem>() {
        override fun areItemsTheSame(oldItem: PantryItem, newItem: PantryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PantryItem, newItem: PantryItem): Boolean {
            return oldItem == newItem
        }
    }
}
