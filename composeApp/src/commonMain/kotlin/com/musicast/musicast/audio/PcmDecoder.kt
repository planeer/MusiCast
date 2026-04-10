package com.musicast.musicast.audio

/**
 * Decodes audio files to mono PCM float samples at the target sample rate.
 * Uses a streaming callback to avoid loading the entire file into memory.
 * Platform-specific: MediaCodec on Android, AVAssetReader on iOS.
 */
expect class PcmDecoder() {
    /**
     * Decodes a local audio file, streaming mono float PCM chunks at
     * [AudioConstants.SAMPLE_RATE] to the [onChunk] callback.
     * Calls [onProgress] with a value 0.0..1.0 as decoding advances.
     * Returns false if decoding fails.
     */
    suspend fun decodeFile(
        filePath: String,
        onProgress: (Float) -> Unit,
        onChunk: (FloatArray) -> Unit,
    ): Boolean

    /**
     * Decodes audio from a URL, streaming mono float PCM chunks at
     * [AudioConstants.SAMPLE_RATE] to the [onChunk] callback.
     * Downloads to a temp file first, then decodes.
     * Returns false if decoding fails.
     */
    suspend fun decodeUrl(
        url: String,
        onProgress: (Float) -> Unit,
        onChunk: (FloatArray) -> Unit,
    ): Boolean
}
