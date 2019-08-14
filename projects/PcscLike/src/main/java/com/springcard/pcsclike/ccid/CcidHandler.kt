/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.ccid

import android.util.Log
import com.springcard.pcsclike.SCardError
import com.springcard.pcsclike.SCardReader
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.communication.State
import com.springcard.pcsclike.utils.*
import java.lang.Exception
import kotlin.experimental.and
import kotlin.experimental.inv

internal class CcidHandler(private val scardReaderList: SCardReaderList) {

    private var sequenceNumber: Byte = 0
    internal var commandSend = CcidCommand.CommandCode.PC_To_RDR_Escape
        private set

    internal var currentReaderIndex: Byte = 0

    internal var isSecure: Boolean = false
        private set
    var authenticateOk: Boolean = false
    internal lateinit var ccidSecure: CcidSecure
        private set


    private val TAG: String
        get() = this::class.java.simpleName

    /* Secondary Constructor */

    constructor(scardDevice: SCardReaderList, parameters: CcidSecureParameters) : this(scardDevice) {
        isSecure = true
        ccidSecure = CcidSecure(parameters)
    }

    /* Build Ccid commands */

    fun scardConnect(slotNumber: Byte): CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_IccPowerOn, slotNumber, ByteArray(0))
    }

    fun scardDisconnect(slotNumber: Byte): CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_IccPowerOff, slotNumber, ByteArray(0))
    }

    fun scardStatus(slotNumber: Byte): CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus, slotNumber, ByteArray(0))
    }

    fun scardTransmit(slotNumber: Byte, apdu: ByteArray): CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_XfrBlock, slotNumber, apdu)
    }

    fun scardControl(apdu: ByteArray): CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_Escape, 0, apdu)
    }

    /* Update Ccid commands and get Ccid responses*/

    fun updateCcidCommand(command: CcidCommand): ByteArray {

        var tmp = command
        commandSend = when (tmp.code) {
            CcidCommand.CommandCode.PC_To_RDR_IccPowerOn.value -> CcidCommand.CommandCode.PC_To_RDR_IccPowerOn
            CcidCommand.CommandCode.PC_To_RDR_IccPowerOff.value -> CcidCommand.CommandCode.PC_To_RDR_IccPowerOff
            CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus.value -> CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus
            CcidCommand.CommandCode.PC_To_RDR_Escape.value -> CcidCommand.CommandCode.PC_To_RDR_Escape
            CcidCommand.CommandCode.PC_To_RDR_XfrBlock.value -> CcidCommand.CommandCode.PC_To_RDR_XfrBlock
            else -> throw Exception("Impossible value for CommandCode : 0x${String.format("%02X", tmp.code)})")
        }
        currentReaderIndex = tmp.slotNumber

        tmp.sequenceNumber = sequenceNumber

        /* authenticateOk --> cipher and mac frame */
        if (isSecure && authenticateOk) {
            tmp = ccidSecure.encryptCcidBuffer(tmp)
        }

        return tmp.raw.toByteArray()
    }

    fun getCcidResponse(frame: ByteArray): CcidResponse {

        var response = CcidResponse(frame)

        if (frame.size < 10) {
            val msg = "Too few data to build a CCID response (${frame.toHexString()})"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        if (frame.size - CcidFrame.HEADER_SIZE != response.length) {
            Log.d(TAG, "Frame not complete, excepted length = ${response.length}")
        }

        if (isSecure && authenticateOk) {
            response = ccidSecure.decryptCcidBuffer(response)
        }

        currentReaderIndex = response.slotNumber

        if (sequenceNumber != response.sequenceNumber) {
            val msg =
                "Sequence number in frame (${response.sequenceNumber}) does not match sequence number in cache ($sequenceNumber)"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        sequenceNumber++
        if (sequenceNumber > 255)
            sequenceNumber = 0

        return response
    }

    fun getCcidLength(frame: ByteArray): Int {
        return CcidResponse(frame).length
    }



    fun interpretCcidStatus(data: ByteArray, updatedReaders: MutableList<SCardReader>, isNotification: Boolean = true): SCardError {

        updatedReaders.clear()

        if (data.isEmpty()) {
            return SCardError(
                SCardError.ErrorCodes.PROTOCOL_ERROR,
                "Error, interpretSlotsStatus: array is empty"
            )
        }

        /* Is slot count  matching nb of bytes*/
        if (scardReaderList.readers.size > 4 * data.size) {
            return SCardError(
                SCardError.ErrorCodes.PROTOCOL_ERROR,
                "Error, too much slot (${scardReaderList.readers.size}) for ${data.size - 1} bytes"
            )
        }

        /* If device is sleeping all cards are considered removed */
        if(scardReaderList.isSleeping) {
            Log.i(TAG,"ScardReaderList is sleeping, do not read CCID status data, consider all cards not connected and not powered")
            for(i in 0 until  scardReaderList.readers.size) {
                scardReaderList.readers[i].cardPowered = false
                scardReaderList.readers[i].cardConnected = false
                scardReaderList.readers[i].channel.atr = ByteArray(0)
            }
            SCardError(SCardError.ErrorCodes.NO_ERROR)
        }

        for (i in 0 until data.size) {
            for (j in 0..3) {
                val slotNumber = i * 4 + j
                if (slotNumber < scardReaderList.readers.size) {

                    val slotStatus = (data[i].toInt() shr j * 2) and 0x03
                    Log.i(TAG, "Slot $slotNumber")

                    val slot = scardReaderList.readers[slotNumber]

                    /* Update SCardReadList slot status */
                    slot.cardPresent =
                        !(slotStatus == SCardReader.SlotStatus.Absent.code || slotStatus == SCardReader.SlotStatus.Removed.code)

                    /* If card is not present, it can not be powered */
                    if (!slot.cardPresent) {
                        slot.cardConnected = false
                        slot.channel.atr = ByteArray(0)
                    }

                    when (slotStatus) {
                        SCardReader.SlotStatus.Absent.code -> Log.i(
                            TAG,
                            "card absent, no change since last notification"
                        )
                        SCardReader.SlotStatus.Present.code -> Log.i(
                            TAG,
                            "card present, no change since last notification"
                        )
                        SCardReader.SlotStatus.Removed.code -> Log.i(TAG, "card removed notification")
                        SCardReader.SlotStatus.Inserted.code -> Log.i(TAG, "card inserted notification")
                        else -> {
                            Log.w(TAG, "Impossible value : $slotStatus")
                        }
                    }

                    /* Card considered remove if we read the CCID status and card is absent */
                    val cardRemoved =
                        (slotStatus == SCardReader.SlotStatus.Removed.code) || (!isNotification && !slot.cardPresent)
                    /* Card considered inserted if we read the CCID status and card is present */
                    val cardInserted =
                        (slotStatus == SCardReader.SlotStatus.Inserted.code) || (!isNotification && slot.cardPresent)

                    /* Update list of slots to connect (if there is no card error) */
                    if (cardRemoved && scardReaderList.slotsToConnect.contains(slot)) {
                        Log.d(TAG, "Card gone on slot ${slot.index}, removing slot from listReadersToConnect")
                        scardReaderList.slotsToConnect.remove(slot)
                    } else if (cardInserted && slot.channel.atr.isEmpty() && !scardReaderList.slotsToConnect.contains(
                            slot
                        ) /*&& !slot.cardError*/) { // TODO CRA sse if cardError is useful
                        Log.d(TAG, "Card arrived on slot ${slot.index}, adding slot to listReadersToConnect")
                        scardReaderList.slotsToConnect.add(slot)
                    }

                    /* Send callback only if card removed, when the card is inserted */
                    /* the callback will be send after the connection to the card  */
                    if (cardRemoved) {
                        /* Reset cardError flag */
                        slot.cardError = false
                        /* Add slot to the list of the ones updated  */
                        updatedReaders.add(slot)
                    }

                    /* This line may be optional since slot is a reference on scardReaderList.readers[slotNumber] and not a copy */
                    scardReaderList.readers[slotNumber] = slot
                }
            }
        }
        return SCardError(SCardError.ErrorCodes.NO_ERROR)
    }

    /**
     * Interpret slot status byte and update cardPresent and cardPowered
     *
     * @param slotStatus Byte
     * @param slot SCardReader
     */
    fun interpretSlotsStatusInCcidHeader(slotStatus: Byte, slot: SCardReader) {

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
     * @return null if there is no error, [SCardError] otherwise
     */
    fun interpretSlotsErrorInCcidHeader(slotError: Byte, slotStatus: Byte, slot: SCardReader): SCardError {

        val commandStatus = (slotStatus.toInt() and 0b11000000) shr 6

        when (commandStatus) {
            0b00 -> {
                Log.i(TAG, "Command processed without error")
                return SCardError(SCardError.ErrorCodes.NO_ERROR)
            }
            0b01 -> {
                Log.i(TAG, "Command failed (error code is provided in the SlotError field)")
            }
            else -> {
                Log.w(TAG, "Impossible value for commandStatus : $slotStatus")
            }
        }

        Log.e(TAG, "Error in CCID Header: 0x${String.format("%02X", slotError)}")

        var errorCode = SCardError.ErrorCodes.NO_ERROR
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
            SCardReader.SlotError.XFR_PARITY_ERROR.code -> {
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
                return SCardError(SCardError.ErrorCodes.NO_ERROR)
            }
            else -> {
                Log.w(TAG, "CCID Error code not handled")
                errorCode = SCardError.ErrorCodes.CARD_COMMUNICATION_ERROR
                detail = "CCID slot error: 0x${String.format("%02X", slotError)}"
            }
        }

        slot.cardError = true

        return SCardError(errorCode, detail)
    }

    companion object {
        const val LOW_POWER_NOTIFICATION: Byte = 0x80.toByte()
    }
}