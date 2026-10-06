package com.example.pantrypal.data.repository

import com.example.pantrypal.data.model.SurplusDonationDto
import com.example.pantrypal.data.model.SurplusHubDto
import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Data access for the surplus feature. donorId is supplied by the app's login flow. */
class SurplusRepository {
    @OptIn(InternalSerializationApi::class)
    suspend fun getHubs(): List<SurplusHubDto> = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_hubs").select {
            filter { eq("is_active", true) }
        }.decodeList()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getDonations(donorId: String): List<DonationHistoryDto> = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_donations").select {
            filter { eq("donor_id", donorId) }
        }.decodeList()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun createDonation(donorId: String, donation: SurplusDonationDto) = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_donations").insert(donation.copy(donorId = donorId))
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getImpact(donorId: String): DonationImpact = withContext(Dispatchers.IO) {
        val donations = getDonations(donorId)
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
