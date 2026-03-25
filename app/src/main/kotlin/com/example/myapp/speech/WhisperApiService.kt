package com.example.myapp.speech

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * JSON response returned by the OpenAI `audio/transcriptions` endpoint.
 *
 * @property text The transcribed text from the submitted audio.
 */
data class WhisperResponse(
    @SerializedName("text") val text: String
)

/**
 * Retrofit interface for the OpenAI Whisper transcription endpoint.
 *
 * Sends a multipart request containing the audio file and the model name,
 * and returns the transcription as a [WhisperResponse].
 */
interface WhisperApi {

    /**
     * Transcribes the provided audio file using the specified Whisper model.
     *
     * @param authorization Bearer token in the form `"Bearer sk-proj-..."`.
     * @param model         Model name request body (e.g. `"whisper-1"`).
     * @param file          Audio file part (WAV, MP3, etc., max 25 MB).
     * @return Parsed [WhisperResponse] containing the transcribed text.
     */
    @Multipart
    @POST("audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authorization: String,
        @Part("model") model: RequestBody,
        @Part file: MultipartBody.Part
    ): WhisperResponse
}

/**
 * Singleton holder for the configured [WhisperApi] Retrofit service.
 *
 * Usage:
 * ```kotlin
 * val api = WhisperApiService.api
 * val response = api.transcribeAudio("Bearer $apiKey", modelBody, filePart)
 * ```
 */
object WhisperApiService {

    private const val BASE_URL = "https://api.openai.com/v1/"

    /** Lazily created [WhisperApi] instance backed by a Retrofit client. */
    val api: WhisperApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WhisperApi::class.java)
    }
}
