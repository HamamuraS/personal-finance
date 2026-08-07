package com.example.data

import com.example.BuildConfig

/**
 * Configuración de conexión, fijada en tiempo de compilación.
 *
 * Antes se editaba desde Ajustes y se guardaba en SharedPreferences, lo que obligaba a configurar
 * cada teléfono a mano. Ahora se hornea en el APK: se entrega ya funcionando y la pantalla de
 * Ajustes no muestra ni la URL ni el ID de la carpeta.
 *
 * **Dónde se cambian los valores:** en el archivo `.env` de la raíz del proyecto (está en
 * `.gitignore`). El plugin de secrets los inyecta como campos de `BuildConfig` al compilar.
 * `.env.example` tiene la plantilla con los valores vacíos.
 *
 * No están hardcodeados acá a propósito: la URL del Web App es un endpoint sin autenticación —
 * quien la tenga puede leer y escribir la planilla entera — y este repo es público.
 */
object AppConfig {

    /** URL del Web App de Apps Script (deployment `/exec`). */
    val SCRIPT_URL: String = BuildConfig.SCRIPT_URL.trim()

    /** Carpeta de Drive donde se suben los tickets. */
    val DRIVE_FOLDER_ID: String = BuildConfig.DRIVE_FOLDER_ID.trim()

    /**
     * ¿El build trae una configuración usable? Si no (por ejemplo un clone sin `.env`), la app
     * arranca en Modo Local (Demo) en vez de fallar contra una URL inválida.
     */
    val isConfigured: Boolean =
        SCRIPT_URL.startsWith("https://script.google.com/") && DRIVE_FOLDER_ID.isNotEmpty()
}
