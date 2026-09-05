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
    val id: String? = null,
    // Discriminador de entidad: "plan" opera sobre la hoja Planes; "user" sobre la hoja Usuarios.
    // Si es null, la operación es sobre movimientos (comportamiento por defecto).
    val entity: String? = null,
    val plan: CuotaPlan? = null,
    val user: Usuario? = null,
    // Mes "yyyy-MM" de las acciones de corte (hoy solo SNAPSHOT_CUOTAS).
    val mes: String? = null
)

data class ImageInfo(
    val base64: String,
    val contentType: String = "image/jpeg"
)

data class WebAppResponse(
    val status: String,
    val data: List<Movement>? = null,
    val plans: List<CuotaPlan>? = null,
    val users: List<Usuario>? = null,
    val message: String? = null
)

interface SheetsService {
    @GET
    suspend fun getMovements(
        @Url url: String
    ): Response<WebAppResponse>

    // Mismo endpoint que getMovements pero con action=GET_PLANS; devuelve WebAppResponse.plans.
    @GET
    suspend fun getPlans(
        @Url url: String
    ): Response<WebAppResponse>

    // Mismo endpoint pero con action=GET_USERS; devuelve WebAppResponse.users.
    @GET
    suspend fun getUsers(
        @Url url: String
    ): Response<WebAppResponse>

    @POST
    suspend fun addMovement(
        @Url url: String,
        @Body request: WebAppRequest
    ): Response<WebAppResponse>
}
