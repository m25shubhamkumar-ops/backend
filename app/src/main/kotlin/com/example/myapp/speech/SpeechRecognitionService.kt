package com.example.myapp.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Main orchestrator for the voice-to-code feature.
 *
 * Records audio via [MediaRecorder] and submits it to the OpenAI Whisper API
 * (through [WhisperApiManager]) for transcription.  Results are surfaced to the
 * UI layer via [SpeechRecognitionCallback].
 *
 * ## Typical usage (Activity/Fragment)
 * ```kotlin
 * class MainActivity : AppCompatActivity(), SpeechRecognitionCallback {
 *
 *     private lateinit var speechService: SpeechRecognitionService
 *     private lateinit var apiKeyManager: ApiKeyManager
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         apiKeyManager = ApiKeyManager(this)
 *         val apiKey = apiKeyManager.getApiKey() ?: "YOUR_OPENAI_API_KEY"
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
    apiKey: String
) {

    companion object {
        private const val TAG = "SpeechRecognitionService"
        private const val AUDIO_FILE_NAME = "voice_record.m4a"
    }

    private val whisperManager = WhisperApiManager(apiKey)
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioFile = File(context.filesDir, AUDIO_FILE_NAME)

    private var mediaRecorder: MediaRecorder? = null

    /**
     * Starts microphone recording.
     *
     * Notifies the callback via [SpeechRecognitionCallback.onRecordingStarted] on success
     * or [SpeechRecognitionCallback.onError] if initialisation fails.
     *
     * Has no effect if recording is already active.
     */
    fun startRecording() {
        if (mediaRecorder != null) {
            Log.w(TAG, "startRecording() called while already recording — ignoring")
            return
        }

        try {
            mediaRecorder = createMediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            Log.d(TAG, "Recording started")
            callback.onRecordingStarted()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            callback.onError("Failed to start recording: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Stops microphone recording and submits the captured audio to the
     * Whisper API for transcription.
     *
     * [SpeechRecognitionCallback.onRecordingStopped] is called immediately after the
     * microphone is released.  [SpeechRecognitionCallback.onTranscriptionResult] (or
     * [SpeechRecognitionCallback.onError]) is called once the API responds.
     *
     * Has no effect if no recording is currently active.
     */
    fun stopRecording() {
        if (mediaRecorder == null) {
            Log.w(TAG, "stopRecording() called with no active recording — ignoring")
            return
        }

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            callback.onRecordingStopped()
            Log.d(TAG, "Recording stopped, submitting for transcription")
            transcribeAudio()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}", e)
            mediaRecorder?.release()
            mediaRecorder = null
            callback.onError("Failed to stop recording: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Sends the recorded audio file to the Whisper API and forwards the result
     * to the [callback].
     */
    private fun transcribeAudio() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                whisperManager.transcribeAudio(audioFile)
            }.onSuccess { text ->
                callback.onTranscriptionResult(text)
            }.onFailure { error ->
                callback.onError("Transcription failed: ${error.message ?: "Unknown error"}")
            }
        }
    }

    /**
     * Releases all resources held by this service.
     *
     * Should be called in `Activity.onDestroy()` or the equivalent lifecycle method.
     */
    fun release() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop during release: ${e.message}")
        }
        mediaRecorder?.release()
        mediaRecorder = null
        serviceScope.cancel()
        audioFile.delete()
        Log.d(TAG, "SpeechRecognitionService released")
    }

    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
}
