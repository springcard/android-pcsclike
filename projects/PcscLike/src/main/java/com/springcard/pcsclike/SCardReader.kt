/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import com.springcard.pcsclike.communication.*

/**
 * Represents a slot
 * You can get this object with a call to [SCardReaderList.getReader]
 *
 * @property parent Points to an [SCardReaderList] object
 * @property cardPresent Is the card present in the slot ?
 * @property cardConnected Is a card connected (a ICC PwrOn happened) ?
 * @property cardPowered Is the card powered (by the device) ?
 * @property name Name of the slot
 * @property index Index of the slot in the [SCardReaderList]
 * @property channel Communication channel with a card
 * @constructor
 */
class SCardReader internal  constructor(val parent: SCardReaderList) {


    internal enum class SlotStatus(val code: Int){
        Absent(0),
        Present(1),
        Removed(2),
        Inserted(3),
    }

    /* Warning: This field is valid only if Command Status = 0b01 in the Slot Status field */
    /* cf src\common\hardware\usb\ccid.h */
    internal enum class SlotError(val code: Byte) {
        /* Errors */
        CCID_SUCCESS                              (0x81.toByte()),
        CCID_ERR_UNKNOWN                          (0x82.toByte()),
        CCID_ERR_PARAMETERS                       (0x83.toByte()),
        CCID_ERR_PROTOCOL                         (0x84.toByte()),

        CCID_ERR_CMD_NOT_SUPPORTED                (0x00.toByte()),
        CCID_ERR_BAD_LENGTH                       (0x01.toByte()),
        CCID_ERR_BAD_SLOT                         (0x05.toByte()),
        CCID_ERR_BAD_POWERSELECT                  (0x07.toByte()),
        CCID_ERR_BAD_PROTOCOLNUM                  (0x07.toByte()),
        CCID_ERR_BAD_CLOCKCOMMAND                 (0x07.toByte()),
        CCID_ERR_BAD_ABRFU_3B                     (0x07.toByte()),
        CCID_ERR_BAD_ABRFU_2B                     (0x08.toByte()),
        CCID_ERR_BAD_LEVELPARAMETER               (0x08.toByte()),
        CCID_ERR_BAD_FIDI                         (0x0A.toByte()),
        CCID_ERR_BAD_T01CONVCHECKSUM              (0x0B.toByte()),
        CCID_ERR_BAD_GUARDTIME                    (0x0C.toByte()),
        CCID_ERR_BAD_WAITINGINTEGER               (0x0D.toByte()),
        CCID_ERR_BAD_CLOCKSTOP                    (0x0E.toByte()),
        CCID_ERR_BAD_IFSC                         (0x0F.toByte()),
        CCID_ERR_BAD_NAD                          (0x10.toByte()),

        /* Standard error codes */
        CCID_ERR_CMD_ABORTED                      (0xFF.toByte()),
        CCID_ERR_ICC_MUTE                         (0xFE.toByte()),
        CCID_ERR_XFR_PARITY_ERROR                 (0xFD.toByte()),
        CCID_ERR_XFR_OVERRUN                      (0xFC.toByte()),
        CCID_ERR_HW_ERROR                         (0xFB.toByte()),
        CCID_ERR_BAD_ATR_TS                       (0xF8.toByte()),
        CCID_ERR_BAD_ATR_TCK                      (0xF7.toByte()),
        CCID_ERR_ICC_PROTOCOL_NOT_SUPPORTED       (0xF6.toByte()),
        CCID_ERR_ICC_CLASS_NOT_SUPPORTED          (0xF5.toByte()),
        CCID_ERR_PROCEDURE_BYTE_CONFLICT          (0xF4.toByte()),
        CCID_ERR_DEACTIVATED_PROTOCOL             (0xF3.toByte()),
        CCID_ERR_BUSY_WITH_AUTO_SEQUENCE          (0xF2.toByte()),
        CCID_ERR_PIN_TIMEOUT                      (0xF0.toByte()),
        CCID_ERR_PIN_CANCELLED                    (0xEF.toByte()),
        CCID_ERR_CMD_SLOT_BUSY                    (0xE0.toByte()),

        /* Private error codes for the GemCore library */
        CCID_ERR_CMD_NOT_ABORTED                  (0xC0.toByte()),
        CCID_ERR_CARD_WANTS_RESYNCH               (0xC4.toByte()),
        CCID_ERR_CARD_ABORTED                     (0xC5.toByte()),
        CCID_ERR_CARD_NOT_HEARING                 (0xC6.toByte()),
        CCID_ERR_CARD_IS_LOOPING                  (0xC7.toByte()),
        CCID_ERR_CARD_REMOVED                     (0xC1.toByte()),
        CCID_ERR_CARD_POWERED_DOWN                (0xC2.toByte()),
        CCID_ERR_CARD_PROTOCOL_UNSET              (0xC3.toByte()),
        CCID_ERR_COMM_OVERFLOW                    (0xC8.toByte()),
        CCID_ERR_COMM_FAILED                      (0xC9.toByte()),
        CCID_ERR_COMM_TIMEOUT                     (0xCA.toByte()),
        CCID_ERR_COMM_PROTOCOL                    (0xCB.toByte()),
        CCID_ERR_COMM_FORMAT                      (0xCC.toByte()),

        CCID_STATUS_ICC_MASK                      (0x03.toByte()),
        CCID_STATUS_ICC_PRESENT_ACTIVE            (0x00.toByte()),
        CCID_STATUS_ICC_PRESENT_INACTIVE          (0x01.toByte()),
        CCID_STATUS_ICC_ABSENT                    (0x02.toByte()),

        CCID_STATUS_COMMAND_MASK                  (0xC0.toByte()),
        CCID_STATUS_COMMAND_SUCCESS               (0x00.toByte()),
        CCID_STATUS_COMMAND_FAILED                (0x40.toByte()),
        CCID_STATUS_COMMAND_TIME_EXTENSION        (0x80.toByte())
    }

    var cardPresent = false
        internal set
    var cardConnected = false
        internal set
    var cardPowered = false
        internal set

    internal var cardError = false

    var name: String = ""
        internal set
    var index: Int = 0
        internal set


    val channel: SCardChannel = SCardChannel(this)

    /* Deprecated */
    private fun getStatus() {
        if(parent.isSleeping) {
            parent.postCallback {parent.callbacks.onReaderListError (parent, SCardError(SCardError.ErrorCodes.BUSY, "Error: Device is sleeping"))}
            return
        }
        parent.enterExclusive()
        parent.machineState.setNewState(State.WritingCmdAndWaitingResp)
        val ccidCmd = parent.ccidHandler.scardStatus(index.toByte())
        parent.commLayer.writeCommand(ccidCmd)
    }

    /* Deprecated */
    private fun cardDisconnect() {
        if(parent.isSleeping) {
            parent.postCallback {parent.callbacks.onReaderListError (parent, SCardError(SCardError.ErrorCodes.BUSY, "Error: Device is sleeping"))}
            return
        }

        /* If card not connected */
        if(channel.atr.isEmpty() || !cardConnected)
        {
            parent.postCallback {parent.callbacks.onReaderListError (parent, SCardError(SCardError.ErrorCodes.CARD_ABSENT, "Error: card is not connected"))}
            return
        }

        parent.enterExclusive()
        parent.machineState.setNewState(State.WritingCmdAndWaitingResp)
        val ccidCmd = parent.ccidHandler.scardDisconnect(index.toByte())
        parent.commLayer.writeCommand(ccidCmd)
    }

    /**
     * Connect to the card (power up + open a communication channel with the card)
     *
     * @throws Exception if the device is sleeping, there is a command already processing, the slot number exceed 255
     */
    fun cardConnect() {
        if(channel.atr.isNotEmpty() && cardPresent) {
            cardConnected = true
            parent.postCallback { parent.callbacks.onCardConnected(channel) }
        }
        else {
            if(parent.isSleeping) {
                parent.postCallback {parent.callbacks.onReaderListError (parent, SCardError(SCardError.ErrorCodes.BUSY, "Error: Device is sleeping"))}
                return
            }

            /* If card not present */
            if(channel.atr.isEmpty() || !cardPowered || !cardConnected || !cardPresent)
            {
                parent.postCallback {parent.callbacks.onReaderListError (parent, SCardError(SCardError.ErrorCodes.CARD_ABSENT, "Error: card is not present"))}
                return
            }

            parent.enterExclusive()
            parent.machineState.setNewState(State.WritingCmdAndWaitingResp)
            val ccidCmd = parent.ccidHandler.scardConnect(index.toByte())
            parent.commLayer.writeCommand(ccidCmd)
        }
    }

}