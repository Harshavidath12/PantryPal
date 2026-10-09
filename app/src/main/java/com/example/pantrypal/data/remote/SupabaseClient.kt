package com.example.pantrypal.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://pkspykytswumrkjevbrg.supabase.co",
        supabaseKey = "sb_publishable_qvnJTVxUXlQ9ICXpL1cd6g_azhdaxLh"
    ) {
        defaultSerializer = KotlinXSerializer(Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            isLenient = true
        })
        install(Postgrest)
        install(Auth)
    }
}
