package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GoogleDriveFileMetadata(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val createdTime: Long,
    val formattedDate: String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(createdTime))
)

class GoogleDriveApiClient(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveApiClient"
        private const val BASE_DRIVE_URL = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_DRIVE_URL = "https://www.googleapis.com/upload/drive/v3"

        const val APP_ROOT_FOLDER_NAME = "الحكيمي للأدوية والمستلزمات الطبية"
        const val BACKUPS_FOLDER_NAME = "Backups"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Finds an existing folder or creates it if not found.
     */
    suspend fun getOrCreateFolder(
        accessToken: String,
        folderName: String,
        parentFolderId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(IOException("NO_INTERNET"))
        }

        try {
            // 1. Search for existing folder
            val escapedName = folderName.replace("'", "\\'")
            var query = "mimeType = 'application/vnd.google-apps.folder' and name = '$escapedName' and trashed = false"
            if (!parentFolderId.isNullOrBlank()) {
                query += " and '$parentFolderId' in parents"
            }

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$BASE_DRIVE_URL/files?q=$encodedQuery&spaces=drive&fields=files(id,name)&pageSize=10"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(searchRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.code == 401) {
                    return@withContext Result.failure(IOException("UNAUTHORIZED"))
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP_${response.code}: $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val folderId = files.getJSONObject(0).getString("id")
                    return@withContext Result.success(folderId)
                }
            }

            // 2. Folder not found -> create it
            val createJson = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
                if (!parentFolderId.isNullOrBlank()) {
                    put("parents", JSONArray().put(parentFolderId))
                }
            }

            val requestBody = createJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            val createRequest = Request.Builder()
                .url("$BASE_DRIVE_URL/files")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            client.newCall(createRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.code == 401) {
                    return@withContext Result.failure(IOException("UNAUTHORIZED"))
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP_${response.code}: $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val newFolderId = json.getString("id")
                Result.success(newFolderId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ensures root folder "الحكيمي للأدوية والمستلزمات الطبية" and subfolder "Backups" exist.
     * Returns the "Backups" folder ID.
     */
    suspend fun getOrCreateAppBackupsFolder(accessToken: String): Result<String> {
        val rootResult = getOrCreateFolder(accessToken, APP_ROOT_FOLDER_NAME, null)
        if (rootResult.isFailure) return rootResult

        val rootFolderId = rootResult.getOrThrow()
        return getOrCreateFolder(accessToken, BACKUPS_FOLDER_NAME, rootFolderId)
    }

    /**
     * Finds an existing file by name in the parent folder.
     */
    suspend fun findExistingFileId(
        accessToken: String,
        parentFolderId: String,
        fileName: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) return@withContext Result.failure(IOException("NO_INTERNET"))
        try {
            val escapedName = fileName.replace("'", "\\'")
            val query = "'$parentFolderId' in parents and name = '$escapedName' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_DRIVE_URL/files?q=$encodedQuery&fields=files(id,name)&pageSize=1"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.code == 401) return@withContext Result.failure(IOException("UNAUTHORIZED"))
                if (!response.isSuccessful) return@withContext Result.failure(IOException("HTTP_${response.code}: $bodyStr"))

                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val fileId = files.getJSONObject(0).getString("id")
                    Result.success(fileId)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads or updates backup ZIP file using Google Drive multipart upload.
     * Checks if the file already exists; if so, updates it via PATCH to prevent duplicates.
     */
    suspend fun uploadBackupFile(
        accessToken: String,
        file: File,
        parentFolderId: String,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<GoogleDriveFileMetadata> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(IOException("NO_INTERNET"))
        }

        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IOException("الملف المحدد فارغ أو غير موجود"))
        }

        try {
            onProgress(55, "☁️ جاري البحث عن نسخة سابقة للتحديث أو رفع نسخة جديدة...")

            val existingIdResult = findExistingFileId(accessToken, parentFolderId, file.name)
            val existingFileId = if (existingIdResult.isSuccess) existingIdResult.getOrNull() else null

            val metadataJson = JSONObject().apply {
                if (existingFileId.isNullOrBlank()) {
                    put("name", file.name)
                    put("parents", JSONArray().put(parentFolderId))
                }
                put("mimeType", "application/zip")
                put("description", "نسخة احتياطية سحابية لتطبيق الحكيمي للأدوية والمستلزمات الطبية")
            }

            val metadataPart = metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            val filePart = file.asRequestBody("application/zip".toMediaType())

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(metadataPart)
                .addFormDataPart("file", file.name, filePart)
                .build()

            val request: Request
            if (!existingFileId.isNullOrBlank()) {
                onProgress(70, "🔄 جاري تحديث النسخة الاحتياطية الموجودة في Google Drive...")
                val updateUrl = "$UPLOAD_DRIVE_URL/files/$existingFileId?uploadType=multipart&fields=id,name,size,mimeType,createdTime"
                request = Request.Builder()
                    .url(updateUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .patch(multipartBody)
                    .build()
            } else {
                onProgress(70, "⬆️ جاري رفع نسخة احتياطية جديدة إلى Google Drive...")
                val uploadUrl = "$UPLOAD_DRIVE_URL/files?uploadType=multipart&fields=id,name,size,mimeType,createdTime"
                request = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(multipartBody)
                    .build()
            }

            onProgress(75, "⬆️ جاري نقل البيانات إلى خوادم Google Drive...")

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.code == 401) {
                    return@withContext Result.failure(IOException("UNAUTHORIZED"))
                }
                if (response.code == 403) {
                    if (bodyStr.contains("storageQuotaExceeded", ignoreCase = true)) {
                        return@withContext Result.failure(IOException("QUOTA_EXCEEDED"))
                    }
                    return@withContext Result.failure(IOException("INSUFFICIENT_PERMISSIONS"))
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP_${response.code}: $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val fileId = json.getString("id")
                val fileName = json.optString("name", file.name)
                val fileSize = json.optLong("size", file.length())
                val createdTime = System.currentTimeMillis()

                val metadata = GoogleDriveFileMetadata(
                    id = fileId,
                    name = fileName,
                    size = fileSize,
                    mimeType = "application/zip",
                    createdTime = createdTime
                )

                onProgress(95, "✓ تم تأكيد استلام وحفظ الملف في Google Drive بنجاح!")
                Result.success(metadata)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lists backup files in the app's Backups folder on Google Drive.
     */
    suspend fun listBackups(
        accessToken: String,
        parentFolderId: String
    ): Result<List<GoogleDriveFileMetadata>> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(IOException("NO_INTERNET"))
        }

        try {
            val query = "'$parentFolderId' in parents and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_DRIVE_URL/files?q=$encodedQuery&fields=files(id,name,size,mimeType,createdTime,modifiedTime)&orderBy=createdTime desc&pageSize=30"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.code == 401) {
                    return@withContext Result.failure(IOException("UNAUTHORIZED"))
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP_${response.code}: $bodyStr"))
                }

                val json = JSONObject(bodyStr)
                val filesArr = json.optJSONArray("files") ?: JSONArray()
                val list = mutableListOf<GoogleDriveFileMetadata>()

                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                val isoFormatNoMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

                for (i in 0 until filesArr.length()) {
                    val obj = filesArr.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val size = obj.optLong("size", 0L)
                    val mime = obj.optString("mimeType", "")
                    val createdStr = obj.optString("createdTime", "")

                    var timeMillis = System.currentTimeMillis()
                    if (createdStr.isNotBlank()) {
                        timeMillis = try {
                            isoFormat.parse(createdStr)?.time
                                ?: isoFormatNoMillis.parse(createdStr)?.time
                                ?: System.currentTimeMillis()
                        } catch (_: Exception) {
                            System.currentTimeMillis()
                        }
                    }

                    list.add(
                        GoogleDriveFileMetadata(
                            id = id,
                            name = name,
                            size = size,
                            mimeType = mime,
                            createdTime = timeMillis
                        )
                    )
                }

                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads a file from Google Drive directly to a destination File using streaming.
     */
    suspend fun downloadFile(
        accessToken: String,
        fileId: String,
        destinationFile: File,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(IOException("NO_INTERNET"))
        }

        try {
            onProgress(20, "📥 جاري الاتصال بـ Google Drive لتنزيل النسخة...")

            val url = "$BASE_DRIVE_URL/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    return@withContext Result.failure(IOException("UNAUTHORIZED"))
                }
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    return@withContext Result.failure(IOException("HTTP_${response.code}: $body"))
                }

                val body = response.body ?: return@withContext Result.failure(IOException("جسم الاستجابة فارغ"))
                val totalLength = body.contentLength()

                onProgress(35, "⬇️ جاري كتابة بيانات النسخة إلى التخزين المؤقت...")

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalLength > 0) {
                                val percent = 35 + ((totalRead.toDouble() / totalLength) * 25).toInt().coerceIn(0, 25)
                                onProgress(percent, "⬇️ جاري التنزيل: ${totalRead / 1024} KB...")
                            }
                        }
                        output.flush()
                    }
                }

                onProgress(60, "✓ تم تنزيل النسخة الاحتياطية بنجاح.")
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
