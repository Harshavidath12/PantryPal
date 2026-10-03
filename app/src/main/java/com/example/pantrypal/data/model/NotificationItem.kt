package com.example.pantrypal.data.model

import androidx.annotation.DrawableRes

sealed class NotificationItem {
    abstract val id: String

    data class SectionHeader(
        override val id: String,
        val title: String,
        val tagText: String? = null,
        val isRedTag: Boolean = false,
        val itemCountText: String? = null
    ) : NotificationItem()

    data class ExpiryAlert(
        override val id: String,
        val title: String,
        val categoryAndLocation: String,
        val expiryText: String,
        val urgency: AlertUrgency,
        @DrawableRes val imageResId: Int,
        val isRestockAdded: Boolean = false,
        val isConsumed: Boolean = false,
        val primaryButtonType: PrimaryButtonType = PrimaryButtonType.RECIPE_IDEAS
    ) : NotificationItem()

    data class SharedUpdate(
        override val id: String,
        val userName: String,
        val userInitials: String,
        val actionText: String,
        val timeAgo: String,
        val detailsText: String,
        val linkText: String? = null,
        val isSynced: Boolean = false,
        val avatarBgColorRes: Int
    ) : NotificationItem()
}

enum class PrimaryButtonType {
    RECIPE_IDEAS,
    ADD_TO_RESTOCK
}
