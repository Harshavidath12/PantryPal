package com.example.pantrypal.data.repository

import com.example.pantrypal.data.model.SurplusDonationDto
import com.example.pantrypal.data.model.SurplusHubDto
import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Public guest-mode data access for the community surplus board. */
class SurplusRepository {
    @OptIn(InternalSerializationApi::class)
    suspend fun getHubs(): List<SurplusHubDto> = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_hubs").select {
            filter { eq("is_active", true) }
        }.decodeList()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getDonations(): List<DonationHistoryDto> = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_donations").select {
            order("created_at", Order.DESCENDING)
        }.decodeList()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun createDonation(donation: SurplusDonationDto): String = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_donations").insert(donation.copy(donorId = null)) {
            select()
        }.decodeSingle<SurplusDonationDto>().id ?: error("Donation was saved, but its ID was not returned.")
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun updateDonation(donation: DonationHistoryDto, foodName: String, quantity: String, bestBefore: String, pickupWindow: String, status: String = donation.status): Boolean = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_donations").update({
            set("food_name", foodName)
            set("quantity", quantity)
            set("best_before", bestBefore)
            set("pickup_window", pickupWindow)
            set("status", status)
        }) {
            select()
            filter {
                eq("id", donation.id)
                eq("status", "PENDING")
            }
        }.decodeList<DonationHistoryDto>().isNotEmpty()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun deleteDonation(donation: DonationHistoryDto): Boolean = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_donations").delete {
            select()
            filter {
                eq("id", donation.id)
                eq("status", "PENDING")
            }
        }.decodeList<DonationHistoryDto>().isNotEmpty()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun advanceDonationStatus(donation: DonationHistoryDto): Boolean = withContext(Dispatchers.IO) {
        val nextStatus = when (donation.status.uppercase()) {
            "PENDING" -> "CLAIMED"
            "CLAIMED" -> "PICKED_UP"
            else -> return@withContext false
        }
        if (!meetsCourierMinimum(donation.quantity)) return@withContext false
        SupabaseProvider.client.from("surplus_donations").update({
            set("status", nextStatus)
        }) {
            select()
            filter {
                eq("id", donation.id)
                eq("status", donation.status)
            }
        }.decodeList<DonationHistoryDto>().isNotEmpty()
    }

    private fun meetsCourierMinimum(quantity: String): Boolean {
        val amount = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(kg|kgs|units?)", RegexOption.IGNORE_CASE)
            .find(quantity) ?: return false
        val value = amount.groupValues[1].toDoubleOrNull() ?: return false
        return if (amount.groupValues[2].startsWith("unit", true)) value >= 15 && value % 1.0 == 0.0 else value >= 10.0
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getImpact(): DonationImpact = withContext(Dispatchers.IO) {
        val donations = getDonations()
        val completed = donations.filter { it.status in setOf("PICKED_UP", "DROPPED_OFF") }
        val savedKg = completed.sumOf { donation ->
            Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(?:kg|kgs|g|grams?)\\b", RegexOption.IGNORE_CASE)
                .find(donation.quantity)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        }
        DonationImpact(
            donationCount = donations.size,
            completedCount = completed.size,
            foodSavedKg = savedKg,
            meals = (savedKg * 8.3).toInt(),
            co2SavedKg = savedKg * 2.0
        )
    }
}

@Serializable
data class DonationHistoryDto(
    val id: String,
    @SerialName("donor_id") val donorId: String? = null,
    @SerialName("hub_id") val hubId: String,
    @SerialName("food_name") val foodName: String,
    val quantity: String,
    @SerialName("best_before") val bestBefore: String,
    @SerialName("pickup_window") val pickupWindow: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null
)

data class DonationImpact(
    val donationCount: Int,
    val completedCount: Int,
    val foodSavedKg: Double,
    val meals: Int,
    val co2SavedKg: Double
)
