package com.que.aimbot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class Detection(val bbox: RectF, val confidence: Float, val classId: Int)

class InferenceEngine(context: Context) {

    private val interpreter: Interpreter
    private val inputSize = 640
    private val confidenceThreshold = 0.45f
    private val iouThreshold = 0.45f
    private val numClasses = 1
    private val outputSize = 8400

    init {
        val gpuDelegate = GpuDelegate()
        val options = Interpreter.Options().apply {
            addDelegate(gpuDelegate)
            setNumThreads(4)
        }
        interpreter = Interpreter(loadModel(context, "fn_model.tflite"), options)
    }

    private fun loadModel(context: Context, filename: String): ByteBuffer {
        val assetFd = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFd.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFd.startOffset,
            assetFd.declaredLength
        )
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = bitmapToBuffer(resized)
        resized.recycle()

        val output = Array(1) { Array(4 + numClasses) { FloatArray(outputSize) } }
        interpreter.run(inputBuffer, output)

        return parseOutput(output[0], bitmap.width, bitmap.height)
    }

    private fun bitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }
        return buffer
    }

    private fun parseOutput(
        output: Array<FloatArray>,
        origW: Int,
        origH: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val scaleX = origW.toFloat() / inputSize
        val scaleY = origH.toFloat() / inputSize

        for (i in 0 until outputSize) {
            val cx = output[0][i]
            val cy = output[1][i]
            val w = output[2][i]
            val h = output[3][i]
            val conf = output[4][i]

            if (conf < confidenceThreshold) continue

            val x1 = (cx - w / 2) * scaleX
            val y1 = (cy - h / 2) * scaleY
            val x2 = (cx + w / 2) * scaleX
            val y2 = (cy + h / 2) * scaleY

            detections.add(Detection(RectF(x1, y1, x2, y2), conf, 0))
        }

        return nms(detections)
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(best.bbox, it.bbox) > iouThreshold }
        }

        return result
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interX1 = maxOf(a.left, b.left)
        val interY1 = maxOf(a.top, b.top)
        val interX2 = minOf(a.right, b.right)
        val interY2 = minOf(a.bottom, b.bottom)

        val interArea = maxOf(0f, interX2 - interX1) * maxOf(0f, interY2 - interY1)
        val unionArea = (a.width() * a.height()) + (b.width() * b.height()) - interArea

        return if (unionArea <= 0f) 0f else interArea / unionArea
    }
}
