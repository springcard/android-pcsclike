/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

/**
 * Represents a channel
 * You can get this object with a call to reader.cardConnect()
 *
 * @property parent Points to an [SCardReader] object
 * @property atr Card’s ATR
 *
 */
class SCardChannel internal  constructor(val parent: SCardReader) {

    var atr: ByteArray = ByteArray(0)
        internal set

    /**
     * Transmit a C-APDU to the card, receive the R-APDU in response (in the callback)
     * @param command The C-APDU to send to the card
     */
    fun transmit(command: ByteArray) {
        val ccidCmd = parent.parent.ccidHandler.scardTransmit(parent.index, command)
        parent.parent.process(ActionEvent.ActionWriting(ccidCmd))
    }

    /**
     * Disconnect from the card (close the communication channel + power down)
     */
    fun disconnect() {
        val ccidCmd = parent.parent.ccidHandler.scardDisconnect(parent.index)
        parent.parent.process(ActionEvent.ActionWriting(ccidCmd))
    }

    /**
     * Counterpart to PC/SC’s SCardReconnect, same as [SCardReader.cardConnect]
     */
    fun reconnect() {
        parent.cardConnect()
    }
}