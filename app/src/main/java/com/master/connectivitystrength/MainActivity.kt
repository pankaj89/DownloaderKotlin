package com.master.connectivitystrength

import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.facebook.network.connectionclass.ConnectionClassManager
import com.facebook.network.connectionclass.ConnectionQuality
import com.facebook.network.connectionclass.DeviceBandwidthSampler
import okhttp3.*
import okio.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Override ConnectionClassStateChangeListener
        ConnectionClassManager.getInstance().register(object : ConnectionClassManager.ConnectionClassStateChangeListener {
            override fun onBandwidthStateChange(bandwidthState: ConnectionQuality?) {
                Log.i("Connectivity", bandwidthState.toString())
            }
        })
        DeviceBandwidthSampler.getInstance().startSampling()
        /*Thread {
            val client = OkHttpClient()
            val request = Request.Builder().url("https://stackoverflow.com/questions/41000584/best-way-to-use-bufferedreader-in-kotlin")
                    .build()
            val response = OkHttpClient().newCall(request).execute()

            val `in` = response.body()?.byteStream()
//            val result = `in`?.bufferedReader()?.use(BufferedReader::readText)
//            println(result)
//            response.body()?.close()

            val fos = FileOutputStream(File(Environment.getExternalStorageDirectory(), "file.txt").path)
            fos.write(response.body()?.bytes())
            fos.close()

            runOnUiThread {
                DeviceBandwidthSampler.getInstance().stopSampling()
                Log.i("Connectivity", ConnectionClassManager.getInstance().currentBandwidthQuality.toString())
            }
        }.start()*/

        "https://www.quintic.com/software/sample_videos/Equine%20Walk%20300fps.avi"
                .saveAsFile(
                        file = File(Environment.getExternalStorageDirectory(), "file2.avi"),
                        resumable = true,
                        progressCallback = {
                            Log.i("TAG", "$it")
                        },
                        callback = { success: Boolean, e: Exception? ->
                            Log.i("TAG", "$success and ${e.toString()}")
                        }
                )
    }

    private class ProgressResponseBody internal constructor(private val responseBody: ResponseBody, private val progressListener: (read: Long, totalLength: Long, isDone: Boolean) -> Unit) : ResponseBody() {
        private var bufferedSource: BufferedSource? = null

        override fun contentType(): MediaType? {
            return responseBody.contentType()
        }

        override fun contentLength(): Long {
            return responseBody.contentLength()
        }

        override fun source(): BufferedSource? {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()))
            }
            return bufferedSource
        }

        private fun source(source: Source): Source {
            return object : ForwardingSource(source) {
                internal var totalBytesRead = 0L

                @Throws(IOException::class)
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    totalBytesRead += if (bytesRead != (-1).toLong()) bytesRead else 0
                    progressListener(totalBytesRead, responseBody.contentLength(), bytesRead == (-1).toLong())
                    return bytesRead
                }
            }
        }
    }

    fun String.saveAsFile(file: File, resumable: Boolean = false, progressCallback: (progress: Int) -> Unit, callback: (success: Boolean, e: Exception?) -> Unit) {
        val client = OkHttpClient.Builder()
                .addNetworkInterceptor { chain ->
                    val originalResponse = chain.proceed(chain.request())

                    val builder = originalResponse.newBuilder()
                    if (originalResponse.body() != null) {
                        builder.body(ProgressResponseBody(originalResponse.body()!!) { read, totalLength, _ ->
                            progressCallback(((read / totalLength.toFloat()) * 100).toInt())
                        })
                    }
                    builder.build()
                }
                .build()

        val requestBuilder = Request.Builder()
        requestBuilder.url(this)
        if (resumable) {
            requestBuilder.header("Range", "bytes=" + file.length() + "-")
        }

        val request = requestBuilder.build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback(false, IOException("Failed to download file: $response"))
                    return
                }
                val fos = FileOutputStream(file.path)
                fos.write(response.body()?.bytes())
                fos.close()
                callback(true, null)
            }
        })
    }
}