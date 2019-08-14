/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import android.hardware.usb.*
import com.springcard.pcsclike.*

internal class UsbLayer(scardReaderList : SCardReaderList, usbDevice: UsbDevice): CommunicationLayer(scardReaderList) {

    private val TAG = this::class.java.simpleName

    override var lowLayer = UsbLowLevel(scardReaderList, usbDevice) as LowLevelLayer

    override fun wakeUp() {
        throw NotImplementedError("Error, wakeUp() method is not available in USB")
    }
}