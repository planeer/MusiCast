package com.musicast.musicast.audio

/**
 * Accumulates PCM samples and emits fixed-size windows for YAMNet inference.
 * Non-overlapping: each window covers exactly [windowSize] samples (0.975 s at 16 kHz).
 */
class StreamingWindowBuffer(
    private val windowSize: Int = AudioConstants.YAMNET_WINDOW_SAMPLES,
) {
    private val buffer = FloatArray(windowSize * 2)
    private var length = 0

    /**
     * Appends [chunk] to the internal buffer and invokes [onWindow] for each
     * complete window that can be formed.
     */
    fun add(chunk: FloatArray, onWindow: (FloatArray) -> Unit) {
        var offset = 0
        while (offset < chunk.size) {
            val toCopy = minOf(chunk.size - offset, buffer.size - length)
            chunk.copyInto(buffer, length, offset, offset + toCopy)
            length += toCopy
            offset += toCopy

            while (length >= windowSize) {
                val window = buffer.copyOfRange(0, windowSize)
                onWindow(window)
                buffer.copyInto(buffer, 0, windowSize, length)
                length -= windowSize
            }
        }
    }
}
