/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.util.Log


/* This class call the methods provided by SCardReaderListCallback and overwritten by the user */
/* But it also notify all thread waiting for the method to return */
internal class SynchronizedSCardReaderListCallback(private var callbacks: SCardReaderListCallback, private var scardReaderList: SCardReaderList) : SCardReaderListCallback() {

    private val TAG = this::class.java.simpleName

    /* TODO CRA: use it in AOP class*/
    private fun logMethodName(name: String) {
        Log.d(TAG, "<-- $name")
    }

    private fun unlock() {
        scardReaderList.unlockMachineState()
    }

    /* Methods overwritten */

    override fun onReaderListCreated(readerList: SCardReaderList) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onReaderListCreated(readerList)
    }

    override fun onReaderListClosed(readerList: SCardReaderList) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onReaderListClosed(readerList)
    }

    override fun onControlResponse(readerList: SCardReaderList, response: ByteArray) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onControlResponse(readerList, response)
        scardReaderList.mayConnectCard()
    }

    override fun onReaderStatus(slot: SCardReader, cardPresent: Boolean, cardConnected: Boolean) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onReaderStatus(slot, cardPresent, cardConnected)
        scardReaderList.mayConnectCard()
    }

   fun onReaderStatusWithoutUnlock(slot: SCardReader, cardPresent: Boolean, cardConnected: Boolean) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onReaderStatus(slot, cardPresent, cardConnected)
        scardReaderList.mayConnectCard()
    }

    override fun onCardConnected(channel: SCardChannel) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onCardConnected(channel)
        scardReaderList.mayConnectCard()
    }

    override fun onCardDisconnected(channel: SCardChannel) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onCardDisconnected(channel)
        scardReaderList.mayConnectCard()
    }

    override fun onTransmitResponse(channel: SCardChannel, response: ByteArray) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onTransmitResponse(channel, response)
        scardReaderList.mayConnectCard()
    }

    override fun onPowerInfo(readerList: SCardReaderList, powerState: Int, batteryLevel: Int) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onPowerInfo(readerList, powerState, batteryLevel)
        scardReaderList.mayConnectCard()
    }

    override fun onReaderListError(readerList: SCardReaderList?, error: SCardError) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onReaderListError(readerList, error)
        scardReaderList.mayConnectCard()
    }

    override fun onReaderOrCardError(readerOrCard: Any, error: SCardError) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onReaderOrCardError(readerOrCard, error)
        scardReaderList.mayConnectCard()
    }

    override fun onReaderListState(readerList: SCardReaderList, isInLowPowerMode: Boolean) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        unlock()
        callbacks.onReaderListState(readerList, isInLowPowerMode)
        /* Do not try to connect to cards if device is going to sleep */
        if(!isInLowPowerMode) {
            scardReaderList.mayConnectCard()
        }
    }

    fun onReaderListStateWithoutUnlock(readerList: SCardReaderList, isInLowPowerMode: Boolean) {
        logMethodName(object{}.javaClass.enclosingMethod!!.name)
        callbacks.onReaderListState(readerList, isInLowPowerMode)
        /* Do not try to connect to cards if device is going to sleep */
        if(!isInLowPowerMode) {
            scardReaderList.mayConnectCard()
        }
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

    /**
     * Invoked when the device is going to sleep or waking up
     * @param readerList SCardReaderList
     * @param isInLowPowerMode True = going to sleep, False = waking-up
     */
    abstract fun onReaderListState(readerList: SCardReaderList, isInLowPowerMode: Boolean)
}