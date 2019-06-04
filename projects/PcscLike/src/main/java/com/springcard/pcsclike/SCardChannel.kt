/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import com.springcard.pcsclike.communication.*

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
     *
     * @throws Exception if the device is sleeping, there is a command already processing, the slot number exceed 255
     */
    fun transmit(command: ByteArray) {
        val ccidCmd = parent.parent.ccidHandler.scardTransmit(parent.index.toByte(), command)
        parent.parent.processAction(Action.Writing(ccidCmd))
    }

    /**
     * Disconnect from the card (close the communication channel + power down)
     *
     * @throws Exception if the device is sleeping, there is a command already processing, the slot number exceed 255
     */
    fun disconnect() {
        val ccidCmd = parent.parent.ccidHandler.scardDisconnect(parent.index.toByte())
        parent.parent.processAction(Action.Writing(ccidCmd))
    }

    /**
     * Counterpart to PC/SC’s SCardReconnect, same as [SCardReader.cardConnect]
     *
     * @throws Exception the device is sleeping, there is a command already processing, the slot number exceed 255
     */
    fun reconnect() {
        parent.cardConnect()
    }
}