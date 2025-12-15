package com.example.calculadoraedoia

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class PplxMessage(val role: String, val content: String)

data class PplxRequest(
    val model: String,
    val messages: List<PplxMessage>,
    val temperature: Double = 0.2
)

data class PplxChoice(val message: PplxMessage)
data class PplxResponse(val choices: List<PplxChoice>)

interface PerplexityApi {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun chat(@Body body: PplxRequest): PplxResponse
}
