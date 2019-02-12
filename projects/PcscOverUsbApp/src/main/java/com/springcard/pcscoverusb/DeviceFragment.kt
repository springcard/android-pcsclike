/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverusb


import android.bluetooth.BluetoothDevice
import com.springcard.pcscusblib.*


class DeviceFragment : com.springcard.pcscapp.DeviceFragment() {

    override fun connectToDevice() {

        if(device is BluetoothDevice) {
            deviceName = (device as BluetoothDevice).name
            scardDevice = SCardReaderList(device as BluetoothDevice, scardCallbacks)
            scardDevice.connect(mainActivity)
        }
        else {
            mainActivity.logInfo("Device is not a BLE device")
        }
    }


    override fun init(_device: Any) {

        /*if(_device is BluetoothDevice) {
            device = _device
        }
        else {
            mainActivity.logInfo("Device is not a BLE device")
        }*/
    }
}
