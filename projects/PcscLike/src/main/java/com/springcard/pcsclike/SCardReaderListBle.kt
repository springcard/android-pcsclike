/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import java.util.*
import android.support.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class SCardReaderListBle internal constructor(layerDevice: BluetoothDevice, callbacks: SCardReaderListCallback): SCardReaderList(layerDevice as Any, callbacks) {

    override fun create(ctx : Context) {
        if(layerDevice is BluetoothDevice) {
            commLayer = BluetoothLayer(layerDevice, callbacks, this)
            process(ActionEvent.ActionCreate(ctx))
        }
    }


    override fun create(ctx : Context, secureConnexionParameters: CcidSecureParameters) {
        if(layerDevice is BluetoothDevice) {
            commLayer = BluetoothLayer(layerDevice, callbacks, this)
            ccidHandler = CcidHandler(secureConnexionParameters)
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
            val readerList = checkIfDeviceKnown(device, callbacks)
            readerList.create(ctx)
        }

        /**
         * Instantiate a SpringCard PC/SC product (possibly including one or more reader a.k.a slot)
         * callback when succeed : [SCardReaderListCallback.onReaderListCreated]
         * It also creates a secure communication channel based on info given in parameter
         *
         * @param ctx Application's context use to instantiate the object
         * @param device BLE device
         * @param callbacks list of callbacks
         * @param secureConnexionParameters CcidSecureParameters
         */
        fun create(ctx: Context, device: Any, callbacks: SCardReaderListCallback, secureConnexionParameters: CcidSecureParameters) {
            val readerList = checkIfDeviceKnown(device, callbacks)
            readerList.create(ctx, secureConnexionParameters)
        }


        private fun checkIfDeviceKnown(device: Any, callbacks: SCardReaderListCallback): SCardReaderList {
            lateinit var scardReaderList: SCardReaderListBle
            val address= (device as BluetoothDevice).address

            if(knownSCardReaderList.containsKey(address)) {
                if (knownSCardReaderList[address]!!.isConnected) {
                    throw IllegalArgumentException("SCardReaderList with address $address already exist")
                } else {
                    knownSCardReaderList[address]?.isAlreadyKnown = true
                    scardReaderList = knownSCardReaderList[address] as SCardReaderListBle
                }
            }
            else {
                scardReaderList = SCardReaderListBle(device, callbacks)
                knownSCardReaderList[address] = scardReaderList
            }
            return scardReaderList
        }

        private var knownSCardReaderList = mutableMapOf<String, SCardReaderList>()

        /**
         * Clear list of devices known
         */
        fun clearCache() {
            knownSCardReaderList.clear()
        }
    }

}