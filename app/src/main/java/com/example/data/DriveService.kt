package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class DriveService(private val context: Context) {

    private val client = OkHttpClient()
    private val folderId = AppConfig.DRIVE_FOLDER_ID
    private val driveScope = "oauth2:https://www.googleapis.com/auth/drive.file"

    suspend fun uploadTicket(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("DriveService", "Starting upload for URI: $uri")
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                Log.e("DriveService", "No Google account found. You must be signed in to upload to Drive.")
                return@withContext null
            }
            
            Log.d("DriveService", "Account found: ${account.email}, getting token...")
            val token = GoogleAuthUtil.getToken(context, account.account!!, driveScope)
            Log.d("DriveService", "Token obtained successfully")

            // Copy URI to temporary file
            val tempFile = File(context.cacheDir, "upload_ticket_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Metadata
            val metadata = JSONObject().apply {
                put("name", "ticket_${System.currentTimeMillis()}.jpg")
                put("parents", listOf(folderId))
            }.toString()

            val metadataPart = metadata.toRequestBody("application/json; charset=UTF-8".toMediaType())
            val mediaPart = tempFile.asRequestBody("image/jpeg".toMediaType())

            // Actually Drive API v3 multipart upload:
            // POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart
            
            val boundary = "boundary_string_${System.currentTimeMillis()}"
            
            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,webViewLink")
                .header("Authorization", "Bearer $token")
                .post(
                    MultipartBody.Builder(boundary)
                        .setType("multipart/related".toMediaType())
                        .addPart(metadataPart)
                        .addPart(mediaPart)
                        .build()
                )
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val id = json.getString("id")
                // We return the direct view link if possible, or just the ID to construct it
                json.optString("webViewLink", "https://drive.google.com/file/d/$id/view")
            } else {
                Log.e("DriveService", "Upload failed: ${response.code} - $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e("DriveService", "Exception uploading ticket", e)
            null
        }
    }
}
