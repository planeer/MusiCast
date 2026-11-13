package com.musicast.musicast.audio

/**
 * Streaming linear-interpolation resampler for mono PCM.
 *
 * Output sample positions are computed exactly from integer counters
 * (`outputIndex * sourceRate / targetRate`) rather than by accumulating a
 * floating-point step, and the last input sample of each chunk is carried
 * over so outputs that fall between two chunks are still produced. This keeps
 * the output length locked to `inputLength * targetRate / sourceRate` no
 * matter how the input is chunked, so timestamps derived from the output
 * sample count don't drift over long files.
 */
class LinearResampler(
    private val sourceRate: Int,
    private val targetRate: Int = AudioConstants.SAMPLE_RATE,
) {
    init {
        require(sourceRate > 0 && targetRate > 0) { "Sample rates must be positive" }
    }

    /** Number of output samples produced so far. */
    private var outputCount = 0L

    /** Number of input samples consumed in previous chunks. */
    private var consumed = 0L

    /** Last sample of the previous chunk (global index `consumed - 1`). */
    private var previous = 0f

    fun process(input: FloatArray): FloatArray {
        if (sourceRate == targetRate) {
            consumed += input.size
            outputCount += input.size
            return input
        }
        val n = input.size
        if (n == 0) return input

        // Every output whose right-hand neighbour (idx0 + 1) is inside this
        // chunk can be produced now: idx0 <= consumed + n - 2.
        val lastGlobalIdx0 = consumed + n - 2
        val maxOutput = if (lastGlobalIdx0 < 0) {
            outputCount // single-sample chunk with no history: nothing yet
        } else {
            // largest k with floor(k * src / tgt) <= lastGlobalIdx0
            ((lastGlobalIdx0 + 1) * targetRate - 1) / sourceRate + 1
        }
        val count = (maxOutput - outputCount).coerceAtLeast(0L).toInt()
        val output = FloatArray(count)

        for (i in 0 until count) {
            val numerator = (outputCount + i) * sourceRate
            val globalIdx0 = numerator / targetRate
            val frac = (numerator % targetRate).toFloat() / targetRate
            val local0 = (globalIdx0 - consumed).toInt() // >= -1
            val s0 = if (local0 < 0) previous else input[local0]
            val s1 = input[local0 + 1]
            output[i] = s0 + (s1 - s0) * frac
        }

        outputCount += count
        consumed += n
        previous = input[n - 1]
        return output
    }
}
