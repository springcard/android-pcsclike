/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

/**
 * This abstract class is used to implement [SCardReaderList] callbacks.
 */
abstract class SCardReaderListCallback {

    /**
     * When the [SCardReaderList.create] methods finished its job, this method is called
     * @param readerList the readerList instantiated
     */
    abstract fun onReaderListCreated(readerList: SCardReaderList)

    /**
     * When a disconnection from the current connected readerList is asked or when the readerList itself close
     * @param readerList SCardReaderList
     */
    abstract fun onReaderListClosed(readerList: SCardReaderList)

    /**
     * When a response is received after a call to [SCardReaderList.control]
     * @param readerList SCardReaderList
     * @param response a byte array if everything went well (empty in case of problem)
     */
    abstract fun onControlResponse(readerList: SCardReaderList, response: ByteArray)


    /**
     * When a card is inserted into, or removed from an active reader
     * @param slot SCardReader
     * @param cardPresent Is the card present?
     * @param cardPowered Is the card powered?
     */
    abstract fun onReaderStatus(slot: SCardReader, cardPresent: Boolean, cardPowered: Boolean)

    /**
     * Used to give the result of a [SCardReader.cardConnect]
     * @param channel SCardChannel
     */
    abstract fun onCardConnected(channel: SCardChannel)

    /**
     * Used when the card is disconnected
     * @param channel SCardChannel
     */
    abstract fun onCardDisconnected(channel: SCardChannel)

    /**
     * When a R-APDU is received after a call to [SCardChannel.transmit]
     * @param channel SCardChannel
     * @param response a byte array if everything went well (empty in case of problem)
     */
    abstract fun onTransmitResponse(channel: SCardChannel, response: ByteArray)

    /**
     * Response to [SCardReaderList.getPowerInfo]
     * @param readerList SCardReaderList
     * @param powerState 0 : unknown, 1 : USB/5V power supply, 2 : on battery
     * @param batteryLevel 0-100%
     */
    abstract fun onPowerInfo(readerList: SCardReaderList, powerState: Int, batteryLevel: Int)

    /* Error callbacks */

    /**
     * Invoked for all readerList-level errors, e.g. BLE error, protocol error, etc. When this callback is invoked, the connection to the readerList is often closed.
     * @param readerList SCardReaderList (could be null if the ScardReaderList has not been created yet)
     * @param error SCardError
     */
    abstract fun onReaderListError(readerList: SCardReaderList?, error: SCardError)

    /**
     * Invoked for all “recoverable” errors, e.g. invalid slot number, card absent, card removed or mute, etc.
     * @param readerOrCard Any
     * @param error SCardError
     */
    abstract fun onReaderOrCardError(readerOrCard: Any, error: SCardError)
}