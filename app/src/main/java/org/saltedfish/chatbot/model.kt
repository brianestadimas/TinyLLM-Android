package org.saltedfish.chatbot

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

enum class MessageType{
    TEXT,IMAGE,
}
data class Message(var text:String, val isUser:Boolean, val timeStamp:Int, val type:MessageType=MessageType.TEXT, var content:Any?=null, var isStreaming:Boolean=true, var id:Int=-1,){

}

//object Downloader {
//    private val client = OkHttpClient()
//
//    /**
//     * Downloads a file from the specified URL and saves it to the app's cache directory.
//     *
//     * @param context The application context.
//     * @param fileUrl The URL of the file to download.
//     * @param fileName The name to assign to the downloaded file.
//     * @return The absolute path to the downloaded file, or null if the download failed.
//     */
//    suspend fun downloadFileToCache(context: Context, fileUrl: String, fileName: String): String? {
//        return withContext(Dispatchers.IO) {
//            try {
//                val request = Request.Builder()
//                    .url(fileUrl)
//                    .build()
//
//                client.newCall(request).execute().use { response ->
//                    if (!response.isSuccessful) {
//                        throw IOException("Failed to download file: $response")
//                    }
//
//                    val inputStream = response.body?.byteStream()
//                        ?: throw IOException("Response body is null")
//
//                    val cacheDir = context.cacheDir
//                    if (!cacheDir.exists()) {
//                        cacheDir.mkdirs()
//                    }
//
//                    val file = File(cacheDir, fileName)
//                    file.outputStream().use { outputStream ->
//                        inputStream.copyTo(outputStream)
//                    }
//
//                    Log.i("Downloader", "File downloaded to: ${file.absolutePath}")
//                    file.absolutePath
//                }
//            } catch (e: Exception) {
//                Log.e("Downloader", "Error downloading file: $fileUrl", e)
//                FirebaseCrashlytics.getInstance().recordException(e)
//                null
//            }
//        }
//    }
//}