/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscblelib

import android.util.Log
import java.lang.Exception

internal class CcidHandler() {

    private var sequenceNumber = 0
    internal var commandSend = CcidCommand.CommandCode.PC_To_RDR_Escape
        private set

    internal var currentReaderIndex: Int = 0

    internal var isSecure: Boolean = false
        private set
    var authenticateOk: Boolean = false
    internal lateinit var ccidSecure: CcidSecure
        private set

    private val TAG: String
        get() = this::class.java.simpleName

    /* Secondary Constructor */

    constructor(parameters: CcidSecureParameters) : this() {
        isSecure = true
        ccidSecure = CcidSecure(parameters)
    }

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

    /* Build Ccid commands and get Ccid responses*/

    private fun buildCcidCommand(code: CcidCommand.CommandCode, slotNumber: Int, payload: ByteArray): ByteArray {

        if(slotNumber > 255) {
            val msg = "Slot number is too much ($slotNumber)"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        commandSend = code
        currentReaderIndex = slotNumber

        var command = CcidCommand(code, slotNumber.toByte(), sequenceNumber.toByte(), payload)

        /* authenticateOk --> cipher and mmc frame */
        if(isSecure && authenticateOk) {
            command = ccidSecure.encryptCcidBuffer(command)
        }

        return command.raw.toByteArray()
    }

    fun getCcidResponse(frame: ByteArray): CcidResponse {
        var response = CcidResponse(frame)

        if(frame.size < 10) {
            val msg = "Too few data to build a CCID response (${frame.toHexString()})"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        if(frame.size-CcidFrame.HEADER_SIZE != response.length ) {
              Log.d(TAG, "Frame not complete, excepted length = ${response.length}")
        }

        if(isSecure && authenticateOk) {
            response = ccidSecure.decryptCcidBuffer(response)
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

    fun getCcidLength(frame: ByteArray): Int {
        return CcidResponse(frame).length
    }

}