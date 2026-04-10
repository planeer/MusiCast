package com.musicast.musicast.audio

object AudioConstants {
    /** YAMNet requires 16 kHz mono PCM input. */
    const val SAMPLE_RATE = 16000

    /** One YAMNet window = 0.975 seconds at 16 kHz. */
    const val YAMNET_WINDOW_SAMPLES = 15600

    /** YAMNet classifies into 521 AudioSet classes. */
    const val YAMNET_NUM_CLASSES = 521
}
