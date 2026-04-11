@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.musicast.musicast.audio

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioFile
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

actual class PcmDecoder actual constructor() {

    actual suspend fun decodeFile(
        filePath: String,
        onProgress: (Float) -> Unit,
        onChunk: (FloatArray) -> Unit,
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            decodeWithAVAudioFile(NSURL.fileURLWithPath(filePath), onProgress, onChunk)
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
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            val nsUrl = NSURL.URLWithString(url) ?: return@withContext false
            val data = NSData.dataWithContentsOfURL(nsUrl) ?: return@withContext false
            val tempPath = NSTemporaryDirectory() + NSUUID().UUIDString + ".tmp"
            data.writeToFile(tempPath, atomically = true)
            try {
                decodeWithAVAudioFile(NSURL.fileURLWithPath(tempPath), onProgress, onChunk)
                true
            } finally {
                NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun decodeWithAVAudioFile(
        fileUrl: NSURL,
        onProgress: (Float) -> Unit,
        onChunk: (FloatArray) -> Unit,
    ) {
        val audioFile = AVAudioFile(forReading = fileUrl, error = null)

        val processingFormat = audioFile.processingFormat
        val sourceSampleRate = processingFormat.sampleRate.toInt()
        val channelCount = processingFormat.channelCount.toInt()
        val totalFrames = audioFile.length
        val targetRate = AudioConstants.SAMPLE_RATE
        val needsResample = sourceSampleRate != targetRate
        val resampleRatio = if (needsResample) sourceSampleRate.toDouble() / targetRate else 1.0

        val chunkSize = 16384u
        val buffer = AVAudioPCMBuffer(
            pCMFormat = processingFormat,
            frameCapacity = chunkSize
        )

        var framesRead = 0L
        var lastProgressReport = 0f

        while (framesRead < totalFrames) {
            try {
                audioFile.readIntoBuffer(buffer, error = null)
            } catch (_: Exception) {
                break
            }

            val actualFrames = buffer.frameLength.toInt()
            if (actualFrames == 0) break
            framesRead += actualFrames

            val floatData = buffer.floatChannelData ?: break

            // Downmix to mono
            val mono = FloatArray(actualFrames)
            val channel0 = floatData[0] ?: break
            if (channelCount == 1) {
                for (i in 0 until actualFrames) {
                    mono[i] = channel0[i]
                }
            } else {
                for (i in 0 until actualFrames) {
                    var sum = 0f
                    for (ch in 0 until channelCount) {
                        sum += floatData[ch]!![i]
                    }
                    mono[i] = sum / channelCount
                }
            }

            val output = if (needsResample) resample(mono, resampleRatio) else mono
            if (output.isNotEmpty()) {
                onChunk(output)
            }

            // Report progress
            if (totalFrames > 0) {
                val progress = (framesRead.toFloat() / totalFrames).coerceIn(0f, 1f)
                if (progress - lastProgressReport >= 0.01f) {
                    lastProgressReport = progress
                    onProgress(progress)
                }
            }
        }
        onProgress(1f)
    }

    private fun resample(input: FloatArray, ratio: Double): FloatArray {
        val outputSize = (input.size / ratio).toInt()
        if (outputSize <= 0) return FloatArray(0)
        val output = FloatArray(outputSize)
        for (i in output.indices) {
            val srcIdx = i * ratio
            val idx0 = srcIdx.toInt().coerceAtMost(input.size - 1)
            val idx1 = (idx0 + 1).coerceAtMost(input.size - 1)
            val frac = (srcIdx - idx0).toFloat()
            output[i] = input[idx0] * (1f - frac) + input[idx1] * frac
        }
        return output
    }
}
