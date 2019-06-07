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
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.springcard.pcsclike.ccid.*
import com.springcard.pcsclike.communication.*
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
    internal var ccidHandler = CcidHandler(this)
    internal var callbacksHandler  =  Handler(Looper.getMainLooper())

    internal inner class Constants {
        var vendorName: String = ""
        var productName: String = ""
        var serialNumber: String = ""
        var serialNumberRaw: ByteArray = byteArrayOf(0)
        var firmwareVersion: String = ""
        var firmwareVersionMajor = 0
        var firmwareVersionMinor = 0
        var firmwareVersionBuild = 0

        internal var hardwareVersion: String = ""
        internal var softwareVersion: String = ""
        internal var pnpId: String = ""

        var slotsName = mutableListOf<String>()
    }
    internal var constants = Constants()

    /* Keep old syntax */
    val vendorName: String
        get() { return constants.vendorName }
    val productName: String
        get() { return constants.productName }
    val serialNumber: String
        get() { return constants.serialNumber }
    val serialNumberRaw: ByteArray
        get() { return constants.serialNumberRaw }
    val firmwareVersion: String
        get() { return constants.firmwareVersion }
    val firmwareVersionMajor: Int
        get() { return constants.firmwareVersionMajor }
    val firmwareVersionMinor: Int
        get() { return constants.firmwareVersionMinor }
    val firmwareVersionBuild: Int
        get() { return constants.firmwareVersionBuild }

    internal var hardwareVersion: String = ""
    internal var softwareVersion: String = ""
    internal var pnpId: String = ""

    var isSleeping = false
        internal set
    var isConnected = false
        internal set
    var isCorrectlyKnown = false
        internal set
    internal var isAlreadyCreated = false


    internal var readers: MutableList<SCardReader> = mutableListOf<SCardReader>()

    private val libThread = HandlerThread("LibThread")
    private var libHandler: Handler
    init {
        libThread.start()
        libHandler = Handler(libThread.looper)
    }

    @Volatile private var isLocked : Boolean = false
    private var locker : Object = Object()
    private var idThreadLocking = libThread.id

    private fun lockMachineState() {
        Log.d(TAG, "before lock")
        synchronized(locker) {
            while(isLocked) {
                /* If this thread already hold the lock */
                if(Thread.currentThread().id == idThreadLocking) {
                    throw Exception("Could not process multiples actions from thread: ${Thread.currentThread().name}")
                }
                Log.d(TAG, "waiting...")
                locker.wait()
                Log.d(TAG, "wait done")
            }
            isLocked = true
            idThreadLocking = Thread.currentThread().id
            Log.d(TAG, "after lock")
        }
    }
    private fun unlockMachineState() {
        Log.d(TAG, "before unlock")
        synchronized(locker) {
            if(isLocked) {
                Log.d(TAG, "unlocking...")
                isLocked = false
                try {
                    locker.notifyAll()
                }
                catch(e:Exception)
                {
                    Log.e(TAG, "Could not notifyAll, Exception: ${e.message}")
                }
            }
        }
        Log.d(TAG, "after unlock")
    }

    /* If the lock is already active (internal state like autoconnect to card while creating) , do not try to relock */
    internal fun processAction(action: Action, useLock: Boolean = true) {
        Log.d(TAG, "processAction thread = ${Thread.currentThread().name}")
        if(useLock) {
            lockMachineState()
        }
        libHandler.post {
            commLayer.process(action)
        }
    }

    internal abstract fun create(ctx : Context)
    internal abstract fun create(ctx : Context, secureConnexionParameters: CcidSecureParameters)


    /**
     * The control function gives you direct control on the reader (even when there’s no card in it).
     * Counterpart to PC/SC’s SCardControl
     * callback success: [SCardReaderListCallback.onControlResponse]
     *
     * @param command control APDU to send to the device
     *
     * @throws Exception if the device is sleeping, there is a command already processing, the slot number exceed 255
     */
    fun control(command: ByteArray) {
        val ccidCmd = ccidHandler.scardControl(command)
        processAction(Action.Writing(ccidCmd))
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
     * Disconnect from the device
     * callback when succeed : [SCardReaderListCallback.onReaderListClosed]
     */
    fun close() {
        processAction(Action.Disconnect())
    }


    /**
     * Get battery level and power state from the device.
     * callback: [SCardReaderListCallback.onPowerInfo]
     *
     * @throws Exception if the device is sleeping
     */
    fun getPowerInfo() {
        processAction(Action.ReadPowerInfo())
    }


    /**
     * Post a callback to the main thread if the device is created
     * @param callback () -> Lambda to callback to be called
     * @param forceCallback force callback to be called even if device is not created yet
     */
    internal fun postCallback(callback: () -> Unit, forceCallback: Boolean = false, unlockMachine: Boolean = true) {
        if(isAlreadyCreated || forceCallback) {
            if(unlockMachine) {
                unlockMachineState()
            }
            callbacksHandler.post(Runnable(callback))
        }
        else {
            Log.d(TAG, "Device not created yet, do not post callback")
        }
        mayConnectCard()
    }

    /**
     * Wake-up ScardReaderList, [SCardReaderListCallback.onReaderListState] is called when the reader is exiting from sleep.
     * If the reader is already awake, the callback will not be called.
     */
    fun wakeUp() {
        if(isSleeping) {
            processAction(Action.WakeUp())
        }
        else {
            Log.d(TAG, "Device is not sleeping, waking-up is useless")
        }
    }

    internal fun setKnownConstants(const: Constants) {
        this.isCorrectlyKnown = true
        constants = const
    }

    internal fun mayConnectCard() {
        synchronized(locker) {
            if (!isLocked && !isSleeping && isAlreadyCreated) {
                Log.w(TAG, "Calling processNextSlotConnection()")
                /* Check if there are some cards to connect */
                commLayer.processNextSlotConnection()
            } else {
                Log.w(TAG, "Could not call processNextSlotConnection()")
            }
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
         *
         * @throws UnsupportedOperationException if the SDK version is too low
         * @throws Exception if the device is neither a BluetoothDevice nor a UsbDevice
         * @throws IllegalArgumentException if the device is already connected
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
         *
         * @throws UnsupportedOperationException if the SDK version is too low
         * @throws Exception if the device is neither a BluetoothDevice nor a UsbDevice
         * @throws IllegalArgumentException if the device is already connected
         *
         */
        fun create(ctx: Context, device: Any, callbacks: SCardReaderListCallback, secureConnexionParameters: CcidSecureParameters) {
            val readerList = checkIfDeviceKnown(device, callbacks)
            readerList.create(ctx, secureConnexionParameters)
        }

        /**
         *
         * @param device Any
         * @param callbacks SCardReaderListCallback
         * @return SCardReaderList
         */
        private fun checkIfDeviceKnown(device: Any, callbacks: SCardReaderListCallback): SCardReaderList {
            lateinit var scardReaderList: SCardReaderList

            /* Get device unique id */
            val deviceId = getDeviceUniqueId(device)

            /* Create USB or BLE ScardReaderList  */
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

            /* Set constants previously retrieved */
            if(knownSCardReaderList.containsKey(deviceId)) {
                scardReaderList.setKnownConstants(knownSCardReaderList[deviceId]!!)
            }

            /* Exception if device already connected */
            if(connectedScardReaderList.contains(deviceId)) {
                throw IllegalArgumentException("SCardReaderList with address $deviceId already connected")
            }

            return scardReaderList
        }

        internal var connectedScardReaderList = mutableListOf<String>()
        internal var knownSCardReaderList = mutableMapOf<String, SCardReaderList.Constants>()

        /**
         * Clear list of devices known
         */
        fun clearCache() {
            connectedScardReaderList.clear()
            knownSCardReaderList.clear()
        }

        internal fun getDeviceUniqueId(device: Any): String {
            return when (device) {
                is BluetoothDevice -> {
                    device.address
                }
                is UsbDevice -> {
                    device.deviceId.toString()
                }
                else -> throw Exception("Device is neither a USB neither a BLE peripheral")
            }
        }
    }
}
