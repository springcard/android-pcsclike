/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.bluetooth.BluetoothDevice
import android.content.*
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.springcard.pcsclike.CCID.CcidHandler
import com.springcard.pcsclike.CCID.CcidSecureParameters
import java.lang.Exception

/**
 *
 * This class representing a SpringCard device (with possibly more than one slot)
 *
 * @property layerDevice Physical Device
 * @property callbacks SCardReaderListCallback
 * @property TAG (kotlin.String..kotlin.String?)
 * @property commLayer Communication Layer
 * @property ccidHandler CcidHandler
 * @property callbacksHandler Handler
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
 * @property isSleeping True if the device is gone to sleep
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
    internal var callbacksHandler  =  Handler(Looper.getMainLooper())

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

    var isSleeping = false
        internal set
    var isConnected = false
        internal set
    var isCorrectlyKnown = false
        internal set
    internal var isAlreadyCreated = false

    internal var hardwareVersion: String = ""
    internal var softwareVersion: String = ""
    internal var pnpId: String = ""

    internal var readers: MutableList<SCardReader> = mutableListOf<SCardReader>()

    internal fun process(event: ActionEvent) {
        commLayer.process(event)
    }


    protected abstract fun create(ctx : Context)
    protected abstract fun create(ctx : Context, secureConnexionParameters: CcidSecureParameters)


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


    /**
     * Post a callback to the main thread if the device is created
     * @param callback () -> Lambda to callback to be called
     * @param forceCallback force callback to be called even if device is npot created yet
     */
    internal fun postCallback(callback: () -> Unit, forceCallback: Boolean = false) {
        if(isAlreadyCreated || forceCallback) {
            callbacksHandler.post(Runnable(callback))
        }
        else {
            Log.d(TAG, "Device not created yet, do not post callback")
        }
    }

    companion object {
        /**
         * Instantiate a SpringCard PC/SC product (possibly including one or more reader a.k.a slot)
         * callback when succeed : [SCardReaderListCallback.onReaderListCreated]
         *
         * @param ctx Application's context use to instantiate the object
         * @param device BLE device
         * @param callbacks list of callbacks
         */
        fun create(ctx: Context, device: Any, callbacks: SCardReaderListCallback) {
            val readerList = checkIfDeviceKnown(device, callbacks)
            readerList.create(ctx)
        }

        /**
         * Instantiate a SpringCard PC/SC product (possibly including one or more reader a.k.a slot)
         * callback when succeed : [SCardReaderListCallback.onReaderListCreated]
         * It also creates a secure communication channel based on info given in parameter
         *
         * @param ctx Application's context use to instantiate the object
         * @param device BLE device
         * @param callbacks list of callbacks
         * @param secureConnexionParameters CcidSecureParameters
         */
        fun create(ctx: Context, device: Any, callbacks: SCardReaderListCallback, secureConnexionParameters: CcidSecureParameters) {
            val readerList = checkIfDeviceKnown(device, callbacks)
            readerList.create(ctx, secureConnexionParameters)
        }


        private fun checkIfDeviceKnown(device: Any, callbacks: SCardReaderListCallback): SCardReaderList {
            lateinit var scardReaderList: SCardReaderList

            val address = when (device) {
                is BluetoothDevice -> {
                    (device as BluetoothDevice).address
                }
                is UsbDevice -> {
                    (device as UsbDevice).deviceId.toString()
                }
                else -> throw Exception("Device is neither a USB neither a BLE peripheral")
            }


            if(knownSCardReaderList.containsKey(address) && knownSCardReaderList[address]?.isCorrectlyKnown == true) {
                if (knownSCardReaderList[address]!!.isConnected) {
                    throw IllegalArgumentException("SCardReaderList with address $address already exist")
                } else {
                    scardReaderList = knownSCardReaderList[address]!!
                }
            }
            else {
                when (device) {
                    is BluetoothDevice -> {
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            scardReaderList = SCardReaderListBle(device, callbacks)
                        }
                        else {
                            throw UnsupportedOperationException("BLE not available on Android SDK < ${Build.VERSION_CODES.LOLLIPOP}")
                        }
                    }
                    is UsbDevice -> {
                        scardReaderList = SCardReaderListUsb(device, callbacks)
                    }
                    else -> throw Exception("Device is neither a USB neither a BLE peripheral")
                }
                knownSCardReaderList[address] = scardReaderList
            }
            return scardReaderList
        }

        private var knownSCardReaderList = mutableMapOf<String, SCardReaderList>()

        /**
         * Clear list of devices known
         */
        fun clearCache() {
            knownSCardReaderList.clear()
        }

    }
}
