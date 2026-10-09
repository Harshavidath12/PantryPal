package com.example.pantrypal.data.repository

import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class ProfileUpdateDto(
    val name: String? = null,
    val profile_picture: String? = null
)

class ProfileRepository {

    private val client = SupabaseProvider.client

    // CRUD OPERATION 1: Update the profile name and picture
    suspend fun updateProfileDetails(userId: Long, newName: String, newPictureUrl: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Update both name and profile_picture
                client.postgrest["users"].update(
                    {
                        ProfileUpdateDto(name = newName, profile_picture = newPictureUrl)
                    }
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    // CRUD OPERATION 2: Delete the profile picture (Sets it to NULL)
    suspend fun deleteProfilePicture(userId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // By omitting 'name', it only updates 'profile_picture' to null in the DB
                client.postgrest["users"].update(
                    {
                        ProfileUpdateDto(profile_picture = null)
                    }
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
