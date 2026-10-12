package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// Modelos de datos para el Web App
data class WebAppRequest(
    val action: String,
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

/**
 * Protocolo del Web App de Apps Script: el valor de `action` (en el query string de los GET y en el
 * cuerpo de los POST), las entidades y el `status` de éxito. Son strings que el script compara tal
 * cual: no se cambian sin cambiar el script.
 */
object WebApp {
    const val GET_MOVEMENTS = "GET"
    const val GET_PLANS = "GET_PLANS"
    const val GET_USERS = "GET_USERS"
    /** Upsert por id: reemplaza la fila si existe, la agrega si no. Toda escritura de la app va así. */
    const val PUT = "PUT"
    const val DELETE = "DELETE"
    const val SNAPSHOT_CUOTAS = "SNAPSHOT_CUOTAS"

    const val ENTITY_PLAN = "plan"
    const val ENTITY_USER = "user"

    const val FILTRO_PENDIENTES = "pendientes"
    const val FILTRO_PAGOS = "pagos"

    const val SUCCESS = "SUCCESS"
}

/** Un solo endpoint: todo GET y todo POST van a la URL del Web App (deployment `/exec`). */
interface SheetsService {
    @GET
    suspend fun get(@Url url: String): Response<WebAppResponse>

    @POST
    suspend fun post(@Url url: String, @Body request: WebAppRequest): Response<WebAppResponse>
}

/** Cliente HTTP único del proceso (antes cada repositorio armaba el suyo). */
object WebAppClient {

    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val service: SheetsService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            // El cuerpo lleva la planilla entera y los tickets en base64, y la URL es una credencial:
            // solo se loguea en los builds de desarrollo.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // El script toma un candado de hasta 30 s antes de escribir y GET_PLANS recorre todas las
            // hojas: con los 10 s por defecto, escrituras que el servidor sí hacía volvían como error.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            // La URL real llega por @Url; Retrofit igual exige una base.
            .baseUrl("https://script.google.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SheetsService::class.java)
    }
}
