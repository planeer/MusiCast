package com.musicast.musicast.audio

import com.musicast.musicast.domain.model.AudioSegment
import com.musicast.musicast.domain.model.ContentType

/**
 * Orchestrates the music detection pipeline using YAMNet (TFLite):
 * 1. Decode audio to 16 kHz mono PCM (platform-specific, streamed via callback)
 * 2. Buffer into 0.975-second windows (15 600 samples)
 * 3. Run YAMNet inference per window to get 521 AudioSet class scores
 * 4. Post-process scores into speech/music segments
 *
 * Memory usage is constant regardless of episode length.
 */
class MusicDetector(
    private val pcmDecoder: PcmDecoder,
    private val yamNetClassifier: YamNetClassifier,
) {

    private val segmentClassifier = SegmentClassifier()

    /**
     * Analyzes a local audio file and returns speech/music segments.
     * [onProgress] is called with values 0.0..1.0 as analysis advances.
     */
    suspend fun analyzeFile(
        filePath: String,
        onProgress: (Float) -> Unit = {},
    ): List<AudioSegment>? {
        if (!yamNetClassifier.loadModel()) {
            println("MusicDetector: failed to load YAMNet model")
            return null
        }
        try {
            val windowScores = mutableListOf<FloatArray>()
            val windowBuffer = StreamingWindowBuffer()
            var classifyFailures = 0

            val success = pcmDecoder.decodeFile(filePath, onProgress) { chunk ->
                windowBuffer.add(chunk) { window ->
                    val scores = yamNetClassifier.classify(window)
                    if (scores != null) {
                        windowScores.add(scores)
                    } else {
                        classifyFailures++
                    }
                }
            }
            println("MusicDetector: decode=${success}, windows=${windowScores.size}, failures=$classifyFailures")
            if (!success) return null
            val segments = segmentClassifier.classify(windowScores)
            println("MusicDetector: produced ${segments.size} segments")
            return segments
        } finally {
            yamNetClassifier.close()
        }
    }

    /**
     * Analyzes audio from a URL and returns speech/music segments.
     * [onProgress] is called with values 0.0..1.0 as analysis advances.
     */
    suspend fun analyzeUrl(
        url: String,
        onProgress: (Float) -> Unit = {},
    ): List<AudioSegment>? {
        if (!yamNetClassifier.loadModel()) {
            println("MusicDetector: failed to load YAMNet model")
            return null
        }
        try {
            val windowScores = mutableListOf<FloatArray>()
            val windowBuffer = StreamingWindowBuffer()
            var classifyFailures = 0

            val success = pcmDecoder.decodeUrl(url, onProgress) { chunk ->
                windowBuffer.add(chunk) { window ->
                    val scores = yamNetClassifier.classify(window)
                    if (scores != null) {
                        windowScores.add(scores)
                    } else {
                        classifyFailures++
                    }
                }
            }
            println("MusicDetector: decode=${success}, windows=${windowScores.size}, failures=$classifyFailures")
            if (!success) return null
            val segments = segmentClassifier.classify(windowScores)
            println("MusicDetector: produced ${segments.size} segments")
            return segments
        } finally {
            yamNetClassifier.close()
        }
    }

    /**
     * Returns the content type at a given position in the pre-analyzed segments.
     */
    fun getContentTypeAt(segments: List<AudioSegment>, positionMs: Long): ContentType {
        return segments.firstOrNull { positionMs in it.startMs..it.endMs }?.type
            ?: ContentType.SPEECH
    }
}
