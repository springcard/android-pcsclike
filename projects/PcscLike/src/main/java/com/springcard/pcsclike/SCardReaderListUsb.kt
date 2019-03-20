/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.hardware.usb.UsbDevice
import android.content.Context

class SCardReaderListUsb internal constructor(layerDevice: UsbDevice, callbacks: SCardReaderListCallback): SCardReaderList(layerDevice as Any, callbacks) {


    override fun create(ctx : Context) {
        if(layerDevice is UsbDevice) {
            commLayer = UsbLayer(layerDevice, callbacks, this)
            process(ActionEvent.ActionCreate(ctx))
        }
    }

    override fun create(ctx : Context, secureConnexionParameters: CcidSecureParameters) {
        throw NotImplementedError("Cannot create SCardReaderListUsb with secure parameters for the moment")
    }
}
