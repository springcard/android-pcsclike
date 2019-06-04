/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.ccid

import android.util.Log
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.utils.*
import java.lang.Exception

internal class CcidHandler(private val scardDevice: SCardReaderList) {

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

    fun scardConnect(slotNumber: Byte) : CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_IccPowerOn, slotNumber, ByteArray(0))
    }

    fun scardDisconnect(slotNumber: Byte) : CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_IccPowerOff, slotNumber, ByteArray(0))
    }

    fun scardStatus(slotNumber: Byte) : CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus, slotNumber, ByteArray(0))
    }

    fun scardTransmit(slotNumber: Byte, apdu : ByteArray) : CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_XfrBlock, slotNumber, apdu)
    }

    fun scardControl(apdu : ByteArray) : CcidCommand {
        return CcidCommand(CcidCommand.CommandCode.PC_To_RDR_Escape,0 , apdu)
    }

    /* Update Ccid commands and get Ccid responses*/

    fun updateCcidCommand(command: CcidCommand) : ByteArray {

        var tmp = command
        commandSend = when(tmp.code) {
            CcidCommand.CommandCode.PC_To_RDR_IccPowerOn.value ->  CcidCommand.CommandCode.PC_To_RDR_IccPowerOn
            CcidCommand.CommandCode.PC_To_RDR_IccPowerOff.value ->  CcidCommand.CommandCode.PC_To_RDR_IccPowerOff
            CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus.value ->  CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus
            CcidCommand.CommandCode.PC_To_RDR_Escape.value ->  CcidCommand.CommandCode.PC_To_RDR_Escape
            CcidCommand.CommandCode.PC_To_RDR_XfrBlock.value ->  CcidCommand.CommandCode.PC_To_RDR_XfrBlock
            else -> throw Exception("Impossible value for CommandCode : 0x${String.format("%02X", tmp.code)})")
        }
        currentReaderIndex = tmp.slotNumber

        tmp.sequenceNumber = sequenceNumber

        /* authenticateOk --> cipher and mac frame */
        if(isSecure && authenticateOk) {
            tmp = ccidSecure.encryptCcidBuffer(tmp)
        }

        return tmp.raw.toByteArray()
    }

    fun getCcidResponse(frame: ByteArray): CcidResponse {

        var response = CcidResponse(frame)

        if(frame.size < 10) {
            val msg = "Too few data to build a CCID response (${frame.toHexString()})"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        if(frame.size - CcidFrame.HEADER_SIZE != response.length ) {
              Log.d(TAG, "Frame not complete, excepted length = ${response.length}")
        }

        if(isSecure && authenticateOk) {
            response = ccidSecure.decryptCcidBuffer(response)
        }

        currentReaderIndex = response.slotNumber

        if(sequenceNumber != response.sequenceNumber) {
            val msg = "Sequence number in frame (${response.sequenceNumber}) does not match sequence number in cache ($sequenceNumber)"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        sequenceNumber++
        if(sequenceNumber > 255)
        sequenceNumber = 0

        return response
    }

    fun getCcidLength(frame: ByteArray): Int {
        return CcidResponse(frame).length
    }

}