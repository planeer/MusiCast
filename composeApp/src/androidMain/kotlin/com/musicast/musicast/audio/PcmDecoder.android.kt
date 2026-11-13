package com.musicast.musicast.audio

import android.media.AudioFormat
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
        // The container's rate/channel count can differ from what the decoder
        // actually emits (e.g. HE-AAC with SBR doubles the rate), so these are
        // refreshed from the codec's output format when it is reported.
        var sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false

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

        var resampler = LinearResampler(sourceSampleRate)
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
                if (outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = codec.outputFormat
                    val newRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) ==
                        AudioFormat.ENCODING_PCM_FLOAT
                    if (newRate != sourceSampleRate) {
                        sourceSampleRate = newRate
                        resampler = LinearResampler(newRate)
                    }
                } else if (outputIdx >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }

                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIdx)!!
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val mono = downmix(outputBuffer, bufferInfo.size, channels, floatPcm)
                        val chunk = resampler.process(mono)
                        if (chunk.isNotEmpty()) {
                            onChunk(chunk)
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

    private fun downmix(
        buffer: ByteBuffer,
        size: Int,
        channels: Int,
        floatPcm: Boolean,
    ): FloatArray {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = if (floatPcm) 4 else 2
        val frames = size / bytesPerSample / channels
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (ch in 0 until channels) {
                sum += if (floatPcm) buffer.float else buffer.short / 32768f
            }
            mono[i] = sum / channels
        }
        return mono
    }
}
