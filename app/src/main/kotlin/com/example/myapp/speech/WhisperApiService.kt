package com.example.myapp.speech

import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File

/**
 * Data model for the OpenAI Whisper API transcription response.
 *
 * The API returns a JSON object with a "text" field containing the transcribed speech.
 */
data class WhisperResponse(
    @SerializedName("text") val text: String
)

/**
 * Retrofit interface for the OpenAI Audio Transcriptions endpoint.
 *
 * See https://platform.openai.com/docs/api-reference/audio/createTranscription
 */
interface WhisperApi {

    @Multipart
    @POST("audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") authHeader: String,
        @Part("model") model: RequestBody,
        @Part file: MultipartBody.Part
    ): WhisperResponse
}

/**
 * Manages calls to the OpenAI Whisper API for audio transcription.
 *
 * Wrap calls to [transcribeAudio] in a coroutine scope; the function
 * suspends on the IO dispatcher and returns a [Result] containing the
 * transcribed text or the exception that caused the failure.
 *
 * @param apiKey  Your OpenAI API key (starts with "sk-").
 */
class WhisperApiManager(private val apiKey: String) {

    companion object {
        private const val TAG = "WhisperApiManager"
        private const val BASE_URL = "https://api.openai.com/v1/"
        private const val WHISPER_MODEL = "whisper-1"
    }

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api: WhisperApi = retrofit.create(WhisperApi::class.java)

    /**
     * Sends [audioFile] to the Whisper API for transcription.
     *
     * Must be called from a coroutine (suspending function).  Network I/O is
     * performed on the current coroutine context; wrap in [kotlinx.coroutines.withContext]
     * with [kotlinx.coroutines.Dispatchers.IO] if calling from a non-IO context.
     *
     * @param audioFile Audio file to transcribe (MP3, M4A, WAV, etc., max 25 MB).
     * @return [Result.success] with the transcribed text, or [Result.failure] with the exception.
     */
    suspend fun transcribeAudio(audioFile: File): Result<String> {
        return try {
            val modelBody = WHISPER_MODEL.toRequestBody("text/plain".toMediaTypeOrNull())
            val fileBody = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", audioFile.name, fileBody)

            val response = api.transcribeAudio(
                authHeader = "Bearer $apiKey",
                model = modelBody,
                file = filePart
            )

            Log.d(TAG, "Transcription result: \"${response.text}\"")
            Result.success(response.text)
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
