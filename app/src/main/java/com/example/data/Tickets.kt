package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** Fotos de tickets: se comprimen a disco antes de encolar el movimiento que las lleva. */
object Tickets {

    private const val TAG = "Tickets"

    /**
     * Comprime el ticket a JPEG (70 %, corrigiendo la orientación EXIF) y lo deja en `cacheDir`.
     * Devuelve la ruta, o "" si no se pudo. Va a disco y no dentro de la cola porque el base64 de
     * una foto supera de largo el límite de 10 KB del `Data` de WorkManager.
     */
    fun comprimir(context: Context, ticketUri: Uri, movementId: String): String {
        return try {
            val bitmap = context.contentResolver.openInputStream(ticketUri).use {
                BitmapFactory.decodeStream(it)
            } ?: return ""

            val rotado = try {
                context.contentResolver.openInputStream(ticketUri).use { exifStream ->
                    if (exifStream != null) {
                        val orientation = ExifInterface(exifStream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        rotarSegunExif(bitmap, orientation)
                    } else bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error leyendo EXIF", e)
                bitmap
            }

            val carpeta = File(context.cacheDir, "pending_tickets").apply { mkdirs() }
            val destino = File(carpeta, "$movementId.jpg")
            destino.outputStream().use { out ->
                rotado.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }
            Log.d(TAG, "Ticket comprimido en ${destino.path} (${destino.length()} bytes)")
            destino.path
        } catch (e: Exception) {
            Log.e(TAG, "Error comprimiendo imagen", e)
            ""
        }
    }

    /** Rota un bitmap según la orientación EXIF. */
    private fun rotarSegunExif(img: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return img
        }
        return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    }
}
