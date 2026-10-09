@file:OptIn(kotlinx.serialization.InternalSerializationApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)

package com.example.pantrypal.data.repository

import com.example.pantrypal.data.remote.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class UserDto(
    val id: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val email: String = "",
    val name: String = "",
    val password: String = "",
    val provider: String = "LOCAL",
    val role: String = "USER",
    @SerialName("profile_picture") val profilePicture: String? = null
)

@Serializable
data class UserInsertDto(
    @EncodeDefault val id: Long,
    @SerialName("created_at") @EncodeDefault val createdAt: String,
    @EncodeDefault val email: String,
    @EncodeDefault val name: String,
    @EncodeDefault val password: String,
    @EncodeDefault val provider: String = "LOCAL",
    @EncodeDefault val role: String = "USER"
)

class AuthRepository {

    suspend fun signUp(email: String, name: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        var isDbSuccess = false
        var dbError: Exception? = null

        // 1. Hash the password with BCrypt (cost factor 10)
        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(10))

        // 2. Format current date & time as UTC ISO 8601 timestamp
        val createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        // 3. Determine next sequential ID (e.g., 3 after 2)
        val nextId = try {
            val latestUsers = SupabaseProvider.client.from("users").select {
                order("id", order = Order.DESCENDING)
                limit(1)
            }.decodeList<UserDto>()
            (latestUsers.firstOrNull()?.id ?: 0L) + 1L
        } catch (_: Exception) {
            1L
        }

        // 4. Primary Operation: Save user directly to Supabase 'users' database table
        try {
            SupabaseProvider.client.from("users").insert(
                UserInsertDto(
                    id = nextId,
                    createdAt = createdAt,
                    email = email,
                    name = name,
                    password = hashedPassword,
                    provider = "LOCAL",
                    role = "USER"
                )
            )
            isDbSuccess = true
        } catch (e: Exception) {
            dbError = e
            e.printStackTrace()
            // Check if user record was inserted or already exists in 'users' table
            try {
                val existing = SupabaseProvider.client.from("users").select {
                    filter { eq("email", email) }
                }.decodeList<UserDto>()
                if (existing.isNotEmpty()) {
                    isDbSuccess = true
                }
            } catch (_: Exception) { }
        }

        // 5. Secondary Operation: Register with Supabase Auth (ignore GoTrue email domain/verification errors)
        try {
            SupabaseProvider.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (authEx: Exception) {
            authEx.printStackTrace()
        }

        // 6. Return success if database user is created or exists
        if (isDbSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(dbError ?: Exception("Account creation failed. Please try again."))
        }
    }

    suspend fun signIn(email: String, password: String): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            // 1. Check Supabase 'users' table for user by email
            val users = SupabaseProvider.client.from("users").select {
                filter {
                    eq("email", email)
                }
            }.decodeList<UserDto>()

            if (users.isNotEmpty()) {
                val user = users.first()
                val isPasswordMatch = try {
                    if (user.password.startsWith("$2a$") || user.password.startsWith("$2b$") || user.password.startsWith("$2y$")) {
                        BCrypt.checkpw(password, user.password)
                    } else {
                        user.password == password
                    }
                } catch (_: Exception) {
                    user.password == password
                }

                if (isPasswordMatch) {
                    try {
                        SupabaseProvider.client.auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }
                    } catch (authEx: Exception) {
                        authEx.printStackTrace()
                    }
                    Result.success(user)
                } else {
                    Result.failure(Exception("Invalid email or password"))
                }
            } else {
                // 2. Try Supabase Auth as secondary check
                try {
                    SupabaseProvider.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    Result.success(UserDto(email = email, name = "User", password = password, provider = "LOCAL", role = "USER"))
                } catch (_: Exception) {
                    Result.failure(Exception("Invalid email or password"))
                }
            }
        } catch (e: Exception) {
            // 3. Fallback Auth attempt
            try {
                SupabaseProvider.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                Result.success(UserDto(email = email, name = "User", password = password, provider = "LOCAL", role = "USER"))
            } catch (_: Exception) {
                Result.failure(Exception("Failed to sign in: ${e.message}"))
            }
        }
    }
}


