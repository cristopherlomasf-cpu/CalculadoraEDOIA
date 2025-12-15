package com.example.calculadoraedoia

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object PerplexityClient {

    fun create(apiKey: String): PerplexityApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val req0 = chain.request()
                Log.d("NET", "Request -> ${req0.method} ${req0.url}")

                val req = req0.newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                chain.proceed(req)
            })
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.perplexity.ai/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(PerplexityApi::class.java)
    }
}
