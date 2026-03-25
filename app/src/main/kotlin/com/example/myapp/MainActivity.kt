package com.example.myapp

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), RecognitionListener {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var listenButton: Button
    private lateinit var transcriptionTextView: TextView 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        transcriptionTextView = findViewById(R.id.transcriptionTextView)
        listenButton = findViewById(R.id.listenButton)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)

        listenButton.setOnClickListener {
            startListening()
        }
    }

    private fun startListening() {
        val intent = RecognizerIntent.getVoiceDetailsIntent(this)
        speechRecognizer.startListening(intent)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        // Handle when ready for speech
    }

    override fun onBeginningOfSpeech() {
        // Handle beginning of speech
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Handle change in sound level
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Handle buffer received
    }

    override fun onEndOfSpeech() {
        // Handle end of speech
    }

    override fun onError(error: Int) {
        transcriptionTextView.text = "Error occurred: $error"
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.get(0) ?: "No speech recognized"
        transcriptionTextView.text = text
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // Handle partial results
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Handle recognizer events
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}