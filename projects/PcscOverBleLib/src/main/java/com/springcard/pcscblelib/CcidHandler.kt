/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscblelib

import android.util.Log
import java.lang.Exception

internal class CcidHandler {

    private var sequenceNumber = 0
    internal var commandSend = CcidCommand.CommandCode.PC_To_RDR_Escape
        private set

    internal var currentReaderIndex: Int = 0

    private val TAG: String
        get() = this::class.java.simpleName

    /* CCID Commands */

    fun scardConnect(slotNumber: Int) : ByteArray {
        return buildCcidCommand(CcidCommand.CommandCode.PC_To_RDR_IccPowerOn, slotNumber, ByteArray(0))
    }

    fun scardDisconnect(slotNumber: Int) : ByteArray {
        return buildCcidCommand(CcidCommand.CommandCode.PC_To_RDR_IccPowerOff, slotNumber, ByteArray(0))
    }

    fun scardStatus(slotNumber: Int) : ByteArray {
        return buildCcidCommand(CcidCommand.CommandCode.PC_To_RDR_GetSlotStatus, slotNumber, ByteArray(0))
    }

    fun scardTransmit(slotNumber: Int, apdu : ByteArray) : ByteArray {
        return buildCcidCommand(CcidCommand.CommandCode.PC_To_RDR_XfrBlock, slotNumber, apdu)
    }

    fun scardControl(apdu : ByteArray) : ByteArray {
        return buildCcidCommand(CcidCommand.CommandCode.PC_To_RDR_Escape,0 , apdu)
    }

    private fun buildCcidCommand(code: CcidCommand.CommandCode, slotNumber: Int, payload: ByteArray): ByteArray {

        if(slotNumber > 255) {
            val msg = "Slot number is too much ($slotNumber)"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        commandSend = code
        currentReaderIndex = slotNumber

        val command = CcidCommand(code, slotNumber.toByte(), sequenceNumber.toByte(), payload)

        return command.raw.toByteArray()
    }

    fun getCcidResponse(frame: ByteArray): CcidResponse {
        val response = CcidResponse(frame)

        if(frame.size < 10) {
            val msg = "Too few data to build a CCID response (${frame.byteArrayToHexString()})"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        currentReaderIndex = response.slotNumber.toInt()

        /* TODO CRA if(sequenceNumber.toByte() != response.sequenceNumber) {
            val msg = "Sequence number in frame (${response.sequenceNumber}) does not match sequence number in cache ($sequenceNumber)"
            Log.e(TAG, msg)
            throw Exception(msg)
        }*/

        sequenceNumber++
        if(sequenceNumber > 255)
            sequenceNumber = 0


        return response
    }
}