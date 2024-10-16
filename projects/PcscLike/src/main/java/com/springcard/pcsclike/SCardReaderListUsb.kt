/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.hardware.usb.UsbDevice
import android.content.Context
import android.util.Log
import com.springcard.pcsclike.ccid.*
import com.springcard.pcsclike.communication.*

class SCardReaderListUsb internal constructor(layerDevice: UsbDevice, callbacks: SCardReaderListCallback): SCardReaderList(layerDevice as Any, callbacks) {

    override fun create(ctx : Context) {
        //Log.i("PcscLikeLibrary", "Lib rev = ${BuildConfig.VERSION_NAME}")
        if(layerDevice is UsbDevice) {
            commLayer = UsbLayer(this, layerDevice)
            commLayer.connect(ctx)
        }
    }

    override fun create(ctx : Context, secureConnexionParameters: CcidSecureParameters) {
        //Log.i("PcscLikeLibrary", "Lib rev = ${BuildConfig.VERSION_NAME}")
        if(layerDevice is UsbDevice) {
            commLayer = UsbLayer(this, layerDevice)
            ccidHandler = CcidHandler(this, secureConnexionParameters)
            commLayer.connect(ctx)
        }
    }
}
