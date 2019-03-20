/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample_usb

import android.hardware.usb.UsbDevice
import com.springcard.pcsclike.*
import android.view.Menu
import android.view.MenuInflater


class DeviceFragment : com.springcard.pcsclike_sample.DeviceFragment() {

    override fun connectToDevice() {

        if(device is UsbDevice) {
            deviceName = "${(device as UsbDevice).manufacturerName} ${(device as UsbDevice).productName} [${(device as UsbDevice).serialNumber}]"
            SCardReaderList.create(mainActivity, device as UsbDevice, scardCallbacks)
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

    override fun onCreateOptionsMenu(
        menu: Menu, inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        /* Hide shutdown button when we are on USB */
        menu.findItem(com.springcard.pcsclike_sample.R.id.action_shutdown).isVisible = false
    }

}
