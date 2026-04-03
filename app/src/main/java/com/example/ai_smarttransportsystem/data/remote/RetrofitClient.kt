package com.example.ai_smarttransportsystem.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    internal const val BASE_URL = "http://192.168.100.7:8000/"  // Real device via USB/WiFi → laptop IP
    // For emulator: "http://10.0.2.2:8000/"
    // For production: "https://your-backend.com/"

    // Backend runs TSP + ORS + Firestore — can take 60–120 s.
    // Default Retrofit timeout is 10 s → always throws IOException before 200 OK arrives.
    // Set all timeouts to 3 minutes so the app waits for the real response.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)   // time to establish TCP connection
        .readTimeout(180, TimeUnit.SECONDS)     // time to wait for response body  ← key fix
        .writeTimeout(30, TimeUnit.SECONDS)     // time to send request body
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}