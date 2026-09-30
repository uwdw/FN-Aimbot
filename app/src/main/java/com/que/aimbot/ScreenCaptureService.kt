package com.que.aimbot

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private lateinit var inferenceEngine: InferenceEngine
    private lateinit var aimController: AimController
    private lateinit var btHid: BTHidGamepad

    private val CHANNEL_ID = "aimbot_channel"
    private val WIDTH = 720
    private val HEIGHT = 1280
    private val DPI = 320

    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        inferenceEngine = InferenceEngine(this)
        aimController = AimController(WIDTH, HEIGHT)
        btHid = BTHidGamepad(this)

        captureThread = HandlerThread("CaptureThread").also { it.start() }
        captureHandler = Handler(captureThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val projManager = getSystemService(MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager
        mediaProjection = projManager.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "AimbotCapture",
            WIDTH, HEIGHT, DPI,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, captureHandler
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * WIDTH

                val bitmap = Bitmap.createBitmap(
                    WIDTH + rowPadding / pixelStride,
                    HEIGHT,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                val cropped = Bitmap.createBitmap(bitmap, 0, 0, WIDTH, HEIGHT)
                bitmap.recycle()

                processFrame(cropped)
            } finally {
                image.close()
            }
        }, captureHandler)

        btHid.connect()
        return START_STICKY
    }

    private fun processFrame(bitmap: Bitmap) {
        val detections = inferenceEngine.detect(bitmap)
        bitmap.recycle()

        if (detections.isEmpty()) {
            aimController.reset()
            btHid.sendStickInput(0f, 0f)
            return
        }

        val target = aimController.selectTarget(detections)
        val (rx, ry) = aimController.computeStickDelta(target)
        btHid.sendStickInput(rx, ry)
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay.release()
        mediaProjection.stop()
        imageReader.close()
        captureThread.quitSafely()
        btHid.disconnect()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Aimbot Service",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("aimbot active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }
}
