package com.que.aimbot

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private val PROJECTION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            requestProjection()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            statusText.text = "stopped"
        }
    }

    private fun requestProjection() {
        val bt = BluetoothAdapter.getDefaultAdapter()
        if (!bt.isEnabled) {
            startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1
            )
            return
        }
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            PROJECTION_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PROJECTION_REQUEST && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(intent)
            statusText.text = "running"
        }
    }
}
