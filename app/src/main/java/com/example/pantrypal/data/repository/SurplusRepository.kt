package com.example.pantrypal.data.repository

import com.example.pantrypal.data.model.SurplusDonationDto
import com.example.pantrypal.data.model.SurplusHubDto
import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Data access for the surplus feature. donorId is supplied by the app's login flow. */
class SurplusRepository {
    fun currentDonorId(): String? = SupabaseProvider.client.auth.currentUserOrNull()?.id
    @OptIn(InternalSerializationApi::class)
    suspend fun getHubs(): List<SurplusHubDto> = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_hubs").select {
            filter { eq("is_active", true) }
        }.decodeList()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getDonations(): List<DonationHistoryDto> = withContext(Dispatchers.IO) {
        val donorId = currentDonorId() ?: return@withContext emptyList()
        SupabaseProvider.client.from("surplus_donations").select {
            filter { eq("donor_id", donorId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun createDonation(donation: SurplusDonationDto): String = withContext(Dispatchers.IO) {
        val donorId = currentDonorId() ?: error("Please sign in before submitting a donation.")
        SupabaseProvider.client.from("surplus_donations").insert(donation.copy(donorId = donorId)) {
            select()
        }.decodeSingle<SurplusDonationDto>().id ?: error("Donation was saved, but its ID was not returned.")
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun updateDonation(donation: DonationHistoryDto, foodName: String, quantity: String, bestBefore: String, pickupWindow: String): Boolean = withContext(Dispatchers.IO) {
        val donorId = currentDonorId() ?: error("Please sign in before editing a donation.")
        SupabaseProvider.client.from("surplus_donations").update({
            set("food_name", foodName)
            set("quantity", quantity)
            set("best_before", bestBefore)
            set("pickup_window", pickupWindow)
        }) {
            select()
            filter {
                eq("id", donation.id)
                eq("donor_id", donorId)
                eq("status", "PENDING")
            }
        }.decodeList<DonationHistoryDto>().isNotEmpty()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun deleteDonation(donation: DonationHistoryDto): Boolean = withContext(Dispatchers.IO) {
        val donorId = currentDonorId() ?: error("Please sign in before deleting a donation.")
        SupabaseProvider.client.from("surplus_donations").delete {
            select()
            filter {
                eq("id", donation.id)
                eq("donor_id", donorId)
                eq("status", "PENDING")
            }
        }.decodeList<DonationHistoryDto>().isNotEmpty()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getImpact(): DonationImpact = withContext(Dispatchers.IO) {
        val donations = getDonations()
        val completed = donations.filter { it.status == "PICKED_UP" }
        val savedKg = completed.sumOf { donation ->
            Regex("[0-9]+(?:\\.[0-9]+)?").find(donation.quantity)?.value?.toDoubleOrNull() ?: 0.0
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
    @SerialName("donor_id") val donorId: String,
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
