/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.util.Log

/* This class call the methods provided by SCardReaderListCallback and overwritten by the user */
internal class LoggedSCardReaderListCallback(private var callbacks: SCardReaderListCallback) : SCardReaderListCallback() {

    private val TAG = this::class.java.simpleName

    /* TODO CRA: use it in AOP class*/
    private fun logMethodName(name: String) {
        Log.d(TAG, "<-- $name()")
    }

    /* Methods overwritten */

    override fun onReaderListCreated(readerList: SCardReaderList) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onReaderListCreated(readerList)
    }

    override fun onReaderListClosed(readerList: SCardReaderList?) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onReaderListClosed(readerList)
    }

    override fun onControlResponse(readerList: SCardReaderList, response: ByteArray) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onControlResponse(readerList, response)
    }

    override fun onReaderStatus(slot: SCardReader, cardPresent: Boolean, cardConnected: Boolean) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onReaderStatus(slot, cardPresent, cardConnected)
    }

    override fun onCardConnected(channel: SCardChannel) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onCardConnected(channel)
    }

    override fun onCardDisconnected(channel: SCardChannel) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onCardDisconnected(channel)
    }

    override fun onTransmitResponse(channel: SCardChannel, response: ByteArray) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onTransmitResponse(channel, response)
    }

    override fun onReaderListError(readerList: SCardReaderList?, error: SCardError) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onReaderListError(readerList, error)
    }

    override fun onReaderOrCardError(readerOrCard: Any, error: SCardError) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onReaderOrCardError(readerOrCard, error)
    }

    override fun onReaderListState(readerList: SCardReaderList, isInLowPowerMode: Boolean) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onReaderListState(readerList, isInLowPowerMode)
    }
}


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
    abstract fun onReaderListClosed(readerList: SCardReaderList?)

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
     * @param cardConnected Is the card connected?
     */
    abstract fun onReaderStatus(slot: SCardReader, cardPresent: Boolean, cardConnected: Boolean)

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

    /**
     * Invoked when the device is going to sleep or waking up
     * @param readerList SCardReaderList
     * @param isInLowPowerMode True = going to sleep, False = waking-up
     */
    abstract fun onReaderListState(readerList: SCardReaderList, isInLowPowerMode: Boolean)
}