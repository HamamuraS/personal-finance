package com.example.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

// Modelos de datos para el Web App
data class WebAppRequest(
    val action: String,
    val method: String? = null,
    val parameters: Map<String, Any>? = null,
    val body: Movement? = null,
    val imageInfo: ImageInfo? = null,
    val folderId: String? = null,
    val id: String? = null
)

data class ImageInfo(
    val base64: String,
    val contentType: String = "image/jpeg"
)

data class WebAppResponse(
    val status: String,
    val data: List<Movement>? = null,
    val message: String? = null
)

interface SheetsService {
    @GET
    suspend fun getMovements(
        @Url url: String
    ): Response<WebAppResponse>

    @POST
    suspend fun addMovement(
        @Url url: String,
        @Body request: WebAppRequest
    ): Response<WebAppResponse>
}
