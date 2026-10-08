package com.example.pantrypal.ui.pantry

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantrypal.R
import com.example.pantrypal.data.model.PantryItem
import com.example.pantrypal.databinding.ItemPantryOverviewCardBinding

class PantryOverviewAdapter(
    private val onItemClick: (PantryItem) -> Unit
) : ListAdapter<PantryItem, PantryOverviewAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPantryOverviewCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPantryOverviewCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PantryItem) {
            val context = binding.root.context

            binding.tvItemTitle.text = item.title
            binding.tvItemDetails.text = item.location

            binding.pbStockLevel.progress = item.stockPercent
            binding.tvStockText.text = item.stockText

            if (item.stockPercent <= 25) {
                binding.pbStockLevel.progressDrawable = ContextCompat.getDrawable(context, R.drawable.bg_pill_status_low_stock)
            } else {
                binding.pbStockLevel.progressDrawable = ContextCompat.getDrawable(context, R.drawable.bg_pill_status_manual)
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
