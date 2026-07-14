package com.example.data.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class CobaltRequest(
    val url: String,
    val downloadMode: String = "audio",
    val audioFormat: String = "mp3",
    val audioBitrate: String = "320",
    val isAudioOnly: Boolean = true
)

data class CobaltResponse(
    val status: String, // "stream", "redirect", "error"
    val url: String?,
    val filename: String?,
    val text: String?
)

interface CobaltService {
    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("/")
    suspend fun convertVideo(@Body request: CobaltRequest): CobaltResponse
}

object CobaltClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: CobaltService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.cobalt.tools/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CobaltService::class.java)
    }
}
