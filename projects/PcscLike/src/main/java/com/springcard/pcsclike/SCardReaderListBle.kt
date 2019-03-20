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
         * Communication supervision timeout in ms (30s by default)
         * A small value will increase the reactivity but if it's too short it will disconnect unexpectedly
         */
        var communicationSupervisionTimeout: Long = 30_000


        /**
         * Connexion supervision timeout in ms (30s by default)
         * A small value will increase the reactivity but if it's too short it will disconnect unexpectedly
         */
        var connexionSupervisionTimeout: Long = 30_000
    }

}