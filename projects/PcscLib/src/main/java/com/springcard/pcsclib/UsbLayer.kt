package com.springcard.pcsclib

import android.content.Context
import android.hardware.usb.*
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import android.hardware.usb.UsbRequest
import android.os.Handler
import android.os.HandlerThread


internal class UsbLayer(private var usbDevice: UsbDevice, private var callbacks: SCardReaderListCallback, private var scardReaderList : SCardReaderList): CommunicationLayer(callbacks, scardReaderList) {

    private val TAG = this::class.java.simpleName

    /* useful constants */
    private val BULK_TIMEOUT_MS: Byte = 100
    private val spVendorId = 7220

    /* communication constants */
    private val RDR_to_PC_NotifySlotChange = 0x50.toByte()
    private val RDR_To_PC_DataBlock = 0x80.toByte()
    private val RDR_To_PC_SlotStatus = 0x81.toByte()

    /* current action type */
    private val WAIT_NONE: Byte = 0
    private val WAIT_ATR: Byte = 1
    private val WAIT_APDU: Byte = 2


    /* USB Device Endpoints */
    private lateinit var bulkOut: UsbEndpoint
    private lateinit var  bulkIn: UsbEndpoint
    private lateinit var  interruptIn: UsbEndpoint

    /* USB access points */
    private lateinit var usbDeviceConnection: UsbDeviceConnection

    val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    /* tasking */
    private lateinit var localThread: Thread
    private var operationThread: Thread? = null

    /* reader part */
    private val awaitingOperation = ArrayBlockingQueue<Int>(5)
    private val apduResult = ArrayBlockingQueue<CcidResponse>(5)

    /* current operation */
    private var currentOperation = WAIT_NONE
    private val currentSlot = 0
    val RAPDU_ERROR = byteArrayOf(0xFF.toByte(), 0xFF.toByte())

    private val mWaiterThread = WaiterThread()
    private val handlerThread by lazy {
        HandlerThread("usbHandlerThread")
    }
    private val handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }


    override fun process(event: ActionEvent) {

        scardReaderList.handler.post {

            Log.d(TAG, "Current state = ${currentState.name}")
            // Memo CRA : SCardDevice instance = 0x${System.identityHashCode(scardDevice).toString(16).toUpperCase()}

            when (currentState) {
                State.Disconnected -> handleStateDisconnected(event)
                //State.Connecting -> handleStateConnecting(event)
            State.Connected -> handleStateConnected(event)
            /*State.DiscoveringGatt -> handleStateDiscovering(event)
            State.ReadingInformation -> handleStateReadingInformation(event)
            State.SubscribingNotifications -> handleStateSubscribingNotifications(event)
            State.ReadingSlotsName ->  handleStateReadingSlotsName(event)
            State.Authenticate -> handleStateAuthenticate(event)
            State.ConnectingToCard -> handleStateConnectingToCard(event)
            State.Idle ->  handleStateIdle(event)
            State.ReadingPowerInfo -> handleStateReadingPowerInfo(event)
            State.WritingCommand -> handleStateWritingCommand(event)
            State.WaitingResponse -> handleStateWaitingResponse(event)
            State.Disconnecting ->  handleStateDisconnecting(event)*/
                else -> Log.w(TAG, "Unhandled State : $currentState")
            }
        }
    }

    private fun handleStateDisconnected(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionConnect -> {
                currentState = State.Connecting

                /* save context if we need to try to reconnect */
                context = event.ctx

                if(connect()) {
                    currentState = State.Connected
                    scardReaderList.handler.post {callbacks.onConnect(scardReaderList)}
                }
                else {
                    currentState = State.Disconnected
                    postReaderListError(
                        SCardError.ErrorCodes.DEVICE_NOT_CONNECTED,
                        "Could not connect to device")
                }
                Log.d(TAG, "powerUP")
                val CARD_POWER_UP = byteArrayOf(0x62, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                CARD_POWER_UP[5] = 0
                CARD_POWER_UP[6] = 0
                usbDeviceConnection.bulkTransfer(bulkOut, CARD_POWER_UP, CARD_POWER_UP.size, BULK_TIMEOUT_MS.toInt())
                mWaiterThread.start()
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleStateConnected(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionCreate -> {
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }
    }

    fun stop() {
        synchronized(mWaiterThread) {
            mWaiterThread.mStop = true
        }
    }

    private fun connect(): Boolean {

        /* query for interface */
        if (usbDevice.interfaceCount == 0) {
            Log.e(TAG, "Could not find interface ")
            return false
        }
        val usbInterface = usbDevice.getInterface(0)

        /* check for endpoint */
        if (usbInterface.endpointCount == 0) {
            Log.e(TAG, "could not find endpoint")
            return false
        }

        /* connect to device */
        try {
            usbDeviceConnection = usbManager.openDevice(usbDevice)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Could not open device $usbDevice")
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
        }

        /* are those endpoints valid ? */
        //TODO

        /* find number of slot present in this reader */
        var curPos = 0
        try {
            val descriptor = usbDeviceConnection.rawDescriptors!!

            /* get number of slots present in this reader */
            while (curPos < descriptor.size) {
                /* read descriptor length */
                val dlen = descriptor[curPos].toInt()
                /* read descriptor type */
                val dtype = descriptor[curPos + 1].toInt()
                /* CCID type ? */
                if (dlen == 0x36 && dtype == 0x21) {
                    val slotCount = descriptor[curPos + 4] + 1
                    Log.d(TAG, "Descriptor found, slotCount = $slotCount")
                    /* Add n new readers */
                    for (i in 0 until slotCount) {
                        scardReaderList.readers.add(SCardReader(scardReaderList))
                    }
                    break
                }
                curPos += dlen
            }
        } catch (e: NullPointerException) {
            return false
        }


        return  scardReaderList.slotCount != 0
    }

    private fun onInterruptIn(data: ByteArray) {
        process(ActionEvent.EventOnUsbInterrupt(data))
    }


    private fun onBulkIn(data: ByteArray) {
        process(ActionEvent.EventOnUsbDtataIn(data))
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

                        /* Compute size expected */
                        val size = (scardReaderList.slotCount/4) + 1

                        /* Check if we received expected size */
                        if(interruptBuffer.position() < size +1) {
                            Log.d(TAG, "Buffer not complete, expected ${size +1} bytes, received ${interruptBuffer.position()} byte")
                            break
                        }

                        /* Get slot status data */
                        val data = ByteArray(size)
                        interruptBuffer.position(1)
                        interruptBuffer.get(data, 0, size)

                        /* Recompute CCID Slot Status like in BLE */
                        val ccidStatus = mutableListOf<Byte>()
                        ccidStatus.add(size.toByte())
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
                    if(interruptBuffer.position() < ccidSize + CcidFrame.HEADER_SIZE) {
                        Log.d(TAG, "Buffer not complete, expected ${ccidSize + CcidFrame.HEADER_SIZE} bytes, received ${interruptBuffer.position()} byte")
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
