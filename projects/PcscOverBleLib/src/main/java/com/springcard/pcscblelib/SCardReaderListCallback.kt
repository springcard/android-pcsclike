/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscblelib

/**
 * This abstract class is used to implement [SCardReaderList] callbacks.
 */
abstract class SCardReaderListCallback {

    /**
     * When the BLE connexion is established, this callback is called
     * @param device SCardReaderList
     */
    abstract fun onConnect(device: SCardReaderList)

    /**
     * When the [SCardReaderList.create] methods finished its job, this method is called
     * @param device the device instantiated
     */
    abstract fun onReaderListCreated(device: SCardReaderList)

    /**
     * When a disconnection from the current connected device is asked or when the device itself disconnect
     * @param device SCardReaderList
     */
    abstract fun onReaderListClosed(device: SCardReaderList)

    /**
     * When a response is received after a call to [SCardReaderList.control]
     * @param device SCardReaderList
     * @param response a byte array if everything went well (empty in case of problem)
     */
    abstract fun onControlResponse(device: SCardReaderList, response: ByteArray)


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
     * @param device SCardReaderList
     * @param powerState 0 : unknown, 1 : external power supply, 2 : on battery
     * @param batteryLevel 0-100%
     */
    abstract fun onPowerInfo(device: SCardReaderList, powerState: Int, batteryLevel: Int)

    /* Error callbacks */

    /**
     * Invoked for all device-level errors, e.g. BLE error, protocol error, etc. When this callback is invoked, the connection to the device is often closed.
     * @param device SCardReaderList
     * @param error SCardError
     */
    abstract fun onReaderListError(device: SCardReaderList, error: SCardError)

    /**
     * Invoked for all “recoverable” errors, e.g. invalid slot number, card absent, card removed or mute, etc.
     * @param readerOrCard Any
     * @param error SCardError
     */
    abstract fun onReaderOrCardError(readerOrCard: Any, error: SCardError)
}