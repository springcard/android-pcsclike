/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.content.*
import android.os.Handler
import android.os.Looper

/**
 *
 * This class representing a SpringCard device (with possibly more than one slot)
 *
 * @property layerDevice Physical Device
 * @property callbacks SCardReaderListCallback
 * @property TAG (kotlin.String..kotlin.String?)
 * @property commLayer Communication Layer
 * @property ccidHandler CcidHandler
 * @property handler Handler
 * @property vendorName Manufacturer name of the device
 * @property productName Product name of the device
 * @property serialNumber Serial number of the device, expressed in hexadecimal
 * @property serialNumberRaw Serial number of the BLE device
 * @property firmwareVersion Firmware version of the device, in the form “MM.mm-bb-gXXXXX”
 * @property firmwareVersionMajor The MM part of libraryVersion
 * @property firmwareVersionMinor The mm part of libraryVersion
 * @property firmwareVersionBuild The build number (bb part of libraryVersion)
 * @property isConnected True the device is connected to the phone
 * @property isCorrectlyKnown True if the device has previously been correctly connected (and the library as not been unloaded or SCardReaderList.clearCache has not been called)
 * @property readers List of SCardReader
 * @property slots  Name of every slot
 * @property slotCount Number of slots
 * @constructor Instantiate an new SpringCard PC/SC device
 *
 */
abstract class SCardReaderList internal constructor(internal val layerDevice: Any, internal val callbacks: SCardReaderListCallback) {

    private val TAG = this::class.java.simpleName

    internal lateinit var commLayer: CommunicationLayer
    internal var ccidHandler = CcidHandler()
    internal var handler  =  Handler(Looper.getMainLooper())

    var vendorName: String = ""
        internal set
    var productName: String = ""
        internal set
    var serialNumber: String = ""
        internal set
    var serialNumberRaw: ByteArray = byteArrayOf(0)
        internal set
    var firmwareVersion: String = ""
        internal set
    var firmwareVersionMajor = 0
        internal set
    var firmwareVersionMinor = 0
        internal set
    var firmwareVersionBuild = 0
        internal set

    var isConnected = false
        internal set
    var isCorrectlyKnown = false
        internal set

    internal var hardwareVersion: String = ""
    internal var softwareVersion: String = ""
    internal var pnpId: String = ""

    internal var readers: MutableList<SCardReader> = mutableListOf<SCardReader>()

    internal fun process(event: ActionEvent) {
        commLayer.process(event)
    }


    abstract fun create(ctx : Context)
    abstract fun create(ctx : Context, secureConnexionParameters: CcidSecureParameters)



    /**
     * The control function gives you direct control on the reader (even when there’s no card in it).
     * Counterpart to PC/SC’s SCardControl
     * callback success: [SCardReaderListCallback.onControlResponse]
     *
     * @param command control APDU to send to the device
     */
    fun control(command: ByteArray) {
        val ccidCmd = ccidHandler.scardControl(command)
        process(ActionEvent.ActionWriting(ccidCmd))
    }


    /**
     * Retrieve the SCardReader at the index specified.
     * If the index is wrong the callback [SCardReaderListCallback.onReaderListError]
     * will be called
     *
     * @param slotIndex Int
     * @return SCardReader?
     */
    fun getReader(slotIndex: Int): SCardReader?
    {
        if(slotIndex >= readers.size)
        {
            /* error is not fatal --> do not close product */
            commLayer.postReaderListError(SCardError.ErrorCodes.NO_SUCH_SLOT, "Error: slotIndex $slotIndex is greater than number of slot ${readers.size}", false)
            return null
        }

        return readers[slotIndex]
    }

    /**
     * Retrieve the SCardReader associated to the name
     * passed in parameter. If the name doesn't exist,
     * callback [SCardReaderListCallback.onReaderListError]
     * will be called
     *
     * @param slotName name of the slot
     * @return SCardReader?
     */
    fun getReader(slotName: String): SCardReader?
    {
        for(reader in readers)
        {
            if(reader.name == slotName)
            {
                return reader
            }
        }
        /* Error is not fatal --> do not close product */
        commLayer.postReaderListError(SCardError.ErrorCodes.NO_SUCH_SLOT, "Error: No slot with name $slotName found", false)
        return null
    }

    val slots : List<String>
    get() {
        val slotsName = mutableListOf<String>()
        for (slot in readers) {
            slotsName.add(slot.name)
        }
        return slotsName
    }

    val slotCount : Int
        get() {
            return readers.size
        }

    /**
     * Disconnect from the BLE device
     * callback when succeed : [SCardReaderListCallback.onReaderListClosed]
     */
    fun close() {
        process(ActionEvent.ActionDisconnect())
    }


    /**
     * Get battery level and power state from the device.
     * callback: [SCardReaderListCallback.onPowerInfo]
     */
    fun getPowerInfo() {
        process(ActionEvent.ActionReadPowerInfo())
    }
}
