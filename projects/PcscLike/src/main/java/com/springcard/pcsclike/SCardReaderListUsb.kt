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


    override fun create(ctx : Context) {
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

            val readerList = SCardReaderListUsb.checkIfDeviceKnown(device, callbacks)
            readerList.create(ctx)
        }


        private fun checkIfDeviceKnown(device: Any, callbacks: SCardReaderListCallback): SCardReaderList {
            lateinit var scardReaderList: SCardReaderListUsb

            /* Warning, IDs are not persistent across USB disconnects */
            val address = (device as UsbDevice).deviceId.toString()

            if(knownSCardReaderList.containsKey(address)) {
                if (knownSCardReaderList[address]!!.isConnected) {
                    throw IllegalArgumentException("SCardReaderList with address $address already exist")
                } else {
                    knownSCardReaderList[address]?.isAlreadyKnown = true
                    scardReaderList = knownSCardReaderList[address] as SCardReaderListUsb
                }
            }
            else {
                scardReaderList = SCardReaderListUsb(device, callbacks)
                knownSCardReaderList[address] = scardReaderList
            }
            return scardReaderList
        }

        private var knownSCardReaderList = mutableMapOf<String, SCardReaderList>()
    }
}
