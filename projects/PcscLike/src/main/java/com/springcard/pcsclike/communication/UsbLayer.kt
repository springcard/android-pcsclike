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


internal class UsbLayer(internal var usbDevice: UsbDevice, callbacks: SCardReaderListCallback, internal var scardReaderList : SCardReaderList): CommunicationLayer(callbacks, scardReaderList) {

    private val TAG = this::class.java.simpleName

    private val lowLayer: UsbLowLevel =
        UsbLowLevel(this)

    private val SPRINGCARD_VID = 0x1C34
    private fun isSpringCardDevice() : Boolean = true // usbDevice.vendorId == SPRINGCARD_VID uncomment or set to false to support other products


    override fun process(actionEvent: ActionEvent) {
            Log.d(TAG, "Action/Event ${actionEvent.javaClass.simpleName}")
            // Memo CRA : SCardDevice instance = 0x${System.identityHashCode(scardDevice).toString(16).toUpperCase()}

            when (currentState) {
                State.Disconnected -> handleStateDisconnected(actionEvent)
                //State.Connecting -> handleStateConnecting(actionEvent)
                State.ReadingInformation -> handleStateReadingInformation(actionEvent)
                //State.DiscoveringGatt -> handleStateDiscovering(actionEvent)
                //State.SubscribingNotifications -> handleStateSubscribingNotifications(actionEvent)
                State.ReadingSlotsName -> handleStateReadingSlotsName(actionEvent)
                //State.Authenticate -> handleStateAuthenticate(actionEvent)
                State.ConnectingToCard -> handleStateConnectingToCard(actionEvent)
                State.Idle ->  handleStateIdle(actionEvent)
                State.ReadingPowerInfo -> handleStateReadingPowerInfo(actionEvent)
                State.WritingCmdAndWaitingResp -> handleStateWaitingResponse(actionEvent)
                //State.Disconnecting ->  handleStateDisconnecting(actionEvent)
                else -> Log.w(TAG, "Unhandled State : $currentState")
            }
    }

    private var indexInfoCmd = 0
    private fun handleStateDisconnected(actionEvent: ActionEvent) {
        when (actionEvent) {
            is Action.Create -> {
                currentState = State.Connecting

                /* Save context if we need to try to reconnect */
                context = actionEvent.ctx

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
                    PC_to_RDR(getNextInfoCommand(indexInfoCmd)!!)

                } else {
                    currentState = State.Disconnected
                    postReaderListError(
                        SCardError.ErrorCodes.DEVICE_NOT_CONNECTED,
                        "Could not connect to device"
                    )
                }

            }
            else -> Log.e(TAG, "Unwanted Action/Event ${actionEvent.javaClass.simpleName}")
        }
    }


    private fun handleStateReadingInformation(actionEvent: ActionEvent) {
        when (actionEvent) {
            is Event.OnUsbDataIn -> {

                val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(actionEvent.data)

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
                            if(indexInfoCmd == 1 && isSpringCardDevice()) {
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

                if(command != null) {
                    PC_to_RDR(command)
                }
                else {
                    /* Go to next step */
                    if(isSpringCardDevice()) {
                        indexSlots = 0
                        currentState = State.ReadingSlotsName
                        /* Trigger 1st APDU to get slot name */
                        PC_to_RDR(scardReaderList.ccidHandler.scardControl("582100".hexStringToByteArray()))
                    }
                    else {
                        /* If there are one card present on one or more slot --> go to state ConnectingToCard */
                        mayPostReaderListCreated()
                    }
                }
            }
            else -> handleCommonActionEvents(actionEvent)
        }
    }


    private fun handleStateReadingSlotsName(actionEvent: ActionEvent) {
        when (actionEvent) {
            is Event.OnUsbDataIn -> {
                /* Response */
                val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(actionEvent.data)
                val slotName = ccidResponse.payload.slice(1 until ccidResponse.payload.size).toByteArray().toString(charset("ASCII"))
                Log.d(TAG, "Slot $indexSlots name : $slotName")
                scardReaderList.readers[indexSlots].name = slotName
                scardReaderList.readers[indexSlots].index = indexSlots

                /* Get next slot name */
                indexSlots++
                if (indexSlots < scardReaderList.readers.size) {
                    PC_to_RDR(
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
                    mayPostReaderListCreated()
                }
            }
            else -> handleCommonActionEvents(actionEvent)
        }
    }


    private fun handleStateConnectingToCard(actionEvent: ActionEvent) {
        when (actionEvent) {
            is Action.Writing -> {
                val updatedCcidBuffer = scardReaderList.ccidHandler.updateCcidCommand(actionEvent.command)
                Log.d(TAG, "Writing ${actionEvent.command.raw.toHexString()}")

                /* Trigger 1st write operation */
                lowLayer.bulkOutTransfer(updatedCcidBuffer)
            }
            is Event.OnUsbDataIn -> {

                /* Put data in ccid frame */
                val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(actionEvent.data)
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

                    mayPostReaderListCreated()
                    /* Do not go further */
                    return
                }

                Log.d(TAG, "Frame complete, length = ${ccidResponse.length}")

                if (ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value) {

                    /* Remove reader we just processed */
                    listReadersToConnect.remove(slot)

                    /* save ATR */
                    slot.channel.atr = ccidResponse.payload

                    /* Send callback AFTER checking state of the slots */
                    scardReaderList.postCallback({
                        callbacks.onReaderStatus(
                            slot,
                            slot.cardPresent,
                            slot.cardConnected
                        )
                    })

                    mayPostReaderListCreated()
                }
            }
            else -> handleCommonActionEvents(actionEvent)
        }
    }


    private fun handleStateIdle(actionEvent: ActionEvent) {
        when (actionEvent) {
            is Action.Writing -> {
                currentState = State.WritingCmdAndWaitingResp
                PC_to_RDR(actionEvent.command)
            }
            is Action.ReadPowerInfo -> {
                currentState = State.ReadingPowerInfo
                process(actionEvent)
            }
            else -> handleCommonActionEvents(actionEvent)
        }
    }

    private fun handleStateWaitingResponse(actionEvent: ActionEvent) {
        when (actionEvent) {
            is Event.OnUsbDataIn -> {
                analyseResponse(actionEvent.data)
                mayPostReaderListCreated()
            }
            else -> handleCommonActionEvents(actionEvent)
        }
    }

    private fun handleStateReadingPowerInfo(actionEvent: ActionEvent) {
        when (actionEvent) {
            is Action.ReadPowerInfo -> {
                currentState = State.Idle

                /* TODO: create an entry point in FW to get this info */
                /* Send callback AFTER checking state of the slots */
                scardReaderList.postCallback({ callbacks.onPowerInfo(scardReaderList, 1, 100) })
            }
            else -> handleCommonActionEvents(actionEvent)
        }
    }

    private fun handleCommonActionEvents(actionEvent: ActionEvent) {
        Log.d(TAG, "Action/Event ${actionEvent.javaClass.simpleName} (Common)")
        when (actionEvent) {
            is Action.Disconnect -> {
                currentState = State.Disconnected
                lowLayer.disconnect()
                scardReaderList.isAlreadyCreated = false
                scardReaderList.postCallback({ callbacks.onReaderListClosed(scardReaderList) }, true)

                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))
            }
            is Event.Disconnected -> {
                currentState = State.Disconnected
                scardReaderList.isConnected = false
                scardReaderList.isAlreadyCreated = false
                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))

                scardReaderList.postCallback({ callbacks.onReaderListClosed(scardReaderList) }, true)

                // Reset all lists
                indexSlots = 0
                lowLayer.disconnect()
            }
            is Event.OnUsbInterrupt -> {
                /* Update readers status */
                interpretSlotsStatus(actionEvent.data)
                scardReaderList.mayConnectCard()
            }
            else -> Log.w(TAG, "Unwanted Action/Event ${actionEvent.javaClass.simpleName}")
        }
    }

    private fun getNextInfoCommand(index: Int): CcidCommand? {
        val res: CcidCommand?
        when(index) {
            1 -> {
                /* Firmware revision string */
                res =  scardReaderList.ccidHandler.scardControl("582006".hexStringToByteArray())
            }
            else -> {
                if(indexSlots < scardReaderList.slotCount) {
                    /* Add get slot status for each slot */
                    res =  scardReaderList.ccidHandler.scardStatus(indexSlots.toByte())
                    indexSlots++
                }
                else {
                    Log.d(TAG, "End of the list -> create empty ByteArray")
                    res = null
                }
            }
        }
        return res
    }

    override fun writePcToRdr(buffer: ByteArray) {
        lowLayer.bulkOutTransfer(buffer)
    }
}
