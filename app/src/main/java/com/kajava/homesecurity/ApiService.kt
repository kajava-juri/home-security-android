package com.kajava.homesecurity.api

import com.kajava.homesecurity.models.DevicesResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("devices")
    suspend fun getDevices(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100
    ): Response<DevicesResponse>
}

object ApiConfig {
    const val API_URL = "http://192.168.1.234:8081/api/" // Replace with your actual API URL
}