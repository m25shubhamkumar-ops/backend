package com.example.myapp.speech

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles audio transcription using the OpenAI Whisper API.
 *
 * Raw PCM audio captured by [AudioRecorder] is wrapped in a WAV container and
 * sent to the `audio/transcriptions` endpoint via [WhisperApiService].
 *
 * Transcription requests run on [Dispatchers.IO] so network I/O never blocks
 * the calling thread.  [onResult] and [onError] are invoked on the IO
 * dispatcher; post to the main thread inside those callbacks if UI updates
 * are needed.
 *
 * Call [shutdown] when the manager is no longer needed to cancel any pending
 * coroutines.
 *
 * @param apiKey The OpenAI API key (e.g. `"sk-proj-..."`).
 */
class SpeechToTextManager(private val apiKey: String) {

    companion object {
        private const val TAG = "SpeechToTextManager"
        private const val WHISPER_MODEL = "whisper-1"
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    /**
     * Sends [audioData] (raw 16-bit mono PCM at [AudioRecorder.SAMPLE_RATE_HZ])
     * to the OpenAI Whisper API and delivers the transcript via [onResult].
     *
     * Both [onResult] and [onError] are invoked on a background IO thread.
     *
     * @param audioData Raw LINEAR16 PCM bytes captured by [AudioRecorder].
     * @param onResult  Invoked with the transcribed text string on success.
     * @param onError   Invoked with a human-readable error message on failure.
     */
    fun transcribeAudio(
        audioData: ByteArray,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val wavBytes = buildWavFile(audioData)

                val modelBody = WHISPER_MODEL.toRequestBody("text/plain".toMediaTypeOrNull())
                val fileBody = wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", "recording.wav", fileBody)

                val response = WhisperApiService.api.transcribeAudio(
                    authorization = "Bearer $apiKey",
                    model = modelBody,
                    file = filePart
                )

                Log.d(TAG, "Transcription result: \"${response.text}\"")
                onResult(response.text)
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed: ${e.message}", e)
                onError("Transcription failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Cancels any pending transcription coroutines.
     *
     * Should be called when the manager is no longer needed (e.g. in
     * `Activity.onDestroy()`).
     */
    fun shutdown() {
        job.cancel()
        Log.d(TAG, "SpeechToTextManager shut down")
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Wraps raw LINEAR16 PCM [pcmData] in a minimal RIFF/WAV container so that
     * the Whisper API can identify the audio format.
     */
    private fun buildWavFile(pcmData: ByteArray): ByteArray {
        val sampleRate = AudioRecorder.SAMPLE_RATE_HZ
        val channels = 1           // mono
        val bitsPerSample = 16     // LINEAR16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataChunkSize = pcmData.size
        val riffChunkSize = 36 + dataChunkSize   // 4 (WAVE) + 8 (fmt header) + 16 (fmt data) + 8 (data header)

        val buffer = ByteBuffer.allocate(44 + dataChunkSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(riffChunkSize)
        buffer.put("WAVE".toByteArray())

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)                   // sub-chunk size for PCM
        buffer.putShort(1)                  // audio format: PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataChunkSize)
        buffer.put(pcmData)

        return buffer.array()
    }
}
