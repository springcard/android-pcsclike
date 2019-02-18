/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log


internal enum class State{
    Disconnected,
    Connecting,
    Connected,
    DiscoveringGatt,
    ReadingInformation,
    SubscribingNotifications,
    ReadingSlotsName,
    Authenticate,
    ConnectingToCard,
    Idle,
    ReadingPowerInfo,
    WritingCommand,
    WaitingResponse,
    Disconnecting
}


internal sealed class ActionEvent {
    class ActionConnect(val ctx: Context) : ActionEvent()
    class EventConnected : ActionEvent()
    class ActionCreate : ActionEvent()
    class EventServicesDiscovered(val status: Int) : ActionEvent()
    class EventDescriptorWrite(val descriptor: BluetoothGattDescriptor, val status: Int) : ActionEvent()
    class EventCharacteristicChanged(val characteristic: BluetoothGattCharacteristic) : ActionEvent()
    class EventCharacteristicWrite(val characteristic: BluetoothGattCharacteristic, val status: Int) : ActionEvent()
    class EventCharacteristicRead(val characteristic: BluetoothGattCharacteristic, val status: Int) : ActionEvent()
    class EventOnUsbInterrupt(val data: ByteArray) : ActionEvent()
    class EventOnUsbDataIn(val data: ByteArray) : ActionEvent()
    class ActionWriting(val command: ByteArray) : ActionEvent()
    class ActionAuthenticate : ActionEvent()
    class ActionDisconnect : ActionEvent()
    class EventDisconnected : ActionEvent()
    class ActionReadPowerInfo : ActionEvent()
}


internal abstract class CommunicationLayer(private var callbacks: SCardReaderListCallback, private var scardReaderList : SCardReaderList) {

    private val TAG = this::class.java.simpleName
    protected var currentState = State.Disconnected
    protected lateinit var context: Context


    protected var indexSlots: Int = 0
    protected var listReadersToConnect = mutableListOf<SCardReader>()

    abstract fun process(event: ActionEvent)

    /* Post error callbacks */

    internal fun postReaderListError(code : SCardError.ErrorCodes, detail: String, isFatal: Boolean = true) {
        Log.e(TAG, "Error readerList: ${code.name}, $detail")

        scardReaderList.handler.post {
            callbacks.onReaderListError(scardReaderList, SCardError(code, detail, isFatal))
        }

        /* irrecoverable error --> disconnect */
        if(isFatal) {
            process(ActionEvent.ActionDisconnect())
        }
    }

    internal fun postCardOrReaderError(code : SCardError.ErrorCodes, detail: String, reader: SCardReader) {
        Log.e(TAG, "Error reader or card: ${code.name}, $detail")
        scardReaderList.handler.post {
            callbacks.onReaderOrCardError(reader, SCardError(code, detail))
        }
    }

    protected fun interpretSlotsStatus(data: ByteArray) {

        if(data.isEmpty()) {
            postReaderListError(
                SCardError.ErrorCodes.PROTOCOL_ERROR,
                "Error, interpretSlotsStatus: array is empty")
            return
        }

        val slotCount = data[0]

        /* Is slot count  matching nb of bytes*/
        if (slotCount > 4 * (data.size - 1)) {
            postReaderListError(
                SCardError.ErrorCodes.PROTOCOL_ERROR,
                "Error, too much slot ($slotCount) for ${data.size - 1} bytes")
            return
        }

        /* Is slot count matching nb of readers in scardReaderList obj */
        if(slotCount.toInt() != scardReaderList.readers.size) {
            postReaderListError(
                SCardError.ErrorCodes.PROTOCOL_ERROR,
                "Error, slotCount in frame ($slotCount) does not match slotCount in scardReaderList (${scardReaderList.readers.size})")
            return
        }

        for (i in 1 until data.size) {
            for (j in 0..3) {
                val slotNumber = (i - 1) * 4 + j
                if (slotNumber < slotCount) {

                    val slotStatus = (data[i].toInt() shr j*2) and 0x03
                    Log.i(TAG, "Slot $slotNumber")

                    /* Update SCardReadList slot status */
                    scardReaderList.readers[slotNumber].cardPresent =
                        !(slotStatus == SCardReader.SlotStatus.Absent.code || slotStatus == SCardReader.SlotStatus.Removed.code)

                    /* If card is not present, it can not be powered */
                    if(!scardReaderList.readers[slotNumber].cardPresent) {
                        scardReaderList.readers[slotNumber].cardPowered = false
                    }

                    /* If the card on the slot we used is gone */
                    if(scardReaderList.ccidHandler.currentReaderIndex == slotNumber) {
                        if(!scardReaderList.readers[slotNumber].cardPresent || !scardReaderList.readers[slotNumber].cardPowered) {
                            currentState = State.Idle
                        }
                    }

                    when (slotStatus) {
                        SCardReader.SlotStatus.Absent.code -> Log.i(TAG, "card absent, no change since last notification")
                        SCardReader.SlotStatus.Present.code -> Log.i(TAG, "card present, no change since last notification")
                        SCardReader.SlotStatus.Removed.code  -> Log.i(TAG, "card removed notification")
                        SCardReader.SlotStatus.Inserted.code  -> Log.i(TAG, "card inserted notification")
                        else -> {
                            Log.w(TAG, "Impossible value : $slotStatus")
                        }
                    }
                    val cardChanged = (slotStatus == SCardReader.SlotStatus.Removed.code || slotStatus == SCardReader.SlotStatus.Inserted.code)
                    if(cardChanged) {
                        scardReaderList.handler.post {
                            callbacks.onReaderStatus(
                                scardReaderList.readers[slotNumber],
                                scardReaderList.readers[slotNumber].cardPresent,
                                scardReaderList.readers[slotNumber].cardPowered
                            )
                        }
                    }
                }
            }
        }
    }


    /**
     * Interpret slot status byte and update cardPresent and cardPowered
     *
     * @param slotStatus Byte
     * @param slot SCardReader
     */
    protected fun interpretSlotsStatusInCcidHeader(slotStatus: Byte, slot: SCardReader) {

        val cardStatus = slotStatus.toInt() and 0b00000011

        when (cardStatus) {
            0b00 -> {
                Log.i(TAG, "A Card is present and active (powered ON)")
                slot.cardPresent = true
                slot.cardPowered = true
            }
            0b01 -> {
                Log.i(TAG, "A Card is present and inactive (powered OFF or hardware error)")
                slot.cardPresent = true
                slot.cardPowered = false
            }
            0b10  -> {
                Log.i(TAG, "No card present (slot is empty)")
                slot.cardPresent = false
                slot.cardPowered = false
            }
            0b11  -> {
                Log.i(TAG, "Reserved for future use")
            }
            else -> {
                Log.w(TAG, "Impossible value for cardStatus : $slotStatus")
            }
        }
    }

    /**
     * Interpret slot error byte, and send error callback if necessary
     *
     * @param slotError Byte
     * @param slotStatus Byte
     * @param slot SCardReader
     * @return true if there is no error, false otherwise
     */
    protected fun interpretSlotsErrorInCcidHeader(slotError: Byte, slotStatus: Byte, slot: SCardReader, postScardErrorCb: Boolean = true): Boolean {

        val commandStatus = (slotStatus.toInt() and 0b11000000) shr 6

        when (commandStatus) {
            0b00 -> {
                Log.i(TAG, "Command processed without error")
                return true
            }
            0b01 -> {
                Log.i(TAG, "Command failed (error code is provided in the SlotError field)")
            }
            else -> {
                Log.w(TAG, "Impossible value for commandStatus : $slotStatus")
            }
        }

        Log.e(TAG, "Error in CCID Header: 0x${String.format("%02X", slotError)}")

        var errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
        var detail = ""

        when (slotError) {
            SCardReader.SlotError.CMD_ABORTED.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "The PC has sent an ABORT command"
            }
            SCardReader.SlotError.ICC_MUTE.code -> {
                errorCode = SCardError.ErrorCodes.CARD_MUTE
                detail = "CCID slot error: Time out in Card communication"
            }
            SCardReader.SlotError.ICC_MUTE.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Parity error in Card communication"
            }
            SCardReader.SlotError.XFR_OVERRUN.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Overrun error in Card communication"
            }
            SCardReader.SlotError.HW_ERROR.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Hardware error on Card side (over-current?)"
            }
            SCardReader.SlotError.BAD_ATR_TS.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Invalid ATR format"
            }
            SCardReader.SlotError.BAD_ATR_TCK.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Invalid ATR checksum"
            }
            SCardReader.SlotError.ICC_PROTOCOL_NOT_SUPPORTED.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Card's protocol is not supported"
            }
            SCardReader.SlotError.ICC_CLASS_NOT_SUPPORTED.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Card's power class is not supported"
            }
            SCardReader.SlotError.PROCEDURE_BYTE_CONFLICT.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Error in T=0 protocol"
            }
            SCardReader.SlotError.DEACTIVATED_PROTOCOL.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "Specified protocol is not allowed"
            }
            SCardReader.SlotError.BUSY_WITH_AUTO_SEQUENCE.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "RDR is currently busy activating a Card"
            }
            SCardReader.SlotError.CMD_SLOT_BUSY.code -> {
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "RDR is already running a command)}"
            }
            SCardReader.SlotError.CMD_NOT_SUPPORTED.code -> {
                // TODO CRA do something in springcore fw ??
                return true
            }
            else -> {
                Log.w(TAG, "CCID Error code not handled")
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "CCID slot error: 0x${String.format("%02X", slotError)}"
            }
        }

        if(postScardErrorCb) {
            postCardOrReaderError(errorCode, detail, slot)
        }
        else {
            Log.e(TAG, "Error reader or card: ${errorCode.name}, $detail")
        }

        return false
    }


    protected fun analyseResponse(data: ByteArray) {
        /* Put data in ccid frame */
        val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(data)
        val slot = scardReaderList.readers[ccidResponse.slotNumber.toInt()]

        /* Update slot status (present, powered) */
        interpretSlotsStatusInCcidHeader(ccidResponse.slotStatus, slot)

        /* Check slot error */
        if(!interpretSlotsErrorInCcidHeader(ccidResponse.slotError, ccidResponse.slotStatus, slot)) {
            Log.d(TAG, "Error, do not process CCID packet, returning to Idle state")
            currentState = State.Idle
            return
        }


        currentState = State.Idle
        Log.d(TAG, "Frame complete, length = ${ccidResponse.length}")

        when {
            ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_Escape.value -> when (scardReaderList.ccidHandler.commandSend) {
                CcidCommand.CommandCode.PC_To_RDR_Escape -> scardReaderList.handler.post {
                    callbacks.onControlResponse(
                        scardReaderList,
                        ccidResponse.payload
                    )
                }
                else -> postReaderListError(SCardError.ErrorCodes.DIALOG_ERROR, "Unexpected CCID response (${ccidResponse.code}) for command : ${scardReaderList.ccidHandler.commandSend}")
            }
            ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value -> when (scardReaderList.ccidHandler.commandSend) {
                CcidCommand.CommandCode.PC_To_RDR_XfrBlock -> {
                    if (ccidResponse.slotNumber > scardReaderList.readers.size) {
                        postReaderListError(
                            SCardError.ErrorCodes.PROTOCOL_ERROR,
                            "Error, slot number specified (${ccidResponse.slotNumber}) greater than maximum slot (${scardReaderList.readers.size - 1}), maybe the packet is incorrect"
                        )
                    } else {

                        currentState = State.Idle
                        scardReaderList.handler.post {
                            callbacks.onTransmitResponse(
                                slot.channel,
                                ccidResponse.payload
                            )
                        }
                    }
                }
                CcidCommand.CommandCode.PC_To_RDR_IccPowerOn -> {
                    /* save ATR */
                    slot.channel.atr = ccidResponse.payload
                    /* set cardPowered flag */
                    slot.cardPowered = true
                    /* Change state */
                    currentState = State.Idle
                    /* Call callback */
                    scardReaderList.handler.post { callbacks.onCardConnected(slot.channel) }
                }
                else -> postReaderListError(SCardError.ErrorCodes.DIALOG_ERROR, "Unexpected CCID response (${ccidResponse.code}) for command : ${scardReaderList.ccidHandler.commandSend}")
            }
            ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_SlotStatus.value -> when (scardReaderList.ccidHandler.commandSend) {
                CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus -> {
                    /* Do nothing */
                    Log.d(TAG, "Reader Status --> Cool! ...but useless")

                    /* Update slot concerned */
                    interpretSlotsStatusInCcidHeader(ccidResponse.slotStatus, scardReaderList.readers[ccidResponse.slotNumber.toInt()])
                }
                CcidCommand.CommandCode.PC_To_RDR_IccPowerOff -> {
                    slot.cardPowered = false
                    scardReaderList.handler.post {callbacks.onCardDisconnected(slot.channel)}
                }
                CcidCommand.CommandCode.PC_To_RDR_XfrBlock -> {
                    // TODO CRA scardReaderList.handler.post {callbacks?.onReaderStatus()}
                    //  scardReaderList.handler.post {callbacks?.onCardConnected(channel)}
                }
                CcidCommand.CommandCode.PC_To_RDR_IccPowerOn -> {
                    val channel = slot.channel
                    slot.cardPowered = true
                    scardReaderList.handler.post {callbacks.onCardConnected(channel)}
                    // TODO onReaderOrCardError
                }
                else -> postReaderListError(SCardError.ErrorCodes.DIALOG_ERROR, "Unexpected CCID response (${ccidResponse.code}) for command : ${scardReaderList.ccidHandler.commandSend}")
            }
            else -> postReaderListError(SCardError.ErrorCodes.DIALOG_ERROR, "Unknown CCID response (${ccidResponse.code}) for command : ${scardReaderList.ccidHandler.commandSend}")
        }
    }

    protected fun processNextSlotConnection() {
        /* If there are one card present on one or more slot --> go to state ConnectingToCard */
        if(listReadersToConnect.size > 0) {
            currentState = State.ConnectingToCard
            /* Call explicitly ccidHandler.scardConnect() instead of reader.scardConnect() */
            /* Because if the card is present and powered (in USB) the command will not be send */
            /* In USB the card is auto powered if present and it's not the case in BLE*/
            process(ActionEvent.ActionWriting(scardReaderList.ccidHandler.scardConnect(listReadersToConnect[0].index)))
        }
        /* Otherwise go to idle state */
        else {
            currentState = State.Idle
            scardReaderList.handler.post { callbacks.onReaderListCreated(scardReaderList) }
        }
    }

    protected fun getVersionFromRevString(revString: String) {
        scardReaderList.firmwareVersion = revString
        scardReaderList.firmwareVersionMajor = revString.split("-")[0].split(".")[0].toInt()
        scardReaderList.firmwareVersionMinor = revString.split("-")[0].split(".")[1].toInt()
        scardReaderList.firmwareVersionBuild = revString.split("-")[1].toInt()
    }

}