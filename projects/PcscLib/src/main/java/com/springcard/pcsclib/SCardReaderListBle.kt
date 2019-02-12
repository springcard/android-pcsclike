package com.springcard.pcsclib

import android.bluetooth.BluetoothDevice
import android.content.Context

class SCardReaderListBle(layerDevice: BluetoothDevice, callbacks: SCardReaderListCallback): SCardReaderList(layerDevice as Any, callbacks) {

    override fun connect(ctx : Context) {
        if(layerDevice is BluetoothDevice) {
            commLayer = BluetoothLayer(layerDevice, callbacks, this)
            process(ActionEvent.ActionConnect(ctx))
        }
    }
}