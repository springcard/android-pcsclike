/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample_ble


import android.bluetooth.BluetoothDevice
import android.widget.Toast
import com.springcard.pcsclike.*
import com.springcard.pcsclike.ccid.*


class DeviceFragment : com.springcard.pcsclike_sample.DeviceFragment() {

    override fun connectToDevice() {

        if(device is BluetoothDevice) {
            deviceName = (device as BluetoothDevice).name

            if(mainActivity.supportCrypto && mainActivity.useAuthentication) {
                mainActivity.logInfo("Create readerList with authentication")

                val key: MutableList<Byte>
                if(mainActivity.authenticationKey.isHex()) {
                    key = mainActivity.authenticationKey.hexStringToByteArray().toMutableList()
                }
                else {
                    progressDialog.dismiss()
                    mainActivity.backToScanFragment()
                    return
                }

                if(key.size != 16) {
                    Toast.makeText(mainActivity.applicationContext, "Wrong key size, must be 16 bytes long", Toast.LENGTH_LONG)
                    progressDialog.dismiss()
                    mainActivity.backToScanFragment()
                    return
                }

                val index: CcidSecureParameters.AuthenticationKeyIndex = when {
                    mainActivity.authenticationKeyIndex == 0 -> CcidSecureParameters.AuthenticationKeyIndex.User
                    mainActivity.authenticationKeyIndex == 1 -> CcidSecureParameters.AuthenticationKeyIndex.Admin
                    else -> {
                        Toast.makeText(mainActivity.applicationContext, "Wrong key index ${mainActivity.authenticationKeyIndex}", Toast.LENGTH_LONG)
                        progressDialog.dismiss()
                        mainActivity.backToScanFragment()
                        return
                    }
                }

                SCardReaderList.create(
                    mainActivity.applicationContext,
                    device as BluetoothDevice,
                    scardCallbacks,
                    CcidSecureParameters(
                        CcidSecureParameters.AuthenticationMode.Aes128,
                        index,
                        key,
                        CcidSecureParameters.CommunicationMode.MacAndCipher
                    )
                )
            }
            else {
                SCardReaderList.create(mainActivity.applicationContext, device as BluetoothDevice, scardCallbacks)
            }
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
