package com.springcard.pcscusblib

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager

class USbService {

    /* USB Device Endpoints */
    private lateinit var bulkOut: UsbEndpoint
    private lateinit var  bulkIn: UsbEndpoint
    private lateinit var  interruptIn: UsbEndpoint

    /* USB access points */
    private var reader: UsbDevice? = null
    private var reader_connection: UsbDeviceConnection? = null
    private var usb_manager: UsbManager? = null
    private var manufacturer_name: String? = null
    private var product_name: String? = null
}