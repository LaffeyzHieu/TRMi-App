package com.trmi.s3monitor.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trmi.s3monitor.data.model.GpioPin
import com.trmi.s3monitor.data.model.SensorData
import com.trmi.s3monitor.data.model.Threshold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TrmiApi(rawBaseUrl: String = "http://192.168.4.1") {

    // IP trần ("192.168.4.1") → tự thêm "http://", không thì OkHttp throw
    // "Expected URL scheme" và lỗi bị nuốt im lặng.
    private val baseUrl: String = rawBaseUrl.trim().removeSuffix("/").let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonType = "application/json".toMediaType()

    suspend fun fetchData(): Result<SensorData> =
        call { gson.fromJson(get("/data"), SensorData::class.java) }

    suspend fun fetchThreshold(): Result<Threshold> =
        call { gson.fromJson(get("/threshold"), Threshold::class.java) }

    suspend fun pushThreshold(threshold: Threshold): Result<Unit> =
        call { post("/threshold", gson.toJson(threshold)); Unit }

    suspend fun fetchGpio(): Result<List<GpioPin>> = call {
        val type = object : TypeToken<List<GpioPin>>() {}.type
        gson.fromJson<List<GpioPin>>(get("/gpio"), type)
    }

    suspend fun setGpio(pin: Int, state: Boolean): Result<GpioPin> = call {
        gson.fromJson(post("/gpio/set", gson.toJson(GpioPin(pin, state))), GpioPin::class.java)
    }

    // OkHttp execute() chặn luồng → luôn chạy trên IO. Gọi thẳng từ main thread
    // sẽ ném NetworkOnMainThreadException (message = null): chính là lỗi
    // "gửi ESP lỗi: null" bên Settings.
    private suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun get(path: String): String =
        execute(Request.Builder().url(baseUrl + path).get().build())

    private fun post(path: String, json: String): String =
        execute(Request.Builder().url(baseUrl + path).post(json.toRequestBody(jsonType)).build())

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("Empty body")
        }
}
