package com.musicast.musicast.audio

import com.musicast.musicast.domain.model.AudioSegment
import com.musicast.musicast.domain.model.ContentType

/**
 * Classifies YAMNet output scores into speech/music segments.
 *
 * Each input element is a FloatArray of 521 AudioSet class scores produced by
 * one 0.975-second YAMNet inference window.
 */
class SegmentClassifier {

    /**
     * Classifies a sequence of per-window YAMNet score vectors into
     * speech/music segments.
     */
    fun classify(windowScores: List<FloatArray>): List<AudioSegment> {
        if (windowScores.isEmpty()) return emptyList()

        // Step 1: Label each window as SPEECH or MUSIC
        val windowLabels = windowScores.map { scores ->
            val speechScore = SPEECH_INDICES.sumOf { scores.getOrElse(it) { 0f }.toDouble() }
            val musicScore = MUSIC_INDICES.sumOf { scores.getOrElse(it) { 0f }.toDouble() }
            if (musicScore > speechScore) ContentType.MUSIC else ContentType.SPEECH
        }

        // Step 2: Median filter to smooth noise
        val smoothed = medianFilter(windowLabels, kernelSize = 5)

        // Step 3: Merge into segments (each window = 975 ms)
        val rawSegments = mergeToSegments(smoothed)

        // Step 4: Remove short segments (< 3 seconds)
        return removeShortSegments(rawSegments, minDurationMs = 3000L)
    }

    private fun medianFilter(labels: List<ContentType>, kernelSize: Int): List<ContentType> {
        val half = kernelSize / 2
        return List(labels.size) { i ->
            val window = (maxOf(0, i - half)..minOf(labels.size - 1, i + half))
            val musicCount = window.count { labels[it] == ContentType.MUSIC }
            val total = window.last - window.first + 1
            if (musicCount > total / 2) ContentType.MUSIC else ContentType.SPEECH
        }
    }

    private fun mergeToSegments(labels: List<ContentType>): List<AudioSegment> {
        if (labels.isEmpty()) return emptyList()

        val segments = mutableListOf<AudioSegment>()
        var currentType = labels[0]
        var startIdx = 0

        for (i in 1 until labels.size) {
            if (labels[i] != currentType) {
                segments.add(
                    AudioSegment(
                        startMs = startIdx * MS_PER_WINDOW,
                        endMs = i * MS_PER_WINDOW,
                        type = currentType,
                    )
                )
                currentType = labels[i]
                startIdx = i
            }
        }
        // Final segment
        segments.add(
            AudioSegment(
                startMs = startIdx * MS_PER_WINDOW,
                endMs = labels.size * MS_PER_WINDOW,
                type = currentType,
            )
        )

        return segments
    }

    private fun removeShortSegments(
        segments: List<AudioSegment>,
        minDurationMs: Long,
    ): List<AudioSegment> {
        if (segments.size <= 1) return segments

        val result = segments.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            val iter = result.listIterator()
            while (iter.hasNext()) {
                val seg = iter.next()
                if (seg.endMs - seg.startMs < minDurationMs && result.size > 1) {
                    val idx = iter.previousIndex()
                    if (idx > 0 && (idx >= result.size - 1 ||
                            (result[idx - 1].endMs - result[idx - 1].startMs >=
                                result[idx + 1].endMs - result[idx + 1].startMs))
                    ) {
                        result[idx - 1] = result[idx - 1].copy(endMs = seg.endMs)
                        iter.remove()
                    } else if (idx < result.size - 1) {
                        result[idx + 1] = result[idx + 1].copy(startMs = seg.startMs)
                        iter.remove()
                    }
                    changed = true
                    break
                }
            }
        }

        // Merge adjacent segments of the same type
        val merged = mutableListOf(result[0])
        for (i in 1 until result.size) {
            val last = merged.last()
            if (result[i].type == last.type) {
                merged[merged.lastIndex] = last.copy(endMs = result[i].endMs)
            } else {
                merged.add(result[i])
            }
        }

        return merged
    }

    companion object {
        /** Duration of one YAMNet window in milliseconds. */
        private const val MS_PER_WINDOW = 975L

        // YAMNet AudioSet class indices for speech-related sounds
        private val SPEECH_INDICES = intArrayOf(
            0,  // Speech
            1,  // Child speech, kid speaking
            2,  // Conversation
            3,  // Narration, monologue
            4,  // Babbling
            5,  // Speech synthesizer
            12, // Whispering
        )

        // YAMNet AudioSet class indices for music-related sounds
        // Includes: singing (24-31), music/instruments (132-210), genres (211-276)
        private val MUSIC_INDICES = run {
            val indices = mutableListOf<Int>()
            // Singing
            indices.addAll(24..31)
            // Music, instruments, genres
            indices.addAll(132..276)
            indices.toIntArray()
        }
    }
}
