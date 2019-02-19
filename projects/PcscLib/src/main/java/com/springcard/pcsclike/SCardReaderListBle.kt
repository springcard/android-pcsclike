/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.bluetooth.BluetoothDevice
import android.content.Context

class SCardReaderListBle(layerDevice: BluetoothDevice, callbacks: SCardReaderListCallback): SCardReaderList(layerDevice as Any, callbacks) {

    override fun connect(ctx : Context) {
        if(layerDevice is BluetoothDevice) {
            commLayer = BluetoothLayer(layerDevice, callbacks, this)
            process(ActionEvent.ActionConnect(ctx))
        }
    }

    /**
     * Get battery level and power state from the device.
     * callback: [SCardReaderListCallback.onPowerInfo]
     */
    fun getPowerInfo() {
        process(ActionEvent.ActionReadPowerInfo())
    }
}