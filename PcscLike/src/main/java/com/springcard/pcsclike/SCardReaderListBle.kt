/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class SCardReaderListBle internal constructor(layerDevice: BluetoothDevice, callbacks: SCardReaderListCallback): SCardReaderList(layerDevice as Any, callbacks) {

    private fun create(ctx : Context) {
        if(layerDevice is BluetoothDevice) {
            commLayer = BluetoothLayer(layerDevice, callbacks, this)
            process(ActionEvent.ActionCreate(ctx))
        }
    }


    private fun create(ctx : Context, secureConnexionParameters: CcidSecureParameters) {
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
            val scardReaderList = SCardReaderListBle(device as BluetoothDevice, callbacks)
            scardReaderList.create(ctx)
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
            val scardReaderList = SCardReaderListBle(device as BluetoothDevice, callbacks)
            scardReaderList.create(ctx, secureConnexionParameters)
        }
    }

}