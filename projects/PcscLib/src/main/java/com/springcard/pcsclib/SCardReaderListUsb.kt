package com.springcard.pcsclib

import android.hardware.usb.UsbDevice
import android.content.Context

class SCardReaderListUsb(layerDevice: UsbDevice, callbacks: SCardReaderListCallback): SCardReaderList(layerDevice as Any, callbacks) {

    override fun connect(ctx : Context) {
        if(layerDevice is UsbDevice) {
            commLayer = UsbLayer(layerDevice, callbacks, this)
            process(ActionEvent.ActionConnect(ctx))
        }
    }
}