@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.musicast.musicast.audio

import cocoapods.TensorFlowLiteC.TfLiteInterpreterAllocateTensors
import cocoapods.TensorFlowLiteC.TfLiteInterpreterCreate
import cocoapods.TensorFlowLiteC.TfLiteInterpreterDelete
import cocoapods.TensorFlowLiteC.TfLiteInterpreterGetInputTensor
import cocoapods.TensorFlowLiteC.TfLiteInterpreterGetOutputTensor
import cocoapods.TensorFlowLiteC.TfLiteInterpreterInvoke
import cocoapods.TensorFlowLiteC.TfLiteModelCreate
import cocoapods.TensorFlowLiteC.TfLiteModelDelete
import cocoapods.TensorFlowLiteC.TfLiteTensorCopyFromBuffer
import cocoapods.TensorFlowLiteC.TfLiteTensorCopyToBuffer
import cocoapods.TensorFlowLiteC.TfLiteTensorByteSize
import cocoapods.TensorFlowLiteC.kTfLiteOk
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle

class IosYamNetClassifier : YamNetClassifier {

    private var model: kotlinx.cinterop.CPointer<cocoapods.TensorFlowLiteC.TfLiteModel>? = null
    private var interpreter: kotlinx.cinterop.CPointer<cocoapods.TensorFlowLiteC.TfLiteInterpreter>? = null

    override fun loadModel(): Boolean {
        return try {
            val modelPath = NSBundle.mainBundle.pathForResource("yamnet", ofType = "tflite")
                ?: return false

            model = TfLiteModelCreate(modelPath) ?: return false
            interpreter = TfLiteInterpreterCreate(model, null) ?: run {
                TfLiteModelDelete(model)
                model = null
                return false
            }

            val status = TfLiteInterpreterAllocateTensors(interpreter)
            status == kTfLiteOk
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun classify(samples: FloatArray): FloatArray? {
        val interp = interpreter ?: return null
        return try {
            val inputTensor = TfLiteInterpreterGetInputTensor(interp, 0) ?: return null

            // Copy input samples to the input tensor (shape [15600], float32)
            samples.usePinned { pinned ->
                val status = TfLiteTensorCopyFromBuffer(
                    inputTensor,
                    pinned.addressOf(0),
                    (samples.size * 4).toULong(),
                )
                if (status != kTfLiteOk) return null
            }

            // Run inference
            val invokeStatus = TfLiteInterpreterInvoke(interp)
            if (invokeStatus != kTfLiteOk) return null

            // Read output 0 (scores) — shape [1, 521]
            val outputTensor = TfLiteInterpreterGetOutputTensor(interp, 0) ?: return null
            val outputBytes = TfLiteTensorByteSize(outputTensor).toInt()
            val numFloats = outputBytes / 4
            val output = FloatArray(numFloats)

            output.usePinned { pinned ->
                val status = TfLiteTensorCopyToBuffer(
                    outputTensor,
                    pinned.addressOf(0),
                    outputBytes.toULong(),
                )
                if (status != kTfLiteOk) return null
            }

            // Model outputs [1, 521] — return just the 521 scores
            if (output.size > AudioConstants.YAMNET_NUM_CLASSES) {
                output.copyOfRange(0, AudioConstants.YAMNET_NUM_CLASSES)
            } else {
                output
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun close() {
        interpreter?.let { TfLiteInterpreterDelete(it) }
        interpreter = null
        model?.let { TfLiteModelDelete(it) }
        model = null
    }
}
