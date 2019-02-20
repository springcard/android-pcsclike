/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.bluetooth.BluetoothDevice
import android.hardware.usb.UsbDevice
import android.content.Context

class SCardReaderListUsb internal constructor(layerDevice: UsbDevice, callbacks: SCardReaderListCallback): SCardReaderList(layerDevice as Any, callbacks) {


    private fun create(ctx : Context) {
        if(layerDevice is UsbDevice) {
            commLayer = UsbLayer(layerDevice, callbacks, this)
            process(ActionEvent.ActionCreate(ctx))
        }
    }

    companion object {
        /**
         * Instantiate a SpringCard PC/SC product (possibly including one or more reader a.k.a slot)
         * callback when succeed : [SCardReaderListCallback.onReaderListCreated]
         *
         * @param ctx Application's context use to instantiate the object
         * @param device BLE device
         * @param callbacks list of callbacks
         */
        fun create(ctx: Context, device: Any, callbacks: SCardReaderListCallback) {
            val scardReaderList = SCardReaderListUsb(device as UsbDevice, callbacks)
            scardReaderList.create(ctx)
        }
    }
}
