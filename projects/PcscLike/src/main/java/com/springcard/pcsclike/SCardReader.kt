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

    // Warning: This field is valid only if Command Status = 0b01 in the Slot Status field
    internal enum class SlotError(val code: Byte) {
        CMD_ABORTED(0xFF.toByte()),
        ICC_MUTE(0xFE.toByte()),
        XFR_PARITY_ERROR(0xFD.toByte()),
        XFR_OVERRUN(0xFC.toByte()),
        HW_ERROR(0xFB.toByte()),
        BAD_ATR_TS(0xF8.toByte()),
        BAD_ATR_TCK(0xF7.toByte()),
        ICC_PROTOCOL_NOT_SUPPORTED(0xF6.toByte()),
        ICC_CLASS_NOT_SUPPORTED(0xF5.toByte()),
        PROCEDURE_BYTE_CONFLICT(0xF4.toByte()),
        DEACTIVATED_PROTOCOL(0xF3.toByte()),
        BUSY_WITH_AUTO_SEQUENCE(0xF2.toByte()),
        CMD_SLOT_BUSY(0xE0.toByte()),
        CMD_NOT_SUPPORTED(0x00.toByte()),
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
        parent.process(ActionEvent.ActionWriting(parent.ccidHandler.scardStatus(index)))
    }

    /* Deprecated */
    private fun cardDisconnect() {
        parent.process(ActionEvent.ActionWriting(parent.ccidHandler.scardDisconnect(index)))
    }

    /**
     * Connect to the card (power up + open a communication channel with the card)
     */
    fun cardConnect() {
        if(channel.atr.isNotEmpty() && cardPresent) {
            cardConnected = true
            parent.postCallback({ parent.callbacks.onCardConnected(channel) })
        }
        else {
            parent.process(ActionEvent.ActionWriting(parent.ccidHandler.scardConnect(index)))
        }
    }

}