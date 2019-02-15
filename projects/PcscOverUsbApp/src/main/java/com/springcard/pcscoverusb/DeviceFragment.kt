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
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.MenuItem
import com.springcard.pcscapp.R
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // handle item selection
        when (item.itemId) {
            R.id.action_info -> {
                showDeviceInfo()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun showDeviceInfo() {
        /* Info dialog */
        val builder = AlertDialog.Builder(activity!!)

        builder.setTitle(deviceName)


        val deviceInfo = "Vendor: ${scardDevice.vendorName}\n" +
                "Product: ${scardDevice.productName}\n" +
                "Serial Number: ${scardDevice.serialNumber}\n" +
                "FW Version: ${scardDevice.firmwareVersion}\n" +
                "FW Version Major: ${scardDevice.firmwareVersionMajor}\n" +
                "FW Version Minor: ${scardDevice.firmwareVersionMinor}\n" +
                "FW Version Build: ${scardDevice.firmwareVersionBuild}"

        // Do something when user press the positive button
        builder.setMessage(deviceInfo)

        // Set a positive button and its click listener on alert dialog
        builder.setPositiveButton("OK") { _, _ ->
            // Do something when user press the positive button
        }

        // Finally, make the alert dialog using builder
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}
