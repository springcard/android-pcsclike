/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import android.hardware.usb.*
import android.util.Log
import com.springcard.pcsclike.*
import com.springcard.pcsclike.ccid.*
import com.springcard.pcsclike.utils.*


internal class UsbLayer(internal var usbDevice: UsbDevice, private var callbacks: SCardReaderListCallback, internal var scardReaderList : SCardReaderList): CommunicationLayer(callbacks, scardReaderList) {

    private val TAG = this::class.java.simpleName

    private val lowLayer: UsbLowLevel =
        UsbLowLevel(this)

    private val SPRINGCARD_VID = 0x1C34
    private fun isSpringCardDevice() : Boolean = true // usbDevice.vendorId == SPRINGCARD_VID uncomment to support other products


    override fun process(event: ActionEvent) {
        scardReaderList.callbacksHandler.post {
            Log.d(TAG, "Current state = ${currentState.name}")
            // Memo CRA : SCardDevice instance = 0x${System.identityHashCode(scardDevice).toString(16).toUpperCase()}

            when (currentState) {
                State.Disconnected -> handleStateDisconnected(event)
                //State.Connecting -> handleStateConnecting(event)
                State.ReadingInformation -> handleStateReadingInformation(event)
                //State.DiscoveringGatt -> handleStateDiscovering(event)
                //State.SubscribingNotifications -> handleStateSubscribingNotifications(event)
                State.ReadingSlotsName -> handleStateReadingSlotsName(event)
                //State.Authenticate -> handleStateAuthenticate(event)
                State.ConnectingToCard -> handleStateConnectingToCard(event)
                State.Idle ->  handleStateIdle(event)
                State.ReadingPowerInfo -> handleStateReadingPowerInfo(event)
                State.WaitingResponse -> handleStateWaitingResponse(event)
                //State.Disconnecting ->  handleStateDisconnecting(event)
                else -> Log.w(TAG, "Unhandled State : $currentState")
            }
        }
    }

    private var indexInfoCmd = 0
    private fun handleStateDisconnected(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionCreate -> {
                currentState = State.Connecting

                /* Save context if we need to try to reconnect */
                context = event.ctx

                if (lowLayer.connect() && lowLayer.getSlotCount() != 0) {

                    val slotCount = lowLayer.getSlotCount()
                    /* Add n new readers */
                    for (i in 0 until slotCount) {
                        scardReaderList.readers.add(SCardReader(scardReaderList))
                    }

                    /* Retrieve readers name */
                    if(scardReaderList.isCorrectlyKnown) {
                        for (i in 0 until slotCount) {
                            scardReaderList.readers[i].name = scardReaderList.constants.slotsName[i]
                            scardReaderList.readers[i].index = i
                        }
                    }
                    else {
                        /* Otherwise set temporary names */
                        for (i in 0 until slotCount) {
                            scardReaderList.readers[i].name =  "Slot $i"
                            scardReaderList.readers[i].index = i
                        }
                    }

                    val infoArray = lowLayer.getDeviceInfo()

                    scardReaderList.constants.vendorName = infoArray[0]
                    scardReaderList.constants.productName = infoArray[1]
                    scardReaderList.constants.serialNumber = infoArray[2]
                    scardReaderList.constants.serialNumberRaw = infoArray[2].hexStringToByteArray()

                    currentState = State.ReadingInformation

                    /* Trigger 1st APDU to get 1st info */
                    indexInfoCmd = 1 // 1st command
                    indexSlots = 0   // reset indexSlot cpt
                    lowLayer.bulkOutTransfer(getNextInfoCommand(indexInfoCmd))

                } else {
                    currentState = State.Disconnected
                    postReaderListError(
                        SCardError.ErrorCodes.DEVICE_NOT_CONNECTED,
                        "Could not connect to device"
                    )
                }

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
                            if(indexInfoCmd == 2 && isSpringCardDevice()) {
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
                    lowLayer.bulkOutTransfer(command)
                }
                else {
                    /* Go to next step */
                    if(isSpringCardDevice()) {
                        indexSlots = 0
                        currentState = State.ReadingSlotsName
                        /* Trigger 1st APDU to get slot name */
                        lowLayer.bulkOutTransfer(scardReaderList.ccidHandler.scardControl("582100".hexStringToByteArray()))
                    }
                    else {
                        /* If there are one card present on one or more slot --> go to state ConnectingToCard */
                        processNextSlotConnection()
                    }
                }
            }
            else -> handleCommonActionEvents(event)
        }
    }


    private fun handleStateReadingSlotsName(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventOnUsbDataIn -> {
                /* Response */
                val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(event.data)
                val slotName = ccidResponse.payload.slice(1 until ccidResponse.payload.size).toByteArray().toString(charset("ASCII"))
                Log.d(TAG, "Slot $indexSlots name : $slotName")
                scardReaderList.readers[indexSlots].name = slotName
                scardReaderList.readers[indexSlots].index = indexSlots

                /* Get next slot name */
                indexSlots++
                if (indexSlots < scardReaderList.readers.size) {
                    lowLayer.bulkOutTransfer(
                        scardReaderList.ccidHandler.scardControl("58210$indexSlots".hexStringToByteArray())
                    )
                } else {
                    Log.d(TAG, "Reading readers name finished")
                    /* Check if there is some card already presents on the slots */
                    listReadersToConnect.clear()
                    for (slot in scardReaderList.readers) {
                        /* We don't have to check cardConnected because in BLE it will  */
                        /* not be powered whereas in USB it will be the case */
                        if (slot.cardPresent /*&& !slot.cardConnected*/) {
                            Log.d(TAG, "Slot ${slot.index}, card present, must connect to this card")
                            listReadersToConnect.add(slot)
                        }
                    }

                    /* If there are one card present on one or more slot --> go to state ConnectingToCard */
                    processNextSlotConnection()
                }
            }
            else -> handleCommonActionEvents(event)
        }
    }


    private fun handleStateConnectingToCard(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionWriting -> {
                Log.d(TAG, "Writing ${event.command.toHexString()}")

                /* Trigger 1st write operation */
                lowLayer.bulkOutTransfer(event.command)
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
                        slot
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

                    /* Remove reader we just processed */
                    listReadersToConnect.remove(slot)

                    /* save ATR */
                    slot.channel.atr = ccidResponse.payload

                    /* Change state if we are at the end of the list */
                    processNextSlotConnection()

                    /* Send callback AFTER checking state of the slots */
                    scardReaderList.postCallback({
                        callbacks.onReaderStatus(
                            slot,
                            slot.cardPresent,
                            slot.cardConnected
                        )
                    })
                }
            }
            else -> handleCommonActionEvents(event)
        }
    }


    private fun handleStateIdle(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionWriting -> {
                currentState = State.WaitingResponse
                Log.d(TAG, "Writing ${event.command.toHexString()}")

                /* Trigger 1st write operation */
                lowLayer.bulkOutTransfer(event.command)
            }
            is ActionEvent.ActionReadPowerInfo -> {
                currentState = State.ReadingPowerInfo
                process(event)
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateWaitingResponse(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventOnUsbDataIn -> {
                /* Check if there are some cards to connect */
                processNextSlotConnection()

                /* Send callback AFTER checking state of the slots */
                analyseResponse(event.data)
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateReadingPowerInfo(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionReadPowerInfo -> {
                currentState = State.Idle

                /* If there are one card present on one or more slot --> go to state ConnectingToCard */
                processNextSlotConnection()

                /* TODO: create an entry point in FW to get this info */
                /* Send callback AFTER checking state of the slots */
                scardReaderList.postCallback({ scardReaderList.callbacks.onPowerInfo(scardReaderList, 1, 100) })
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleCommonActionEvents(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName} (Common)")
        when (event) {
            is ActionEvent.ActionDisconnect -> {
                currentState = State.Disconnected
                lowLayer.disconnect()
                scardReaderList.isAlreadyCreated = false
                scardReaderList.postCallback({ scardReaderList.callbacks.onReaderListClosed(scardReaderList) })

                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))
            }
            is ActionEvent.EventDisconnected -> {
                currentState = State.Disconnected
                scardReaderList.isConnected = false
                scardReaderList.isAlreadyCreated = false
                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))

                scardReaderList.postCallback({ callbacks.onReaderListClosed(scardReaderList) })

                // Reset all lists
                indexSlots = 0
                lowLayer.disconnect()
            }
            is ActionEvent.EventOnUsbInterrupt -> {
                /* Update readers status */
                interpretSlotsStatus(event.data)

                /* Update list of slots to connect (if there is no card error)*/
                for (slot in scardReaderList.readers) {
                    if (!slot.cardPresent && listReadersToConnect.contains(slot)) {
                        Log.d(TAG, "Card gone on slot ${slot.index}, removing slot from listReadersToConnect")
                        listReadersToConnect.remove(slot)
                    } else if (slot.cardPresent && slot.channel.atr.isEmpty() && !listReadersToConnect.contains(slot) && !slot.cardError) {
                        Log.d(TAG, "Card arrived on slot ${slot.index}, adding slot to listReadersToConnect")
                        listReadersToConnect.add(slot)
                    }
                }

                /* If we are idle or already connecting to cards */
                /* And if there is no pending command */
                if((currentState == State.Idle || currentState == State.ConnectingToCard)
                    && !scardReaderList.ccidHandler.pendingCommand){
                    processNextSlotConnection()
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
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
}
