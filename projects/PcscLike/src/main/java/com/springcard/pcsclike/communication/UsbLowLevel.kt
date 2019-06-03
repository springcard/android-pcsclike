package com.springcard.pcsclike.communication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.springcard.pcsclike.utils.*
import com.springcard.pcsclike.ccid.CcidFrame
import java.nio.ByteBuffer


internal class UsbLowLevel(private val highLayer: UsbLayer) {

    private val TAG = this::class.java.simpleName

    /* useful constants */
    private val BULK_TIMEOUT_MS: Int = 100

    /* communication constants */
    private val RDR_to_PC_NotifySlotChange = 0x50.toByte()
    private val RDR_To_PC_DataBlock = 0x80.toByte()
    private val RDR_To_PC_SlotStatus = 0x81.toByte()

    /* USB Device Endpoints */
    private lateinit var bulkOut: UsbEndpoint
    private lateinit var bulkIn: UsbEndpoint
    private lateinit var interruptIn: UsbEndpoint

    /* USB access points */
    private lateinit var usbDeviceConnection: UsbDeviceConnection
    private lateinit var descriptors : ByteArray

    private val usbManager: UsbManager by lazy {
        highLayer.context.getSystemService(Context.USB_SERVICE) as UsbManager
    }


    private val mWaiterThread by lazy {
        WaiterThread()
    }
    private val handlerThread by lazy {
        HandlerThread("usbHandlerThread")
    }
    private val handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }


    /* Utilities func */

    internal fun connect(): Boolean {

        /* query for interface */
        if (highLayer.usbDevice.interfaceCount == 0) {
            Log.e(TAG, "Could not find interface ")
            return false
        }
        val usbInterface = highLayer.usbDevice.getInterface(0)

        /* check for endpoint */
        if (usbInterface.endpointCount == 0) {
            Log.e(TAG, "could not find endpoint")
            return false
        }

        /* connect to device */
        try {
            usbDeviceConnection = usbManager.openDevice(highLayer.usbDevice)
            descriptors = usbDeviceConnection.rawDescriptors
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Could not open device ${highLayer.usbDevice}")
            return false
        }

        /* grab endpoints */
        if (usbInterface.endpointCount > 3) {
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

            highLayer.context.registerReceiver(mUsbReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
        }

        if (!::interruptIn.isInitialized || !::bulkOut.isInitialized || !::bulkIn.isInitialized) {
            Log.e(TAG, "Device ${highLayer.usbDevice} miss on or more endpoint")
            return false
        }

        mWaiterThread.start()

        /* are those endpoints valid ? */
        //TODO

        return true
    }

    internal fun getSlotCount(): Int {
        /* find number of slot present in this reader */
        var curPos = 0

        /* get number of slots present in this reader */
        while (curPos < descriptors.size) {
            /* read descriptors length */
            val dlen = descriptors[curPos].toInt()
            /* read descriptors type */
            val dtype = descriptors[curPos + 1].toInt()
            /* CCID type ? */
            if (dlen == 0x36 && dtype == 0x21) {
                val slotCount = descriptors[curPos + 4] + 1
                Log.d(TAG, "Descriptor found, slotCount = $slotCount")
                return slotCount
            }
            curPos += dlen
        }
        return 0
    }

    internal fun getDeviceInfo(): Array<String> {

        /* Get info directly from USB */
        val manufacturerName: String
        val productName: String
        val serialNumber: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            manufacturerName = highLayer.usbDevice.manufacturerName ?: ""
            productName = highLayer.usbDevice.productName ?: ""
            serialNumber = highLayer.usbDevice.serialNumber ?: ""
        } else {
            val buffer = ByteArray(255)
            manufacturerName = usbDeviceConnection.getString(descriptors, 14 /* iManufacturer */, buffer)
            productName = usbDeviceConnection.getString(descriptors, 15 /* iProduct */, buffer)
            serialNumber = usbDeviceConnection.getString(descriptors, 16 /* iSerialNumber */, buffer)
        }

        return arrayOf(manufacturerName, productName, serialNumber)
    }

    private fun UsbDeviceConnection.getString(rawDescriptor: ByteArray, index: Int, buffer: ByteArray): String {
        val len = controlTransfer(
            UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_STANDARD,
            0x06 /* GET_DESCRIPTOR */,
            (0x03 /* DESCRIPTOR_STRING */ shl 8) or rawDescriptor[index].toInt(),
            0, buffer, buffer.size,
            0
        )
        if (len < 0) {
            // Read failure
            return ""
        }

        return String(buffer, 2, len - 2, Charsets.UTF_16LE)
    }

    private fun stop() {
        synchronized(mWaiterThread) {
            mWaiterThread.mStop = true
        }
    }


    internal fun disconnect() {
        usbDeviceConnection.releaseInterface(highLayer.usbDevice.getInterface(0))
        usbDeviceConnection.close()
        stop()
        highLayer.context.unregisterReceiver(mUsbReceiver)
    }




    /* USB Entries / Outputs */

    private fun onInterruptIn(data: ByteArray) {
        highLayer.process(Event.OnUsbInterrupt(data))
    }

    private fun onBulkIn(data: ByteArray) {
        highLayer.process(Event.OnUsbDataIn(data))
    }

    internal fun bulkOutTransfer(data: ByteArray) {
        usbDeviceConnection.bulkTransfer(bulkOut, data, data.size, BULK_TIMEOUT_MS)
    }

    private var mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.apply {

                    if(device == highLayer.usbDevice) {
                        /* Method that cleans up and closes communication with the device */
                        highLayer.process(Action.Disconnect())
                    }
                }
            }
        }
    }

    private inner class WaiterThread : Thread() {

        var mStop: Boolean = false
        override fun run() {

            /* define working buffer */
            val interruptBuffer = ByteBuffer.allocate(16)
            val inBuffer = ByteBuffer.allocate(65536)

            /* ask to receive EP interrupt */
            val interruptRequest = UsbRequest()
            interruptRequest.initialize(usbDeviceConnection, interruptIn)
            interruptRequest.queue(interruptBuffer, 16)

            /* ask to receive EP Bulk In */
            val inRequest = UsbRequest()
            inRequest.initialize(usbDeviceConnection, bulkIn)
            inRequest.queue(inBuffer, 65536)

            while (true) {
                synchronized(this) {
                    if (mStop) {
                        return
                    }
                }
                val request = usbDeviceConnection.requestWait() ?: break
                Log.d(TAG, "${request.clientData}")

                if (request.endpoint === interruptIn) {
                    if (interruptBuffer.get(0) == RDR_to_PC_NotifySlotChange) {

                        /* Save received size */
                        val recvSize = interruptBuffer.position()

                        /* Compute size expected */
                        val size = (highLayer.scardReaderList.slotCount/4) + 1

                        /* Check if we received expected size */
                        if(recvSize < size +1) {
                            Log.d(TAG, "Buffer not complete, expected ${size +1} bytes, received $recvSize byte")
                            break
                        }

                        /* Get slot status data */
                        val data = ByteArray(size)
                        interruptBuffer.position(1)
                        interruptBuffer.get(data, 0, size)

                        /* Recompute CCID Slot Status like in BLE */
                        val ccidStatus = mutableListOf<Byte>()
                        ccidStatus.add(highLayer.scardReaderList.slotCount.toByte())
                        ccidStatus.addAll(data.toMutableList())

                        Log.d(TAG, "Received data on interruptIn, value : ${ccidStatus.toHexString()}")

                        /* Post message to USB layer thread */
                        handler.post {
                            onInterruptIn(ccidStatus.toByteArray())
                        }
                    }
                    else {
                        Log.d(TAG, "Unknown interruptIn" + Integer.toHexString(interruptBuffer.get(0).toInt()))
                    }

                    /* enable receive again */
                    interruptBuffer.rewind()
                    interruptRequest.queue(interruptBuffer, 16)

                }
                else if(request.endpoint == bulkIn) {

                    val recvSize = inBuffer.position()
                    val rxBuffer = ByteArray(recvSize)
                    inBuffer.position(0)
                    inBuffer.get(rxBuffer, 0, recvSize)

                    /* Compute expected size */
                    val ccidSize =  highLayer.scardReaderList.ccidHandler.getCcidLength(rxBuffer)


                    /* Check if we received expected size */
                    if(recvSize < ccidSize + CcidFrame.HEADER_SIZE) {
                        Log.d(TAG, "Buffer not complete, expected ${ccidSize + CcidFrame.HEADER_SIZE} bytes, received $recvSize byte")
                        break
                    }

                    Log.d(TAG, "Read ${rxBuffer.toHexString()} on bulkIn")

                    /* Post message to USB layer thread */
                    handler.post {
                        onBulkIn(rxBuffer)
                    }

                    /* enable receive again */
                    inBuffer.rewind()
                    inRequest.queue(inBuffer, 65536)
                }
            }

            /* free USB listener */
            interruptRequest.close()
            inRequest.close()

            Log.d(TAG, "localThread exit ")
        }
    }
}