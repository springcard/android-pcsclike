/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverusb

import android.hardware.usb.UsbDevice
import com.springcard.pcsclike.*


class DeviceFragment : com.springcard.pcscapp.DeviceFragment() {

    override fun connectToDevice() {

        if(device is UsbDevice) {
            deviceName = "${(device as UsbDevice).manufacturerName} ${(device as UsbDevice).productName} [${(device as UsbDevice).serialNumber}]"
            SCardReaderListUsb.create(mainActivity, device as UsbDevice, scardCallbacks)
        }
        else {
            mainActivity.logInfo("Device is not a USB device")
        }
    }

    override fun init(_device: Any) {

        if(_device is UsbDevice) {
            device = _device
        }
        else {
            mainActivity.logInfo("Device is not a USB device")
        }
    }

}
