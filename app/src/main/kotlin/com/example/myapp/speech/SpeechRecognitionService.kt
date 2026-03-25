package com.example.myapp.speech

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Main orchestrator for the voice-to-code feature.
 *
 * Coordinates [AudioRecorder] and [SpeechToTextManager] and surfaces results
 * to the UI layer via [SpeechRecognitionCallback].
 *
 * ## Typical usage (Activity/Fragment)
 * ```kotlin
 * class MainActivity : AppCompatActivity(), SpeechRecognitionCallback {
 *
 *     private lateinit var speechService: SpeechRecognitionService
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         speechService = SpeechRecognitionService(this, this, apiKey)
 *     }
 *
 *     // Called when mic button is pressed
 *     fun onMicButtonDown() = speechService.startRecording()
 *
 *     // Called when mic button is released
 *     fun onMicButtonUp() = speechService.stopRecording()
 *
 *     override fun onTranscriptionResult(text: String) {
 *         runOnUiThread { codeEditor.append(text) }
 *     }
 *
 *     override fun onError(errorMessage: String) {
 *         runOnUiThread { Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show() }
 *     }
 *
 *     override fun onRecordingStarted() {
 *         runOnUiThread { micButton.setImageResource(R.drawable.ic_mic_active) }
 *     }
 *
 *     override fun onRecordingStopped() {
 *         runOnUiThread { micButton.setImageResource(R.drawable.ic_mic) }
 *     }
 *
 *     override fun onDestroy() {
 *         super.onDestroy()
 *         speechService.release()
 *     }
 * }
 * ```
 *
 * @param context  Android [Context] (application or activity context).
 * @param callback Receiver for recording events and transcription results.
 * @param apiKey   OpenAI API key used to authenticate Whisper API requests.
 */
class SpeechRecognitionService(
    private val context: Context,
    private val callback: SpeechRecognitionCallback,
    private val apiKey: String
) {

    companion object {
        private const val TAG = "SpeechRecognitionService"
    }

    private val audioRecorder = AudioRecorder()
    private val speechToTextManager = SpeechToTextManager(apiKey)

    /** Accumulates raw PCM audio chunks during an active recording session. */
    private val audioBuffer = ByteArrayOutputStream()

    /**
     * Starts microphone recording.
     *
     * Notifies the callback via [SpeechRecognitionCallback.onRecordingStarted] on success
     * or [SpeechRecognitionCallback.onError] if initialisation fails.
     *
     * Has no effect if recording is already active.
     */
    fun startRecording() {
        if (audioRecorder.isRecording()) {
            Log.w(TAG, "startRecording() called while already recording — ignoring")
            return
        }

        audioBuffer.reset()

        try {
            audioRecorder.startRecording { audioData ->
                audioBuffer.write(audioData)
            }
            Log.d(TAG, "SpeechRecognitionService: recording started")
            callback.onRecordingStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            callback.onError("Failed to start recording: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Stops microphone recording and submits the captured audio to the
     * Speech-to-Text API for transcription.
     *
     * [SpeechRecognitionCallback.onRecordingStopped] is called immediately after the
     * microphone is released.  [SpeechRecognitionCallback.onSpeechDetected] (or
     * [SpeechRecognitionCallback.onError]) is called once the API responds.
     *
     * Has no effect if no recording is currently active.
     */
    fun stopRecording() {
        if (!audioRecorder.isRecording()) {
            Log.w(TAG, "stopRecording() called with no active recording — ignoring")
            return
        }

        try {
            audioRecorder.stopRecording()
            callback.onRecordingStopped()

            val audioData = audioBuffer.toByteArray()
            if (audioData.isEmpty()) {
                Log.w(TAG, "No audio data was captured")
                callback.onError("No audio data was captured")
                return
            }

            Log.d(TAG, "Submitting ${audioData.size} bytes for transcription")
            speechToTextManager.transcribeAudio(
                audioData = audioData,
                onResult = { transcription -> callback.onSpeechDetected(transcription) },
                onError = { error -> callback.onError(error) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}", e)
            callback.onError("Failed to stop recording: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Releases all resources held by this service.
     *
     * Should be called in `Activity.onDestroy()` or the equivalent lifecycle method.
     */
    fun release() {
        if (audioRecorder.isRecording()) {
            audioRecorder.stopRecording()
        }
        speechToTextManager.shutdown()
        Log.d(TAG, "SpeechRecognitionService released")
    }
}
