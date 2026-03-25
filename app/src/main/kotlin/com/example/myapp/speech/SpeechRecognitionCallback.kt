package com.example.myapp.speech

/**
 * Callback interface for the UI layer to receive speech recognition events.
 *
 * Usage (in Activity or Fragment):
 * ```kotlin
 * class MainActivity : AppCompatActivity(), SpeechRecognitionCallback {
 *
 *     override fun onTranscriptionResult(text: String) {
 *         // Display the transcribed text in your UI
 *         textView.text = text
 *     }
 *
 *     override fun onError(errorMessage: String) {
 *         // Show error to the user
 *         Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
 *     }
 *
 *     override fun onRecordingStarted() {
 *         // Update mic button to active/recording state
 *         micButton.setImageResource(R.drawable.ic_mic_active)
 *     }
 *
 *     override fun onRecordingStopped() {
 *         // Update mic button back to idle state
 *         micButton.setImageResource(R.drawable.ic_mic)
 *     }
 * }
 * ```
 */
interface SpeechRecognitionCallback {

    /**
     * Called when speech has been successfully transcribed to text.
     *
     * @param text The transcribed text from the speech input.
     */
    fun onTranscriptionResult(text: String)

    /**
     * Called when an error occurs during recording or transcription.
     *
     * @param errorMessage A human-readable description of the error.
     */
    fun onError(errorMessage: String)

    /**
     * Called when microphone recording has started successfully.
     */
    fun onRecordingStarted()

    /**
     * Called when microphone recording has stopped and transcription is in progress.
     */
    fun onRecordingStopped()
}
