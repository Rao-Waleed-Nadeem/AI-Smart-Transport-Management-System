package com.example.ai_smarttransportsystem.data.remote

import retrofit2.Response
import retrofit2.http.POST

interface ApiService {

    @POST("optimize-routes")
    suspend fun optimizeRoutes(): Response<Map<String, Any>>
}