/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverusb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.UnsupportedEncodingException


class DeviceFragment : com.springcard.pcscapp.DeviceFragment() {

    val usbManager: UsbManager by lazy {
        mainActivity.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    override fun connectToDevice() {


        if(device is UsbDevice) {
            deviceName = (device as UsbDevice).productName!!
            // TODO scardDevice = SCardReaderList(device as BluetoothDevice, scardCallbacks)
            // scardDevice.connect(mainActivity)

            connect(device as UsbDevice)
        }
        else {
            mainActivity.logInfo("Device is not a USB device")
        }
    }

    private fun connect(reader: UsbDevice): Boolean {
        val usbDeviceConnection: UsbDeviceConnection

        /* query for interface */
        if (reader.interfaceCount == 0) {
            Log.e(TAG, "Could not find interface ")
            return false
        }
        val usbInterface = reader.getInterface(0)

        /* check for endpoint */
        if (usbInterface.endpointCount == 0) {
            Log.e(TAG, "could not find endpoint")
            return false
        }

        /* connect to device */
        try {
            usbDeviceConnection = usbManager.openDevice(reader)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Could not open device $reader")
            return false
        }


        if (usbDeviceConnection == null) {
            Log.e(TAG, "Unable to attach to new plugged device $reader")
            return false
        }

        return true

        /* grab endpoints */
        /*if (usbInterface.endpointCount > 3) {
            Log.d(TAG, "Found extra endpoints ! ")
        }
        for (i in 0 until usbInterface.endpointCount) {
            val epCheck = usbInterface.getEndpoint(i)
            /* look for BULK type */
            if (epCheck.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (epCheck.direction == UsbConstants.USB_DIR_OUT) {
                    this.bulkOut = epCheck
                } else {
                    this.bulkIn = epCheck
                }
            }

            /* look for BULK type */
            if (epCheck.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (epCheck.direction == UsbConstants.USB_DIR_IN) {
                    this.interruptIn = epCheck
                }
            }
        }

        /* are those endpoints valid ? */
        if (this.bulkOut == null || this.bulkIn == null || this.interruptIn == null) {
            Log.e(TAG, "Unable to find required endpoints ")
            usbDeviceConnection = null
            return false
        }

        /* find number of slot present in this reader */
        var curPos = 0
        try {
            val descriptor = usbDeviceConnection.rawDescriptors

            /* get manufacturer and product name */
            /* with Android API level 21, it's possible to use getManufacturerName() and getProductName () */
            try {
                val buffer = ByteArray(255)
                var len = usbDeviceConnection.controlTransfer(
                    UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_STANDARD, 0x06,
                    0x03 shl 8 or descriptor[14], 0, buffer, 0xFF, 0
                )
                this.manufacturer_name = String(buffer, 2, len - 2, "UTF-16LE")

                len = this.reader_connection.controlTransfer(
                    UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_STANDARD, 0x06,
                    0x03 shl 8 or descriptor[15], 0, buffer, 0xFF, 0
                )
                this.product_name = String(buffer, 2, len - 2, "UTF-16LE")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }

            /* get number of slots present in this reader */
            while (curPos < descriptor.size) {
                /* read descriptor length */
                val dlen = descriptor[curPos].toInt()
                /* read descriptor type */
                val dtype = descriptor[curPos + 1].toInt()
                /* CCID type ? */
                if (dlen == 0x36 && dtype == 0x21) {
                    Log.d(TAG, "Descriptor found, bMaxSlotIndex = " + descriptor[curPos + 4])
                    this.bMaxSlotIndex = descriptor[curPos + 4] + 1
                    break
                }
                curPos += dlen
            }
        } catch (e: NullPointerException) {
            return false
        }

        return this.bMaxSlotIndex != 0*/
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
