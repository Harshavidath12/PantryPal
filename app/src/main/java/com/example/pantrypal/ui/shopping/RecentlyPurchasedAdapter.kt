package com.example.pantrypal.ui.shopping

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantrypal.data.model.ShoppingItem
import com.example.pantrypal.databinding.ItemRecentlyPurchasedCardBinding

class RecentlyPurchasedAdapter(
    private val onMoveToPantryClick: (ShoppingItem) -> Unit
) : ListAdapter<ShoppingItem, RecentlyPurchasedAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentlyPurchasedCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentlyPurchasedCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShoppingItem) {
            binding.tvItemTitle.text = item.name
            binding.tvSubtitle.text = item.secondaryText.ifBlank { "Bought today" }

            binding.btnMoveToPantry.setOnClickListener {
                onMoveToPantryClick(item)
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
