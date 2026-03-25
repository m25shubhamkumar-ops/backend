package com.example.myapp.speech

import android.util.Log
import com.google.cloud.speech.v1.RecognitionAudio
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.SpeechClient
import com.google.protobuf.ByteString
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Handles integration with the Google Cloud Speech-to-Text REST API.
 *
 * Transcription requests are dispatched on a single background thread so that
 * network I/O never blocks the calling thread (typically the main thread).
 *
 * The caller must ensure that valid Google Cloud credentials are available to the
 * app (e.g. via the `GOOGLE_APPLICATION_CREDENTIALS` environment variable or a
 * credentials JSON file bundled with the app).
 *
 * Call [shutdown] when the manager is no longer needed to free executor resources.
 */
class SpeechToTextManager {

    companion object {
        private const val TAG = "SpeechToTextManager"

        /** Language code sent to the API. Change to support other languages. */
        private const val LANGUAGE_CODE = "en-US"
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SpeechToText-Thread")
    }

    private val speechClient: SpeechClient = SpeechClient.create()

    /**
     * Sends [audioData] (raw 16-bit mono PCM at 16 kHz) to the Google Cloud
     * Speech-to-Text API and returns the highest-confidence transcript via [onResult].
     *
     * Both [onResult] and [onError] are invoked on the background executor thread.
     * Post to the main thread inside the callbacks if UI updates are needed.
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
        executor.execute {
            try {
                val config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(AudioRecorder.SAMPLE_RATE_HZ)
                    .setLanguageCode(LANGUAGE_CODE)
                    .build()

                val audio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(audioData))
                    .build()

                val response = speechClient.recognize(config, audio)

                // Use the best alternative from the first result (already ordered by relevance).
                val transcript = response.resultsList
                    .firstOrNull()
                    ?.alternativesList
                    ?.maxByOrNull { it.confidence }
                    ?.transcript
                    .orEmpty()

                Log.d(TAG, "Transcription result: \"$transcript\"")
                onResult(transcript)
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed: ${e.message}", e)
                onError("Transcription failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Shuts down the background executor and closes the shared [SpeechClient].
     *
     * Any in-flight transcription will complete before the executor terminates.
     */
    fun shutdown() {
        executor.shutdown()
        speechClient.close()
        Log.d(TAG, "SpeechToTextManager shut down")
    }
}
