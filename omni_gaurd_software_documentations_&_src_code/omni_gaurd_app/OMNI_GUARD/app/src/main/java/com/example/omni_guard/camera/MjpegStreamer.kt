package com.example.omni_guard.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import javax.inject.Inject

class MjpegStreamer @Inject constructor() {
    private val client = OkHttpClient()

    fun getStream(url: String): Flow<Bitmap?> = flow {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val inputStream = response.body?.byteStream() ?: return@flow
            
            while (true) {
                val bitmap = parseMjpegFrame(inputStream)
                emit(bitmap)
            }
        } catch (e: Exception) {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    private fun parseMjpegFrame(inputStream: InputStream): Bitmap? {
        // This is a simplified MJPEG parser
        // In a real implementation, you would look for boundary markers (0xFF 0xD8)
        return BitmapFactory.decodeStream(inputStream)
    }
}
