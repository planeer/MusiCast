package com.musicast.musicast.audio

/**
 * Platform-specific YAMNet TFLite inference wrapper.
 * Accepts a 0.975-second window (15 600 samples at 16 kHz) and returns
 * 521 AudioSet class scores.
 */
interface YamNetClassifier {
    /** Load the TFLite model. Must be called once before [classify]. */
    fun loadModel(): Boolean

    /**
     * Run inference on a single 0.975-second window.
     * @param samples exactly [AudioConstants.YAMNET_WINDOW_SAMPLES] float samples, mono 16 kHz PCM
     * @return FloatArray of [AudioConstants.YAMNET_NUM_CLASSES] class scores, or null on failure
     */
    fun classify(samples: FloatArray): FloatArray?

    /** Release model resources. */
    fun close()
}
