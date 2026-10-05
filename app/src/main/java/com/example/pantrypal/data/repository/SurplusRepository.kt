package com.example.pantrypal.data.repository

import com.example.pantrypal.data.model.SurplusDonationDto
import com.example.pantrypal.data.model.SurplusHubDto
import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi

/** Data access for the surplus feature. donorId is supplied by the app's login flow. */
class SurplusRepository {
    @OptIn(InternalSerializationApi::class)
    suspend fun getHubs(): List<SurplusHubDto> = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_hubs").select().decodeList()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun getDonations(donorId: String): List<SurplusDonationDto> = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_donations").select {
            filter { eq("donor_id", donorId) }
        }.decodeList()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun createDonation(donation: SurplusDonationDto) = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("surplus_donations").insert(donation)
    }
}
