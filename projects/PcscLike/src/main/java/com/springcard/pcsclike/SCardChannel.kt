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

        /* Update to new state and lock machine state if necessary */
        parent.parent.machineState.setNewState(State.WritingCmdAndWaitingResp)
        /* Build the frame */
        val ccidCmd = parent.parent.ccidHandler.scardTransmit(parent.index.toByte(), command)

        /* Send the frame */
        parent.parent.commLayer.writeCommand(ccidCmd)

        /* NB: The lock will be exited when the response will arrive */
    }

    /**
     * Disconnect from the card (close the communication channel + power down)
     *
     * @throws Exception if the device is sleeping, there is a command already processing, the slot number exceed 255
     */
    fun disconnect() {
        parent.parent.machineState.setNewState(State.WritingCmdAndWaitingResp)
        val ccidCmd = parent.parent.ccidHandler.scardDisconnect(parent.index.toByte())
        parent.parent.commLayer.writeCommand(ccidCmd)
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