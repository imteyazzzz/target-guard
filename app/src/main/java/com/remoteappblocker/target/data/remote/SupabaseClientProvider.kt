package com.remoteappblocker.target.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.cio.CIO

object SupabaseClientProvider {
    const val SUPABASE_URL = "https://hyzmhejedapqzkjujqku.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imh5em1oZWplZGFwcXpranVqcWt1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE3OTQ5MzgsImV4cCI6MjEwNzM3MDkzOH0.12loWkimS58HQHmbwzot3VZCOhSwm-3p8qdrZRt-_Rg"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            httpEngine = CIO.create()
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
