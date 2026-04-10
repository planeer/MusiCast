package com.musicast.musicast.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class PcmDecoder actual constructor() {

    actual suspend fun decodeFile(
        filePath: String,
        onProgress: (Float) -> Unit,
        onChunk: (FloatArray) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            decodeAudio(filePath, onProgress, onChunk)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual suspend fun decodeUrl(
        url: String,
        onProgress: (Float) -> Unit,
        onChunk: (FloatArray) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("podcast_decode_", ".tmp")
            try {
                URL(url).openStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                decodeAudio(tempFile.absolutePath, onProgress, onChunk)
                true
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decodeAudio(
        filePath: String,
        onProgress: (Float) -> Unit,
        onChunk: (FloatArray) -> Unit,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)

        // Find audio track
        var audioTrackIdx = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIdx = i
                format = trackFormat
                break
            }
        }
        require(audioTrackIdx >= 0 && format != null) { "No audio track found" }

        extractor.selectTrack(audioTrackIdx)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val targetRate = AudioConstants.SAMPLE_RATE
        val needsResample = sourceSampleRate != targetRate

        // Get total duration for progress reporting
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        // Resampling state
        val resampleRatio = if (needsResample) sourceSampleRate.toDouble() / targetRate else 1.0
        var resampleAccumulator = 0.0
        var lastProgressReport = 0f

        try {
            while (!outputDone) {
                // Feed input
                if (!inputDone) {
                    val inputIdx = codec.dequeueInputBuffer(10_000L)
                    if (inputIdx >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIdx, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Drain output
                val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outputIdx >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }

                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIdx)!!
                        val chunk = extractAndResample(
                            outputBuffer, bufferInfo.size, channels,
                            needsResample, resampleRatio, resampleAccumulator
                        )
                        resampleAccumulator = chunk.second
                        if (chunk.first.isNotEmpty()) {
                            onChunk(chunk.first)
                        }

                        // Report progress based on presentation time
                        if (durationUs > 0) {
                            val progress = (bufferInfo.presentationTimeUs.toFloat() / durationUs)
                                .coerceIn(0f, 1f)
                            // Only report in 1% increments to avoid flooding
                            if (progress - lastProgressReport >= 0.01f) {
                                lastProgressReport = progress
                                onProgress(progress)
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outputIdx, false)
                }
            }
            onProgress(1f)
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
    }

    private fun extractAndResample(
        buffer: ByteBuffer,
        size: Int,
        channels: Int,
        needsResample: Boolean,
        resampleRatio: Double,
        accumulator: Double,
    ): Pair<FloatArray, Double> {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(0)

        val totalSamples = size / 2
        val monoSamples = totalSamples / channels

        if (!needsResample) {
            val result = FloatArray(monoSamples)
            for (i in 0 until monoSamples) {
                var sum = 0f
                for (ch in 0 until channels) {
                    sum += buffer.short.toFloat() / 32768f
                }
                result[i] = sum / channels
            }
            return result to accumulator
        }

        val mono = FloatArray(monoSamples)
        for (i in 0 until monoSamples) {
            var sum = 0f
            for (ch in 0 until channels) {
                sum += buffer.short.toFloat() / 32768f
            }
            mono[i] = sum / channels
        }

        val outputEstimate = (monoSamples / resampleRatio).toInt() + 1
        val output = FloatArray(outputEstimate)
        var outIdx = 0
        var acc = accumulator

        while (acc < monoSamples - 1 && outIdx < outputEstimate) {
            if (acc >= 0) {
                val idx0 = acc.toInt()
                val frac = (acc - idx0).toFloat()
                output[outIdx] = mono[idx0] * (1f - frac) + mono[idx0 + 1] * frac
                outIdx++
            }
            acc += resampleRatio
        }

        val newAccumulator = acc - monoSamples
        return output.copyOf(outIdx) to newAccumulator
    }
}
