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
import androidx.annotation.RequiresApi
import com.springcard.pcsclike.SCardError
import com.springcard.pcsclike.SCardReader
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.utils.*
import com.springcard.pcsclike.ccid.CcidFrame
import java.nio.ByteBuffer


internal class UsbLowLevel(private val scardReaderList: SCardReaderList, private val usbDevice: UsbDevice): LowLevelLayer {

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

    private lateinit var context: Context

    private val SPRINGCARD_VID = 0x1C34
    private fun isSpringCardDevice() : Boolean =  usbDevice.vendorId == SPRINGCARD_VID // uncomment or set to false to support other products

    private val mWaiterThread by lazy {
        WaiterThread()
    }
    private val handlerThread = HandlerThread("usbHandlerThread")

    private val handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    /* Utilities func */

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun connect(ctx: Context) {

        context = ctx

        /* register to be notified when the device is unplugged */
        context.registerReceiver(mUsbReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))

        /* Connect to device */
        val usbManager: UsbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        try {
            usbDeviceConnection = usbManager.openDevice(usbDevice)
            descriptors = usbDeviceConnection.rawDescriptors
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Could not open device $usbDevice")
            return
        }

        /* Check interface and device class */
        if(usbDevice.deviceClass != 0x0B && usbDevice.deviceClass != 0x00) {
            scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.DUMMY_DEVICE, "Wrong device class: ${usbDevice.deviceClass.toByte().toHexString()}"))
            return
        }

        /* Query for interface */
        if (usbDevice.interfaceCount == 0) {
            scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.DUMMY_DEVICE, "There is no USB interface on this device"))
            return
        }

        /* Try to find the CCID interface */
        for(indexInterface in 0 until usbDevice.interfaceCount) {

            Log.d(TAG, "Interface $indexInterface")

            val usbInterface = usbDevice.getInterface(indexInterface)

            /* Check for endpoint */
            if (usbInterface.endpointCount == 0) {
                Log.w(TAG, "Could not find endpoint")
                break
            }

            /* Grab endpoints */
            if (usbInterface.endpointCount != 3) {
                Log.w(TAG, "CCID interface must only have 3 endpoints")
                break
            }

            /* Check interface and device class */
            if((usbInterface.interfaceClass == 0x0B || usbInterface.interfaceClass == 0x00)) {
                Log.w(TAG, "CCID interface found")
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
            }
            else {
                Log.w(TAG, "Wrong interface class: ${usbInterface.interfaceClass.toUByte().toHexString()}")
                break
            }
        }

        if (!::interruptIn.isInitialized || !::bulkOut.isInitialized || !::bulkIn.isInitialized) {
            Log.e(TAG, "Device ${usbDevice.productName} miss one or more endpoint")
            scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.DUMMY_DEVICE, "Device ${usbDevice.productName} miss one or more endpoint"))
            return
        }

        mWaiterThread.start()

        //************************************************

        val slotCount = getSlotCount()
        if(slotCount != 0) {
            /* Add n new readers */
            for (i in 0 until slotCount) {
                scardReaderList.readers.add(SCardReader(scardReaderList))
            }

            /* Retrieve readers name */
            if(scardReaderList.isCorrectlyKnown) {
                for (i in 0 until slotCount) {
                    scardReaderList.readers[i].name = scardReaderList.constants.slotsName[i]
                    scardReaderList.readers[i].index = i
                    /* Get slot status */
                    scardReaderList.infoToRead.add("0$i".hexStringToByteArray())
                }
            }
            else {
                /* Otherwise set temporary names */
                for (i in 0 until slotCount) {
                    val tempName = "Slot $i"
                    scardReaderList.readers[i].name =  tempName
                    scardReaderList.readers[i].index = i
                    if(isSpringCardDevice()) {
                        /* Get slot name */
                        scardReaderList.infoToRead.add("58210$i".hexStringToByteArray())
                    }
                    else {
                        /* Set dummy values */
                        if(!scardReaderList.constants.slotsName.contains(tempName)) {
                            scardReaderList.constants.slotsName.add(tempName)
                        }
                    }
                    /* Get slot status */
                    scardReaderList.infoToRead.add("0$i".hexStringToByteArray())
                }
            }
        }
        else {
            scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.MISSING_SERVICE, "Slot count is 0"))
            return
        }

        val infoArray = getDeviceInfo()
        scardReaderList.constants.vendorName = infoArray[0]
        scardReaderList.constants.productName = infoArray[1]
        scardReaderList.constants.serialNumber = infoArray[2]
        scardReaderList.constants.serialNumberRaw = infoArray[2].hexStringToByteArray()


        /* Firmware revision string */
        if(isSpringCardDevice()) {
            scardReaderList.infoToRead.add("582006".hexStringToByteArray())
        }
        else {
            /* Set dummy values */
            scardReaderList.constants.firmwareVersion = ""
            scardReaderList.constants.firmwareVersionMajor = 0
            scardReaderList.constants.firmwareVersionMinor = 0
            scardReaderList.constants.firmwareVersionBuild = 0
        }
        scardReaderList.commLayer.onCreateFinished()
    }

    private fun getSlotCount(): Int {
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

    private fun getDeviceInfo(): Array<String> {

        /* Get info directly from USB */
        val manufacturerName: String
        val productName: String
        val serialNumber: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            manufacturerName = usbDevice.manufacturerName ?: ""
            productName = usbDevice.productName ?: ""
            serialNumber = usbDevice.serialNumber ?: ""
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

    override fun disconnect() {
        usbDeviceConnection.releaseInterface(usbDevice.getInterface(0))
        usbDeviceConnection.close()
        stop()
        context.unregisterReceiver(mUsbReceiver)
        scardReaderList.commLayer.onDisconnected()
    }

    override fun close() {
        Log.d(TAG, "Close")
        // TODO CRA close USB
    }


    override fun write(data: List<Byte>) {
        bulkOutTransfer(data.toByteArray())
    }


    /* USB Entries / Outputs */

    private fun onInterruptIn(data: ByteArray) {
        Log.d(TAG, "Received data on interruptIn, value : ${data.toHexString()}")
        /* Update readers status */
        val readCcidStatus = data.drop(1).toByteArray()
        scardReaderList.commLayer.onStatusReceived(readCcidStatus)
    }

    private fun onBulkIn(data: ByteArray) {
        Log.d(TAG, "Read ${data.toHexString()} on bulkIn")
        scardReaderList.commLayer.onResponseReceived(data)
    }

    private fun bulkOutTransfer(data: ByteArray) {
        Log.d(TAG, "Write ${data.toHexString()} on bulkOut")
        usbDeviceConnection.bulkTransfer(bulkOut, data, data.size, BULK_TIMEOUT_MS)
    }

    private var mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.apply {

                    if(device == usbDevice) {
                        /* Method that cleans up and closes communication with the device */
                        disconnect()
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
                        val size = (scardReaderList.slotCount/4) + 1

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
                        ccidStatus.add(scardReaderList.slotCount.toByte())
                        ccidStatus.addAll(data.toMutableList())



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
                    val ccidSize =  scardReaderList.ccidHandler.getCcidLength(rxBuffer)


                    /* Check if we received expected size */
                    if(recvSize < ccidSize + CcidFrame.HEADER_SIZE) {
                        Log.d(TAG, "Buffer not complete, expected ${ccidSize + CcidFrame.HEADER_SIZE} bytes, received $recvSize byte")
                        break
                    }

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