package com.example.calculadoraedoia

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// Request/Response models
data class VisionRequest(
    val requests: List<AnnotateImageRequest>
)

data class AnnotateImageRequest(
    val image: Image,
    val features: List<Feature>
)

data class Image(
    val content: String  // Base64 encoded image
)

data class Feature(
    val type: String,
    val maxResults: Int = 10
)

data class VisionResponse(
    val responses: List<AnnotateImageResponse>
)

data class AnnotateImageResponse(
    val textAnnotations: List<TextAnnotation>? = null,
    val fullTextAnnotation: FullTextAnnotation? = null,
    val error: VisionError? = null
)

data class TextAnnotation(
    val description: String,
    val locale: String? = null,
    val boundingPoly: BoundingPoly? = null
)

data class FullTextAnnotation(
    val text: String
)

data class BoundingPoly(
    val vertices: List<Vertex>
)

data class Vertex(
    val x: Int,
    val y: Int
)

data class VisionError(
    val code: Int,
    val message: String,
    val status: String
)

// Retrofit interface
interface GoogleVisionApi {
    @POST("v1/images:annotate")
    suspend fun annotateImage(
        @Query("key") apiKey: String,
        @Body request: VisionRequest
    ): VisionResponse
}

object GoogleVisionClient {
    private const val BASE_URL = "https://vision.googleapis.com/"

    fun create(apiKey: String): GoogleVisionApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(GoogleVisionApi::class.java)
    }
}
