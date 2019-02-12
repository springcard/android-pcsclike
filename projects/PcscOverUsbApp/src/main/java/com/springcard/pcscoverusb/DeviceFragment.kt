/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverusb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.springcard.pcsclib.*


class DeviceFragment : com.springcard.pcscapp.DeviceFragment() {

    override fun connectToDevice() {

        if(device is UsbDevice) {
            deviceName = (device as UsbDevice).productName!!
            scardDevice = SCardReaderListUsb(device as UsbDevice, scardCallbacks)
            scardDevice.connect(mainActivity)
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
