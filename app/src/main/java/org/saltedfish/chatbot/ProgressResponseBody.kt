package com.holix.android.bottomsheetdialog.compose.org.saltedfish.chatbot

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.*
import java.io.IOException

class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressListener: ProgressListener
) : ResponseBody() {

    interface ProgressListener {
        fun onProgressUpdate(percentage: Int)
    }

    private var bufferedSource: BufferedSource? = null

    override fun contentType(): MediaType? = responseBody.contentType()

    override fun contentLength(): Long = responseBody.contentLength()

    override fun source(): BufferedSource {
        if (bufferedSource == null) {
            bufferedSource = source(responseBody.source()).buffer()
        }
        return bufferedSource!!
    }

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = 0L
            val contentLength = responseBody.contentLength()

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // Read returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                if (contentLength > 0) { // Only if total length is known
                    val progress = (100 * totalBytesRead / contentLength).toInt()
                    progressListener.onProgressUpdate(progress)
                }
                return bytesRead
            }
        }
    }
}
