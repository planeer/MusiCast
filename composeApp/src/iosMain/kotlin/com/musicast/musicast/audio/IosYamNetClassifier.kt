@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.musicast.musicast.audio

import cnames.structs.TfLiteInterpreter
import cnames.structs.TfLiteModel
import cocoapods.TensorFlowLiteC.TfLiteInterpreterAllocateTensors
import cocoapods.TensorFlowLiteC.TfLiteInterpreterCreate
import cocoapods.TensorFlowLiteC.TfLiteInterpreterDelete
import cocoapods.TensorFlowLiteC.TfLiteInterpreterGetInputTensor
import cocoapods.TensorFlowLiteC.TfLiteInterpreterGetOutputTensor
import cocoapods.TensorFlowLiteC.TfLiteInterpreterInvoke
import cocoapods.TensorFlowLiteC.TfLiteInterpreterResizeInputTensor
import cocoapods.TensorFlowLiteC.TfLiteModelCreateFromFile
import cocoapods.TensorFlowLiteC.TfLiteModelDelete
import cocoapods.TensorFlowLiteC.TfLiteTensorCopyFromBuffer
import cocoapods.TensorFlowLiteC.TfLiteTensorCopyToBuffer
import cocoapods.TensorFlowLiteC.TfLiteTensorByteSize
import cocoapods.TensorFlowLiteC.kTfLiteOk
import kotlinx.cinterop.*
import platform.Foundation.NSBundle

class IosYamNetClassifier : YamNetClassifier {

    private var model: CPointer<TfLiteModel>? = null
    private var interpreter: CPointer<TfLiteInterpreter>? = null

    override fun loadModel(): Boolean {
        return try {
            val modelPath = NSBundle.mainBundle.pathForResource("yamnet", ofType = "tflite")
                ?: return false

            model = TfLiteModelCreateFromFile(modelPath) ?: return false
            val interp = TfLiteInterpreterCreate(model, null) ?: run {
                TfLiteModelDelete(model)
                model = null
                return false
            }
            interpreter = interp

            // YAMNet's TFLite model ships with a dynamic input shape. Before
            // we can allocate tensors and copy samples in, we have to resize
            // input 0 to [15600] (0.975s of 16kHz mono). Without this, every
            // TfLiteTensorCopyFromBuffer call below silently fails with a
            // size mismatch and `classify()` returns null for every window.
            val resizeOk = memScoped {
                val dims = allocArray<IntVar>(1)
                dims[0] = AudioConstants.YAMNET_WINDOW_SAMPLES
                TfLiteInterpreterResizeInputTensor(interp, 0, dims, 1) == kTfLiteOk
            }
            if (!resizeOk) return false

            val status = TfLiteInterpreterAllocateTensors(interp)
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
