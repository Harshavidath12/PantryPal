package com.example.pantrypal.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.Auth
object SupabaseProvider {
    val client = createSupabaseClient(
        supabaseUrl = "https://pkspykytswumrkjevbrg.supabase.co", // From your screen
        supabaseKey = "sb_publishable_qvnJTVxUXlQ9ICXpL1cd6g_azhdaxLh" // Actual Supabase anon key
    ) {
        install(Postgrest)
        install(Auth)
    }
}
