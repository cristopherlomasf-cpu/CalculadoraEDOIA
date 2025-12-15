package com.example.calculadoraedoia

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class MathpixRequest(
    val src: String, // base64 image
    val formats: List<String> = listOf("latex_styled"),
    val data_options: Map<String, Boolean> = mapOf(
        "include_asciimath" to false,
        "include_latex" to true
    )
)

data class MathpixResponse(
    val latex_styled: String?,
    val text: String?,
    val error: String?
)

interface MathpixApi {
    @POST("v3/text")
    suspend fun recognizeImage(@Body request: MathpixRequest): MathpixResponse
}

object MathpixClient {
    private const val BASE_URL = "https://api.mathpix.com/"

    fun create(appId: String, appKey: String): MathpixApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .addHeader("app_id", appId)
                .addHeader("app_key", appKey)
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MathpixApi::class.java)
    }
}
