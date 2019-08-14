/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import android.content.Context
import android.util.Log
import com.springcard.pcsclike.*
import com.springcard.pcsclike.ccid.*
import com.springcard.pcsclike.utils.hexStringToByteArray
import com.springcard.pcsclike.utils.toHexString

internal enum class SubState {
    Idle,
    Authenticate,
    ReadingSlotsName,
    ConnectingToCards
}

internal abstract class CommunicationLayer(private var scardReaderList : SCardReaderList) {

    private val TAG = this::class.java.simpleName
    protected abstract var lowLayer: LowLevelLayer

    private var creatingSubState: SubState = SubState.Idle
    private var authenticateStep = 0

    /* Actions */

    fun connect(ctx : Context) {
        scardReaderList.machineState.setNewState(State.Creating)
        lowLayer.connect(ctx)
    }

    fun disconnect() {
        scardReaderList.machineState.setNewState(State.Closing)
        lowLayer.disconnect()
    }

    fun writeCommand(ccidCommand: CcidCommand) {
        /* Update sqn, save it and cipher */
        val updatedCcidBuffer = scardReaderList.ccidHandler.updateCcidCommand(ccidCommand)
        Log.d(TAG, "Writing ${ccidCommand.raw.toHexString()} in PC_to_RDR")
        lowLayer.write(updatedCcidBuffer.asList())
    }

    abstract fun wakeUp()

    /* Events */

    fun onCreateFinished() {

        if(scardReaderList.ccidHandler.isSecure && !scardReaderList.ccidHandler.authenticateOk) {
            creatingSubState = SubState.Authenticate
            /* Trigger 1st auth step */
            authenticateStep = 1
            writeCommand(scardReaderList.ccidHandler.scardControl(scardReaderList.ccidHandler.ccidSecure.hostAuthCmd()))
        }
        else if(scardReaderList.slotsNameToRead.size > 0) {
            creatingSubState = SubState.ReadingSlotsName
            /* Trigger 1st read command */
            writeCommand(scardReaderList.ccidHandler.scardControl("58210${scardReaderList.slotsNameToRead[0]}".hexStringToByteArray()))
        }
        else if(scardReaderList.slotsToConnect.size > 0) {
            creatingSubState = SubState.ConnectingToCards
            /* Trigger 1st connect */
            writeCommand(scardReaderList.ccidHandler.scardConnect(scardReaderList.slotsToConnect[0].index.toByte()))
        }
        else {
            creatingSubState = SubState.Idle
            Log.d(TAG, "Everything is done -> post onCreated callback")
            scardReaderList.machineState.setNewState(State.Idle)
        }
    }

    fun onDisconnected() {
        when(scardReaderList.machineState.getCurrentState()) {
            State.Closed -> {
                Log.w(TAG, "Impossible to close device if it's already closed")
            }
            State.Creating, State.Idle, State.Sleeping,
            State.WakingUp, State.WritingCmdAndWaitingResp -> {
                scardReaderList.machineState.setNewState(State.Closing)
                lowLayer.close()
                scardReaderList.machineState.setNewState(State.Closed)
            }
            State.Closing -> {
                lowLayer.close()
                scardReaderList.machineState.setNewState(State.Closed)
            }
            else -> {
                Log.w(TAG, "Impossible state: ${scardReaderList.machineState.getCurrentState()}")
            }
        }
    }

    fun onStatusReceived(data: ByteArray) {
        val listSlotsUpdated = mutableListOf<SCardReader>()
        val error = scardReaderList.ccidHandler.interpretCcidStatus(data, listSlotsUpdated)

        if(error.code != SCardError.ErrorCodes.NO_ERROR) {
            onCommunicationError(error)
            return
        }

        for (slot in listSlotsUpdated) {
            scardReaderList.postCallback {scardReaderList.callbacks.onReaderStatus(slot, slot.cardPresent, slot.cardConnected)}
        }

        scardReaderList.mayConnectCard()
    }

    fun onResponseReceived(data: ByteArray) {
        val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(data)

        Log.d(TAG, "Received ${ccidResponse.raw.toHexString()} in RDR_to_PC")

        when(creatingSubState) {
            SubState.Idle -> interpretResponse(ccidResponse)
            SubState.Authenticate -> interpretResponseAuthenticate(ccidResponse)
            SubState.ReadingSlotsName ->  interpretResponseSlotName(ccidResponse)
            SubState.ConnectingToCards -> interpretResponseConnectingToCard(ccidResponse)
            else -> Log.w(TAG,"Impossible SubState: $creatingSubState")
        }
    }

    fun onCommunicationError(error: SCardError) {
        scardReaderList.lastError = error
        disconnect()
    }

    fun onDeviceState(isGoingToSleep: Boolean) {
        if(isGoingToSleep) {
            scardReaderList.machineState.setNewState(State.Sleeping)
        }
        else {
            scardReaderList.machineState.setNewState(State.Idle)
        }
    }

    /* Utilities func */

    private fun interpretResponse(ccidResponse: CcidResponse) {

        val slot = scardReaderList.readers[ccidResponse.slotNumber.toInt()]

        /* Update slot status (present, powered) */
        scardReaderList.ccidHandler.interpretSlotsStatusInCcidHeader(ccidResponse.slotStatus, slot)

        /* Check slot error */
        val error = scardReaderList.ccidHandler.interpretSlotsErrorInCcidHeader(ccidResponse.slotError, ccidResponse.slotStatus, slot)
        if(error.code != SCardError.ErrorCodes.NO_ERROR) {
            Log.d(TAG, "Error, do not process CCID packet")
            scardReaderList.postCallback {scardReaderList.callbacks.onReaderOrCardError(slot, error)}
            scardReaderList.machineState.setNewState(State.Idle)
            return
        }

        Log.d(TAG, "Frame complete, length = ${ccidResponse.length}")
        when {
            ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_Escape.value -> when (scardReaderList.ccidHandler.commandSend) {
                CcidCommand.CommandCode.PC_To_RDR_Escape -> scardReaderList.postCallback {scardReaderList.callbacks.onControlResponse(
                    scardReaderList,
                    ccidResponse.payload)
                }
                else -> onCommunicationError(SCardError(SCardError.ErrorCodes.DIALOG_ERROR,
                    "Unexpected CCID response (${String.format("%02X", ccidResponse.code)}) for command : ${scardReaderList.ccidHandler.commandSend}"))
            }
            ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value -> when (scardReaderList.ccidHandler.commandSend) {
                CcidCommand.CommandCode.PC_To_RDR_XfrBlock -> {
                    if (ccidResponse.slotNumber > scardReaderList.readers.size) {
                        onCommunicationError(SCardError(
                            SCardError.ErrorCodes.PROTOCOL_ERROR,
                            "Error, slot number specified (${ccidResponse.slotNumber}) greater than maximum slot (${scardReaderList.readers.size - 1}), maybe the packet is incorrect"))
                    } else {
                        scardReaderList.postCallback {scardReaderList.callbacks.onTransmitResponse(
                            slot.channel,
                            ccidResponse.payload
                        )
                        }
                    }
                }
                CcidCommand.CommandCode.PC_To_RDR_IccPowerOn -> {

                    /* save ATR */
                    slot.channel.atr = ccidResponse.payload

                    /* Call callback */

                    /* Eventually remove slot in list if auto-connect */
                    if(scardReaderList.slotsToConnect.size > 0 && scardReaderList.slotsToConnect[0].index.toByte() == ccidResponse.slotNumber) {
                        scardReaderList.slotsToConnect.removeAt(0)
                        scardReaderList.postCallback { scardReaderList.callbacks.onReaderStatus(slot, slot.cardPresent, slot.cardConnected) }
                    }
                    else {
                        /* set cardConnected flag */
                        slot.cardConnected = true
                        scardReaderList.postCallback {scardReaderList.callbacks.onCardConnected(slot.channel)}
                    }
                }
                else -> onCommunicationError(SCardError(
                    SCardError.ErrorCodes.DIALOG_ERROR,
                    "Unexpected CCID response (${String.format("%02X", ccidResponse.code)}) for command : ${scardReaderList.ccidHandler.commandSend}"))
            }
            ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_SlotStatus.value -> when (scardReaderList.ccidHandler.commandSend) {
                CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus -> {
                    /* Do nothing */
                    Log.d(TAG, "Reader Status, Cool! ...but useless")

                    /* Update slot concerned */
                    scardReaderList.ccidHandler.interpretSlotsStatusInCcidHeader(ccidResponse.slotStatus, slot)
                }
                CcidCommand.CommandCode.PC_To_RDR_IccPowerOff -> {
                    slot.cardConnected = false
                    slot.channel.atr = ByteArray(0)
                    scardReaderList.postCallback {scardReaderList.callbacks.onCardDisconnected(slot.channel)}
                }
                CcidCommand.CommandCode.PC_To_RDR_XfrBlock -> {
                    if(slot.cardPresent && !slot.cardPowered) {
                        val scardError = SCardError(
                            SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR,
                            "Transmit invoked, but card not powered"
                        )
                        scardReaderList.postCallback {scardReaderList.callbacks.onReaderOrCardError(slot, scardError)}
                    }
                    // TODO CRA else ...
                }
                CcidCommand.CommandCode.PC_To_RDR_IccPowerOn -> {
                    val channel = slot.channel
                    slot.channel.atr = ccidResponse.payload
                    slot.cardConnected = true
                    scardReaderList.postCallback {scardReaderList.callbacks.onCardConnected(channel)}
                    // TODO onReaderOrCardError
                }
                else -> onCommunicationError(SCardError(SCardError.ErrorCodes.DIALOG_ERROR,
                    "Unexpected CCID response (${String.format("%02X", ccidResponse.code)}) for command : ${scardReaderList.ccidHandler.commandSend}"))
            }
            else -> onCommunicationError(SCardError(SCardError.ErrorCodes.DIALOG_ERROR,
                "Unknown CCID response (${String.format("%02X", ccidResponse.code)}) for command : ${scardReaderList.ccidHandler.commandSend}"))
        }
        scardReaderList.machineState.setNewState(State.Idle)
    }

    private fun interpretResponseAuthenticate(ccidResponse: CcidResponse) {
        if(authenticateStep == 1) {

            if(scardReaderList.ccidHandler.ccidSecure.deviceRespStep1(ccidResponse.payload)) {
                authenticateStep = 2
                writeCommand(scardReaderList.ccidHandler.scardControl(
                    scardReaderList.ccidHandler.ccidSecure.hostCmdStep2(ccidResponse.payload.toMutableList())
                ))
            }
            else {
                onCommunicationError(SCardError(SCardError.ErrorCodes.AUTHENTICATION_ERROR, "Authentication failed at step 1"))
            }
        }
        else if(authenticateStep == 2) {
            if(scardReaderList.ccidHandler.ccidSecure.deviceRespStep3(ccidResponse.payload)) {
                scardReaderList.ccidHandler.authenticateOk = true
                Log.d(TAG, "Authenticate succeed")
                onCreateFinished()
            }
            else {
                onCommunicationError(SCardError(SCardError.ErrorCodes.AUTHENTICATION_ERROR, "Authentication failed at step 3"))
            }
        }
    }

    private fun interpretResponseSlotName(ccidResponse: CcidResponse) {
        /* Response */
        val slotName = ccidResponse.payload.slice(1 until ccidResponse.payload.size).toByteArray().toString(charset("ASCII"))
        /* Get index of slot being processed */
        val slotIndex = scardReaderList.slotsNameToRead[0]
        Log.d(TAG, "Slot $slotIndex name : $slotName")
        scardReaderList.readers[slotIndex].name = slotName
        scardReaderList.readers[slotIndex].index = slotIndex

        if(!scardReaderList.constants.slotsName.contains(slotName)) {
            scardReaderList.constants.slotsName.add(slotName)
        }

        /* Remove slot we just processed */
        scardReaderList.slotsNameToRead.removeAt(0)

        /* Get next slot name */
        if(scardReaderList.slotsNameToRead.size > 0) {
            writeCommand(scardReaderList.ccidHandler.scardControl("58210${scardReaderList.slotsNameToRead[0]}".hexStringToByteArray()))
        }
        else {
            Log.d(TAG, "ReadingSlotsName succeed")
            onCreateFinished()
        }
    }

    private fun interpretResponseConnectingToCard(ccidResponse: CcidResponse) {

        val slot = scardReaderList.readers[ccidResponse.slotNumber.toInt()]

        /* Remove reader we just processed */
        scardReaderList.slotsToConnect.remove(slot)

        /* Update slot status (present, powered) */
        scardReaderList.ccidHandler.interpretSlotsStatusInCcidHeader(ccidResponse.slotStatus, slot)

        /* Check slot error */
        val error = scardReaderList.ccidHandler.interpretSlotsErrorInCcidHeader(ccidResponse.slotError, ccidResponse.slotStatus, slot)
        if(error.code != SCardError.ErrorCodes.NO_ERROR) {
            Log.w(TAG, "Error, do not process CCID packet")
            Log.w(TAG, "Error: ${error.code.name}, ${error.detail}")
            /* Do not send callback because we are not connected */
            //scardReaderList.postCallback({scardReaderList.callbacks.onReaderOrCardError(slot, error)})

            /* Connect next card */
            if(scardReaderList.slotsToConnect.size > 0) {
                writeCommand(scardReaderList.ccidHandler.scardConnect(scardReaderList.slotsToConnect[0].index.toByte()))
            }
            else {
                Log.d(TAG, "ConnectingToCards succeed")
                onCreateFinished()
            }
            return
        }

        Log.d(TAG, "Frame complete, length = ${ccidResponse.length}")

        if (ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value) {
            /* save ATR */
            slot.channel.atr = ccidResponse.payload
        }
        else {
            Log.w(TAG, "Unexpected CCID response code: ${ccidResponse.code}")
        }

        /* Connect next card */
        if(scardReaderList.slotsToConnect.size > 0) {
            writeCommand(scardReaderList.ccidHandler.scardConnect(scardReaderList.slotsToConnect[0].index.toByte()))
        }
        else {
            Log.d(TAG, "ConnectingToCards succeed")
            onCreateFinished()
        }
    }
}
