/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.util.Log
import java.nio.ByteBuffer
import android.hardware.usb.UsbRequest
import android.os.Handler
import android.os.HandlerThread


internal class UsbLayer(private var usbDevice: UsbDevice, private var callbacks: SCardReaderListCallback, private var scardReaderList : SCardReaderList): CommunicationLayer(callbacks, scardReaderList) {

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

    val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }


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
                State.ReadingInformation -> handleStateReadingInformation(event)
                /*State.DiscoveringGatt -> handleStateDiscovering(event)

                State.SubscribingNotifications -> handleStateSubscribingNotifications(event)*/
                State.ReadingSlotsName -> handleStateReadingSlotsName(event)
                /*State.Authenticate -> handleStateAuthenticate(event)*/
                State.ConnectingToCard -> handleStateConnectingToCard(event)
                State.Idle ->  handleStateIdle(event)
                //State.ReadingPowerInfo -> handleStateReadingPowerInfo(event)
                State.WaitingResponse -> handleStateWaitingResponse(event)
                // State.Disconnecting ->  handleStateDisconnecting(event)*/
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

                if (connect()) {
                    currentState = State.Connected
                    scardReaderList.handler.post { callbacks.onConnect(scardReaderList) }
                } else {
                    currentState = State.Disconnected
                    postReaderListError(
                        SCardError.ErrorCodes.DEVICE_NOT_CONNECTED,
                        "Could not connect to device"
                    )
                }
                mWaiterThread.start()
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }




    private var indexInfoCmd = 0
    private fun handleStateConnected(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionCreate -> {
                currentState = State.ReadingInformation

                /* Get info directly from USB */
                scardReaderList.vendorName = usbDevice.manufacturerName!!
                scardReaderList.productName = usbDevice.productName!!
                scardReaderList.serialNumber = usbDevice.serialNumber!!
                scardReaderList.serialNumberRaw = usbDevice.serialNumber!!.hexStringToByteArray()

                /* Trigger 1st APDU to get 1st info */
                indexInfoCmd = 1 // 1st command
                indexSlots = 0   // reset indexSlot cpt
                bulkOutTransfer(getNextInfoCommand(indexInfoCmd))
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleStateReadingInformation(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")


        when (event) {
            is ActionEvent.EventOnUsbDataIn -> {

                val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(event.data)

                when {
                    ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_SlotStatus.value -> when (scardReaderList.ccidHandler.commandSend) {
                        CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus -> {

                            /* Update slot concerned */
                            interpretSlotsStatusInCcidHeader(
                                ccidResponse.slotStatus,
                                scardReaderList.readers[ccidResponse.slotNumber.toInt()]
                            )
                        }
                        else -> {
                            postReaderListError(
                                SCardError.ErrorCodes.DIALOG_ERROR,
                                "Unexpected CCID response (${ccidResponse.code}) for command : ${scardReaderList.ccidHandler.commandSend}"
                            )
                        }
                    }
                    ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_Escape.value -> when (scardReaderList.ccidHandler.commandSend) {
                        CcidCommand.CommandCode.PC_To_RDR_Escape -> {
                            if(indexInfoCmd == 2) {
                                getVersionFromRevString(ccidResponse.payload.drop(1).toByteArray().toString(charset("ASCII")))
                            }
                            Log.d(TAG, " ${ccidResponse.payload.toHexString()}")
                        }
                        else -> {
                            postReaderListError(
                                SCardError.ErrorCodes.DIALOG_ERROR,
                                "Unexpected CCID response (${ccidResponse.code}) for command : ${scardReaderList.ccidHandler.commandSend}"
                            )
                        }
                    }
                    else -> {
                        postReaderListError(
                            SCardError.ErrorCodes.DIALOG_ERROR,
                            "Unexpected CCID response (${ccidResponse.code})"
                        )
                    }
                }

                indexInfoCmd++
                val command = getNextInfoCommand(indexInfoCmd)

                if(command.isNotEmpty()) {
                    bulkOutTransfer(command)
                }
                else {
                    /* Go to next step */
                    indexSlots = 0
                    currentState = State.ReadingSlotsName
                    /* Trigger 1st APDU to get slot name */
                    bulkOutTransfer(scardReaderList.ccidHandler.scardControl("582100".hexStringToByteArray()))
                }
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }


    private fun handleStateReadingSlotsName(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventOnUsbDataIn -> {
                /* Response */
                val slotName = event.data.slice(11 until event.data.size).toByteArray().toString(charset("ASCII"))
                Log.d(TAG, "Slot $indexSlots name : $slotName")
                scardReaderList.readers[indexSlots].name = slotName
                scardReaderList.readers[indexSlots].index = indexSlots

                /* Get next slot name */
                indexSlots++
                if (indexSlots < scardReaderList.readers.size) {
                    bulkOutTransfer(
                        scardReaderList.ccidHandler.scardControl("58210$indexSlots".hexStringToByteArray())
                    )
                } else {
                    Log.d(TAG, "Reading readers name finished")
                    /* Check if there is some card already presents on the slots */
                    listReadersToConnect.clear()
                    for (slot in scardReaderList.readers) {
                        /* We don't have to check cardPowered because in BLE it will  */
                        /* not be powered whereas in USB it will be the case */
                        if (slot.cardPresent /*&& !slot.cardPowered*/) {
                            Log.d(TAG, "Slot: ${slot.name}, card present --> must connect to this card")
                            listReadersToConnect.add(slot)
                        }
                    }

                    /* If there are one card present on one or more slot --> go to state ConnectingToCard */
                    processNextSlotConnection()
                }
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }


    private fun handleStateConnectingToCard(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionWriting -> {
                Log.d(TAG, "Writing ${event.command.toHexString()}")

                /* Trigger 1st write operation */
                bulkOutTransfer(event.command)
            }
            is ActionEvent.EventOnUsbDataIn -> {

                /* Put data in ccid frame */
                val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(event.data)
                val slot = scardReaderList.readers[ccidResponse.slotNumber.toInt()]

                /* Update slot status (present, powered) */
                interpretSlotsStatusInCcidHeader(
                    ccidResponse.slotStatus,
                    slot
                )

                /* Check slot error */
                if (!interpretSlotsErrorInCcidHeader(
                        ccidResponse.slotError,
                        ccidResponse.slotStatus,
                        slot,
                        false // do not post callback
                    )
                ) {
                    Log.d(TAG, "Error, do not process CCID packet, returning to Idle state")

                    /* Remove reader we just processed */
                    listReadersToConnect.remove(slot)

                    processNextSlotConnection()
                    /* Do not go further */
                    return
                }

                Log.d(TAG, "Frame complete, length = ${ccidResponse.length}")

                if (ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value) {

                    /* save ATR */
                    slot.channel.atr = ccidResponse.payload
                    /* set cardPowered flag */
                    slot.cardPowered = true
                    /* Remove reader we just processed */
                    listReadersToConnect.remove(slot)
                    /* Change state if we are at the end of the list */
                    processNextSlotConnection()
                }

            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }


    private fun handleStateIdle(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionDisconnect -> {
                currentState = State.Disconnecting
                disconnect()
                context.unregisterReceiver(mUsbReceiver)
            }
            is ActionEvent.ActionWriting -> {
                currentState = State.WaitingResponse
                Log.d(TAG, "Writing ${event.command.toHexString()}")

                /* Trigger 1st write operation */
                bulkOutTransfer(event.command)
            }
            is ActionEvent.EventOnUsbInterrupt -> {
                /* Update readers status */
                interpretSlotsStatus(event.data)
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleStateWaitingResponse(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventOnUsbDataIn -> {

                analyseResponse(event.data)
            }
            is ActionEvent.EventOnUsbInterrupt -> {
                /* Update readers status */
                interpretSlotsStatus(event.data)
            }
            else -> Log.w(TAG ,"Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    /* Utilities func */

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

            context.registerReceiver(mUsbReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))

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



    private fun stop() {
        synchronized(mWaiterThread) {
            mWaiterThread.mStop = true
        }
    }


    private fun disconnect() {
        usbDeviceConnection.releaseInterface(usbDevice.getInterface(0))
        usbDeviceConnection.close()
        stop()
        scardReaderList.handler.post{ scardReaderList.callbacks.onReaderListClosed(scardReaderList) }
    }

    private fun getNextInfoCommand(index: Int): ByteArray {
        val res: ByteArray
        when(index) {
            1 -> {
                /* Add get slot status for each slot */
                res =  scardReaderList.ccidHandler.scardStatus(indexSlots)
                indexSlots++
                return res
            }
            2 -> {
                /* Firmware revision string */
                res =  scardReaderList.ccidHandler.scardControl("582006".hexStringToByteArray())

            }
            else -> {
                Log.d(TAG, "End of the list -> create empty ByteArray")
                res = ByteArray(0)
            }
        }
        return res
    }


    /* USB Entries / Outputs */

    private fun onInterruptIn(data: ByteArray) {
        process(ActionEvent.EventOnUsbInterrupt(data))
    }


    private fun onBulkIn(data: ByteArray) {
        process(ActionEvent.EventOnUsbDataIn(data))
    }

    private fun bulkOutTransfer(data: ByteArray) {
        usbDeviceConnection.bulkTransfer(bulkOut, data, data.size, BULK_TIMEOUT_MS)
    }

    var mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.apply {

                    if(device == usbDevice) {
                        /* Method that cleans up and closes communication with the device */
                        process(ActionEvent.ActionDisconnect())
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
