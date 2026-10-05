package com.example.pantrypal.ui.pantry

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pantrypal.R
import com.example.pantrypal.data.model.PantryCategory
import com.example.pantrypal.databinding.ItemCategoryChipHorizontalBinding

class CategoryChipHorizontalAdapter(
    private val onCategoryClick: (PantryCategory) -> Unit
) : ListAdapter<PantryCategory, CategoryChipHorizontalAdapter.ViewHolder>(DiffCallback()) {

    private var selectedCategoryName: String? = null

    fun setSelectedCategory(categoryName: String?) {
        selectedCategoryName = categoryName
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryChipHorizontalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCategoryChipHorizontalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: PantryCategory) {
            val context = binding.root.context
            binding.tvCategoryChipName.text = category.name
            binding.tvCategoryChipCount.text = category.count.toString()

            val isSelected = category.name.equals(selectedCategoryName, ignoreCase = true) ||
                    (selectedCategoryName.isNullOrBlank() && category.name.equals("All", ignoreCase = true))

            if (isSelected) {
                binding.llCategoryChip.setBackgroundResource(R.drawable.bg_chip_selected)
                binding.tvCategoryChipName.setTextColor(ContextCompat.getColor(context, R.color.chip_selected_text))
                binding.tvCategoryChipCount.setTextColor(ContextCompat.getColor(context, R.color.pantry_primary))
            } else {
                binding.llCategoryChip.setBackgroundResource(R.drawable.bg_chip_unselected)
                binding.tvCategoryChipName.setTextColor(ContextCompat.getColor(context, R.color.chip_unselected_text))
                binding.tvCategoryChipCount.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
