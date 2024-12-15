// Downloader.kt
package org.saltedfish.chatbot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class Downloader {
    private val client = OkHttpClient()

    /**
     * Downloads a file from the given URL to the specified directory.
     * Reports progress via the [progressCallback].
     *
     * @param url The URL of the file to download.
     * @param directory The directory path where the file will be saved.
     * @param fileName The name of the file to save.
     * @param progressCallback A lambda to report progress (0-100).
     * @return The downloaded File.
     * @throws IOException If an error occurs during download.
     */
    suspend fun downloadFile(
        url: String,
        directory: String,
        fileName: String,
        progressCallback: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download file: $response")

            val body = response.body ?: throw IOException("Empty response body")

            val contentLength = body.contentLength()
            val inputStream = body.byteStream()

            val dir = File(directory)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val file = File(dir, fileName)
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            progressCallback(progress)
                        }
                    }
                }
                outputStream.flush()
            }
            file
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
