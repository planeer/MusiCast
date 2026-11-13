package com.musicast.musicast.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinearResamplerTest {

    /**
     * An hour of audio must produce an hour of 16 kHz output regardless of
     * the decoder's chunk size — any per-chunk loss shows up as timestamp
     * drift in the detected segments.
     */
    @Test
    fun outputLengthDoesNotDriftOverLongStreams() {
        val cases = listOf(
            44_100 to 1152, // MP3 @ 44.1 kHz
            48_000 to 1152,
            44_100 to 1024, // AAC
            48_000 to 1024,
            22_050 to 1024,
            44_100 to 16_384, // iOS read size
            8_000 to 1024, // upsampling
            16_000 to 1024,
        )
        for ((rate, chunkSize) in cases) {
            val resampler = LinearResampler(rate)
            val chunk = FloatArray(chunkSize)
            val totalInput = rate.toLong() * 3600
            var fed = 0L
            var produced = 0L
            while (fed + chunkSize <= totalInput) {
                produced += resampler.process(chunk).size
                fed += chunkSize
            }
            val expected = fed * AudioConstants.SAMPLE_RATE / rate
            // Outputs past the last input sample wait for more input: a fixed
            // tail of at most ceil(target / source) samples, never growing.
            val tail = (AudioConstants.SAMPLE_RATE + rate - 1) / rate
            assertTrue(
                produced in (expected - tail)..(expected + 1),
                "rate=$rate chunk=$chunkSize expected=$expected produced=$produced",
            )
        }
    }

    /** Linear interpolation of a ramp x[i] = i must give x(t) = k * src / tgt exactly. */
    @Test
    fun interpolatesRampAtExactPositions() {
        for (rate in listOf(44_100, 48_000, 22_050, 8_000)) {
            val input = FloatArray(4_000) { it.toFloat() }
            val output = feedInVaryingChunks(LinearResampler(rate), input)
            assertTrue(output.isNotEmpty(), "rate=$rate produced nothing")
            for (k in output.indices) {
                val expected = k.toDouble() * rate / AudioConstants.SAMPLE_RATE
                assertEquals(expected.toFloat(), output[k], 1e-3f, "rate=$rate sample $k")
            }
        }
    }

    @Test
    fun chunkingDoesNotChangeOutput() {
        for (rate in listOf(44_100, 8_000)) { // downsampling and upsampling
            val signal = FloatArray(rate) { sin(2 * PI * 440 * it / rate).toFloat() }

            val whole = LinearResampler(rate).process(signal)
            val chunked = feedInVaryingChunks(LinearResampler(rate), signal)

            assertEquals(whole.size, chunked.size, "rate=$rate")
            for (i in whole.indices) {
                assertEquals(whole[i], chunked[i], 1e-6f, "rate=$rate sample $i")
            }
        }
    }

    /** Feeds [input] in chunks of 0, 1, 2, … 996 samples (empty chunks included). */
    private fun feedInVaryingChunks(resampler: LinearResampler, input: FloatArray): FloatArray {
        val out = mutableListOf<Float>()
        var offset = 0
        var size = 0
        while (offset < input.size) {
            val end = minOf(input.size, offset + size)
            out += resampler.process(input.copyOfRange(offset, end)).toList()
            offset = end
            size = (size + 1) % 997
        }
        return out.toFloatArray()
    }
}
