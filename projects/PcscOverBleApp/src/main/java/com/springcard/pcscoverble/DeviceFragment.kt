/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverble


import android.bluetooth.BluetoothDevice
import com.springcard.pcsclib.*


class DeviceFragment : com.springcard.pcscapp.DeviceFragment() {

    override fun connectToDevice() {

        if(device is BluetoothDevice) {
            deviceName = (device as BluetoothDevice).name
            scardDevice = SCardReaderListBle(device as BluetoothDevice, scardCallbacks)
            scardDevice.connect(mainActivity)
        }
        else {
            mainActivity.logInfo("Device is not a BLE device")
        }
    }

    override fun init(_device: Any) {

        if(_device is BluetoothDevice) {
            device = _device
        }
        else {
            mainActivity.logInfo("Device is not a BLE device")
        }
    }
}
