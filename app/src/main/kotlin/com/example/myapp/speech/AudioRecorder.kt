package com.example.myapp.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * Utility class for capturing raw PCM audio from the device microphone.
 *
 * Records 16-bit mono PCM audio at 16 kHz, which is a widely supported format
 * for speech recognition APIs including OpenAI Whisper.
 *
 * Requires the RECORD_AUDIO permission to be granted before calling [startRecording].
 */
class AudioRecorder {

    companion object {
        private const val TAG = "AudioRecorder"

        /** Sample rate used for audio capture (16 kHz). */
        const val SAMPLE_RATE_HZ = 16000

        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording = false

    /**
     * Starts capturing audio from the microphone.
     *
     * Audio data is delivered in chunks via [onAudioData] on a background thread.
     * Call [stopRecording] to end the capture session.
     *
     * @param onAudioData Callback invoked with each chunk of PCM audio bytes.
     * @throws IllegalStateException if [AudioRecord] cannot be initialised.
     */
    fun startRecording(onAudioData: (ByteArray) -> Unit) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Unable to determine AudioRecord buffer size")
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialise")
        }

        audioRecord = record
        isRecording = true
        record.startRecording()
        Log.d(TAG, "Recording started (bufferSize=$bufferSize bytes)")

        recordingThread = Thread({
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = record.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    onAudioData(buffer.copyOf(bytesRead))
                } else if (bytesRead < 0) {
                    Log.e(TAG, "AudioRecord.read() returned error code $bytesRead")
                }
            }
            Log.d(TAG, "Recording thread finished")
        }, "AudioRecorder-Thread")

        recordingThread?.start()
    }

    /**
     * Stops the microphone capture and releases the underlying [AudioRecord] resource.
     *
     * Blocks until the recording thread has terminated.
     */
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.join()
        recordingThread = null
        Log.d(TAG, "Recording stopped")
    }

    /**
     * Returns `true` if audio capture is currently active.
     */
    fun isRecording(): Boolean = isRecording
}
