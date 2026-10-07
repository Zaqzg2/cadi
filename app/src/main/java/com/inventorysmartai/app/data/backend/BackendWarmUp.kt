package com.inventorysmartai.app.data.backend

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Free hosting puts an idle server to sleep and the first request afterwards takes up to a minute. One fire-and-forget
 * GET /health when the app starts wakes it in the background, so it is usually ready by the time the person needs it.
 * Best effort by design: it never throws, never blocks, and ignores the answer.
 */
@Singleton
class BackendWarmUp @Inject constructor(private val client: OkHttpClient) {

    fun start() {
        if (BackendConfig.configurationProblem() != null) return
        val request = try {
            Request.Builder().url(BackendConfig.baseUrlNoSlash + "/health").get().build()
        } catch (e: IllegalArgumentException) {
            return
        }
        client.newBuilder().callTimeout(90, TimeUnit.SECONDS).build().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
