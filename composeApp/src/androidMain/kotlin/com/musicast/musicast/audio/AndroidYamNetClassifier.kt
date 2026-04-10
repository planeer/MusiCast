package com.musicast.musicast.audio

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class AndroidYamNetClassifier(private val context: Context) : YamNetClassifier {

    private var interpreter: Interpreter? = null

    override fun loadModel(): Boolean {
        return try {
            val model = loadModelFile("yamnet.tflite")
            val interp = Interpreter(model)

            // YAMNet's input tensor has dynamic shape — resize to our window size
            interp.resizeInput(0, intArrayOf(AudioConstants.YAMNET_WINDOW_SAMPLES))
            interp.allocateTensors()

            interpreter = interp

            Log.d(TAG, "YAMNet loaded: ${interp.inputTensorCount} inputs, ${interp.outputTensorCount} outputs")
            for (i in 0 until interp.inputTensorCount) {
                val t = interp.getInputTensor(i)
                Log.d(TAG, "  Input $i: shape=${t.shape().contentToString()}, dtype=${t.dataType()}")
            }
            for (i in 0 until interp.outputTensorCount) {
                val t = interp.getOutputTensor(i)
                Log.d(TAG, "  Output $i: shape=${t.shape().contentToString()}, dtype=${t.dataType()}, bytes=${t.numBytes()}")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YAMNet model", e)
            false
        }
    }

    override fun classify(samples: FloatArray): FloatArray? {
        val interp = interpreter ?: return null
        return try {
            // YAMNet TFLite input shape: [15600] (1D waveform at 16 kHz)
            val inputBuffer = ByteBuffer.allocateDirect(samples.size * 4)
                .order(ByteOrder.nativeOrder())
            for (sample in samples) {
                inputBuffer.putFloat(sample)
            }
            inputBuffer.rewind()

            // Re-allocate tensors to ensure output shapes are up-to-date
            interp.allocateTensors()

            // Prepare outputs — use ByteBuffers for all outputs (shape-agnostic)
            val numOutputs = interp.outputTensorCount
            val outputMap = HashMap<Int, Any>(numOutputs)

            // Output 0: scores — we'll read this one
            val scoresBytes = interp.getOutputTensor(0).numBytes()
            val scoresBuffer = ByteBuffer.allocateDirect(scoresBytes)
                .order(ByteOrder.nativeOrder())
            outputMap[0] = scoresBuffer

            // Other outputs: allocate generous ByteBuffer placeholders
            // YAMNet has dynamic output shapes that may be larger than
            // what getOutputTensor reports before inference
            for (i in 1 until numOutputs) {
                val reportedBytes = interp.getOutputTensor(i).numBytes()
                val allocBytes = maxOf(reportedBytes, 128 * 1024)
                outputMap[i] = ByteBuffer.allocateDirect(allocBytes)
                    .order(ByteOrder.nativeOrder())
            }

            interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

            // Extract scores from ByteBuffer
            scoresBuffer.rewind()
            val numScores = scoresBytes / 4
            val scores = FloatArray(minOf(numScores, AudioConstants.YAMNET_NUM_CLASSES))
            for (i in scores.indices) {
                scores[i] = scoresBuffer.float
            }
            scores
        } catch (e: Exception) {
            Log.e(TAG, "YAMNet inference failed", e)
            null
        }
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun loadModelFile(filename: String): MappedByteBuffer {
        context.assets.openFd(filename).use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                return fis.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength,
                )
            }
        }
    }

    companion object {
        private const val TAG = "YamNetClassifier"
    }
}
