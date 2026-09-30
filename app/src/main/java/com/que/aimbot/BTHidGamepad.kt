package com.que.aimbot

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

class BTHidGamepad(private val context: Context) {

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private val handler = Handler(Looper.getMainLooper())

    private val descriptor = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),
        0x09.toByte(), 0x05.toByte(),
        0xA1.toByte(), 0x01.toByte(),
        0x09.toByte(), 0x30.toByte(),
        0x09.toByte(), 0x31.toByte(),
        0x09.toByte(), 0x33.toByte(),
        0x09.toByte(), 0x34.toByte(),
        0x15.toByte(), 0x81.toByte(),
        0x25.toByte(), 0x7F.toByte(),
        0x75.toByte(), 0x08.toByte(),
        0x95.toByte(), 0x04.toByte(),
        0x81.toByte(), 0x02.toByte(),
        0xC0.toByte()
    )

    private val sdpRecord = BluetoothHidDevice.AppSdpSettings(
        "Que Gamepad",
        "Aimbot Controller",
        "Que",
        BluetoothHidDevice.SUBCLASS1_GAMEPAD,
        descriptor
    )

    private val qosOut = BluetoothHidDevice.AppQosSettings(
        BluetoothHidDevice.AppQosSettings.SERVICE_BEST_EFFORT,
        800, 9, 0, Integer.MAX_VALUE, Integer.MAX_VALUE
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) {
                connectedHost?.let { hidDevice?.connect(it) }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            connectedHost = if (state == BluetoothProfile.STATE_CONNECTED) device else null
        }
    }

    fun connect() {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        btAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hidDevice = proxy as BluetoothHidDevice
                hidDevice?.registerApp(sdpRecord, null, qosOut,
                    { runnable -> handler.post(runnable) }, callback)
            }
            override fun onServiceDisconnected(profile: Int) {
                hidDevice = null
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun sendStickInput(rx: Float, ry: Float) {
        val host = connectedHost ?: return
        val dev = hidDevice ?: return

        val report = byteArrayOf(
            0, 0,
            (rx * 127f).roundToInt().coerceIn(-127, 127).toByte(),
            (ry * 127f).roundToInt().coerceIn(-127, 127).toByte()
        )
        dev.sendReport(host, 0, report)
    }

    fun disconnect() {
        connectedHost?.let { hidDevice?.disconnect(it) }
        hidDevice?.unregisterApp()
    }
}
