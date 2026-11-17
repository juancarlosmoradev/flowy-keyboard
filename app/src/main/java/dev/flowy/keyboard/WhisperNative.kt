package dev.flowy.keyboard

internal class WhisperNative {
    companion object {
        init {
            System.loadLibrary("flowy_whisper")
        }
    }

    external fun initialize(modelPath: String): Long
    external fun transcribe(context: Long, audioSamples: FloatArray): String
    external fun release(context: Long)
}
