package com.example.pantrypal.ui.pantry

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantrypal.R
import com.example.pantrypal.data.model.PantryCategory
import com.example.pantrypal.databinding.ItemCategoryCardBinding

class CategoryAdapter(
    private val onCategoryClick: (PantryCategory) -> Unit
) : ListAdapter<PantryCategory, CategoryAdapter.ViewHolder>(DiffCallback()) {

    private var selectedCategoryName: String? = null

    fun setSelectedCategory(categoryName: String?) {
        selectedCategoryName = categoryName
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCategoryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: PantryCategory) {
            val context = binding.root.context
            binding.tvCategoryName.text = category.name
            binding.tvCategoryCount.text = category.count.toString()
            binding.ivCategoryIcon.setImageResource(category.iconResId)

            val isSelected = category.name.equals(selectedCategoryName, ignoreCase = true)
            if (isSelected) {
                binding.cardCategory.strokeColor = ContextCompat.getColor(context, R.color.pantry_primary)
                binding.cardCategory.strokeWidth = 4
                binding.cardCategory.setCardBackgroundColor(ContextCompat.getColor(context, R.color.shopping_sync_card_bg))
            } else {
                binding.cardCategory.strokeColor = ContextCompat.getColor(context, R.color.pantry_tag_gray_bg)
                binding.cardCategory.strokeWidth = 2
                binding.cardCategory.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            }

            binding.root.setOnClickListener {
                onCategoryClick(category)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PantryCategory>() {
        override fun areItemsTheSame(oldItem: PantryCategory, newItem: PantryCategory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PantryCategory, newItem: PantryCategory): Boolean {
            return oldItem == newItem
        }
    }
}
