/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import com.springcard.pcsclike.SCardError
import com.springcard.pcsclike.SCardReader
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.ccid.CcidFrame
import com.springcard.pcsclike.ccid.CcidHandler
import com.springcard.pcsclike.utils.*
import java.lang.Exception
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.inv

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class BleLowLevel(private val scardReaderList: SCardReaderList, private val bluetoothDevice: BluetoothDevice): LowLevelLayer {

    private val uuidCharacteristicsToReadPower by lazy {
        mutableListOf<UUID>(
            GattAttributesSpringCore.UUID_BATTERY_POWER_STATE_CHAR,
            GattAttributesSpringCore.UUID_BATTERY_LEVEL_CHAR
        )
    }

    private val uuidCharacteristicsToRead by lazy {
        mutableListOf<UUID>(
            GattAttributesSpringCore.UUID_MODEL_NUMBER_STRING_CHAR,
            GattAttributesSpringCore.UUID_SERIAL_NUMBER_STRING_CHAR,
            GattAttributesSpringCore.UUID_FIRMWARE_REVISION_STRING_CHAR,
            GattAttributesSpringCore.UUID_HARDWARE_REVISION_STRING_CHAR,
            GattAttributesSpringCore.UUID_SOFTWARE_REVISION_STRING_CHAR,
            GattAttributesSpringCore.UUID_MANUFACTURER_NAME_STRING_CHAR,
            GattAttributesSpringCore.UUID_PNP_ID_CHAR,
            GattAttributesSpringCore.UUID_CCID_STATUS_CHAR
        )
    }

    private val uuidCharacteristicsCanIndicate by lazy {
        mutableListOf<UUID>(
            GattAttributesSpringCore.UUID_CCID_STATUS_CHAR,
            GattAttributesSpringCore.UUID_CCID_RDR_TO_PC_CHAR
        )
    }

    private var characteristicsToRead : MutableList<BluetoothGattCharacteristic> = mutableListOf<BluetoothGattCharacteristic>()
    private var characteristicsCanIndicate : MutableList<BluetoothGattCharacteristic> = mutableListOf<BluetoothGattCharacteristic>()
    private var characteristicsToReadPower : MutableList<BluetoothGattCharacteristic> = mutableListOf<BluetoothGattCharacteristic>()
    internal lateinit var charCcidPcToRdr : BluetoothGattCharacteristic
    private lateinit var charCcidStatus : BluetoothGattCharacteristic

    private val TAG = this::class.java.simpleName
    private lateinit var mBluetoothGatt: BluetoothGatt

    private var cptConnectAttempts: Int = 0
    private var indexCharToBeRead: Int = 0
    private var indexCharToBeSubscribed: Int = 0

    /* Write/Read pointers */
    private var txBuffer = mutableListOf<Byte>()
    private var txBufferCursorBegin = 0
    private var txBufferCursorEnd = 0
    private var rxBuffer = mutableListOf<Byte>()


    /* Various callback methods defined by the BLE API */
    private val mGattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                Log.i(TAG, "BLE event (${object{}.javaClass.enclosingMethod!!.name})")
                if (newState == BluetoothProfile.STATE_CONNECTED) {

                    /* Ask to reduce interval timer to 7.5 ms (android 5) or 11.25 ms */
                    /* But the Supervision Timeout will be set to 20s again (in android 9.0 and lower) */
                    /* The device will reduce the supervision timeout itself */
                    mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                    Log.i(TAG, "Attempting to start service discovery")
                    mBluetoothGatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    scardReaderList.commLayer.onDisconnected()
                } else {
                    if (newState == BluetoothProfile.STATE_CONNECTING)
                        Log.i(TAG, "BLE state changed, unhandled STATE_CONNECTING")
                    else if (newState == BluetoothProfile.STATE_DISCONNECTING)
                        Log.i(TAG, "BLE state changed, unhandled STATE_DISCONNECTING")
                }
            }

            override// New services discovered
            fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                Log.i(TAG, "BLE event (${object{}.javaClass.enclosingMethod!!.name})")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.DIALOG_ERROR, "Unable to discover GATT, onServicesDiscovered returned: $status"))
                    return
                }

                val services = mBluetoothGatt.services
                Log.d(TAG, services.toString())

                if(services.isEmpty()) {
                    scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.MISSING_SERVICE, "Android thinks that the GATT of the device is empty"))
                    return
                }

                /* If device is already known, do not read any char except CCID status */
                if(scardReaderList.isCorrectlyKnown) {
                    uuidCharacteristicsToRead.clear()
                    uuidCharacteristicsToRead.add(GattAttributesSpringCore.UUID_CCID_STATUS_CHAR)
                }

                for (srv in services) {
                    Log.d(TAG, "Service = " + srv.uuid.toString())
                    for (chr in srv.characteristics) {
                        Log.d(TAG, "    Characteristic = ${chr.uuid}")

                        if(uuidCharacteristicsCanIndicate.contains(chr.uuid)) {
                            characteristicsCanIndicate.add(chr)
                        }
                        if(uuidCharacteristicsToRead.contains(chr.uuid)){
                            characteristicsToRead.add(chr)
                        }
                        if(uuidCharacteristicsToReadPower.contains(chr.uuid)) {
                            characteristicsToReadPower.add(chr)
                        }
                        if(GattAttributesSpringCore.UUID_CCID_PC_TO_RDR_CHAR == chr.uuid) {
                            charCcidPcToRdr = chr
                        }

                        if((GattAttributesSpringCore.UUID_CCID_STATUS_CHAR == chr.uuid)) {
                            charCcidStatus = chr
                        }
                    }
                }

                if(uuidCharacteristicsCanIndicate.size != characteristicsCanIndicate.size
                    || uuidCharacteristicsToRead.size != characteristicsToRead.size
                    || uuidCharacteristicsToReadPower.size != characteristicsToReadPower.size) {
                    scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.MISSING_CHARACTERISTIC, "One or more characteristic are missing in the GATT"))
                    return
                }

                Log.d(TAG, "Go to ReadingInformation")
                /* Trigger 1st read */
                mBluetoothGatt.readCharacteristic(characteristicsToRead[0])
            }

            override// Result of a characteristic read operation
            fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                Log.i(TAG, "BLE event (${object {}.javaClass.enclosingMethod!!.name})")
                Log.d(TAG, "Read ${characteristic.value.toHexString()} on characteristic ${characteristic.uuid}")
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    scardReaderList.commLayer.onCommunicationError(SCardError(
                        SCardError.ErrorCodes.READ_CHARACTERISTIC_FAILED,
                        "Failed to subscribe to read characteristic $characteristic"
                    ))
                    return
                }

                val error: SCardError

                when (characteristic.uuid) {
                    GattAttributesSpringCore.UUID_MODEL_NUMBER_STRING_CHAR -> scardReaderList.constants.productName =
                        characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_SERIAL_NUMBER_STRING_CHAR -> {
                        scardReaderList.constants.serialNumber = characteristic.value.toString(charset("ASCII"))
                        scardReaderList.constants.serialNumberRaw =
                            characteristic.value.toString(charset("ASCII")).hexStringToByteArray()
                    }
                    GattAttributesSpringCore.UUID_FIRMWARE_REVISION_STRING_CHAR -> scardReaderList.constants.softwareVersion =
                        characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_HARDWARE_REVISION_STRING_CHAR -> scardReaderList.constants.hardwareVersion =
                        characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_SOFTWARE_REVISION_STRING_CHAR -> {
                        try {
                            scardReaderList.constants.setVersionFromRevString(
                                characteristic.value.toString(charset("ASCII"))
                            )
                        }
                        catch (e: Exception) {
                            scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.DUMMY_DEVICE, "Incorrect firmware revision: ${characteristic.value.toString(charset("ASCII"))}"))
                        }
                    }
                    GattAttributesSpringCore.UUID_MANUFACTURER_NAME_STRING_CHAR -> scardReaderList.constants.vendorName =
                        characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_PNP_ID_CHAR -> scardReaderList.constants.pnpId =
                        characteristic.value.toHexString()
                    GattAttributesSpringCore.UUID_CCID_STATUS_CHAR -> {

                        val slotCount = characteristic.value[0] and CcidHandler.LOW_POWER_NOTIFICATION.inv()

                        if (slotCount.toInt() == 0) {
                            scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.DUMMY_DEVICE, "This device has 0 slots"))
                            return
                        }

                        /* Add n new readers */
                        for (i in 0 until slotCount) {
                            scardReaderList.readers.add(SCardReader(scardReaderList))
                        }

                        /* Retrieve readers name */
                        if (scardReaderList.isCorrectlyKnown) {
                            for (i in 0 until slotCount) {
                                scardReaderList.readers[i].name = scardReaderList.constants.slotsName[i]
                                scardReaderList.readers[i].index = i
                            }
                        } else {
                            /* Otherwise set temporary names and add to list */
                            for (i in 0 until slotCount) {
                                scardReaderList.readers[i].name = "Slot $i"
                                scardReaderList.readers[i].index = i
                                scardReaderList.infoToRead.add("58210$i".hexStringToByteArray())
                            }
                        }

                        /* Update readers status */
                        /* Set isNotification = false, because we read the CCID status */
                        val listSlotsUpdated = mutableListOf<SCardReader>()
                        val readCcidStatus = characteristic.value.drop(1).toByteArray()
                        error  = scardReaderList.ccidHandler.interpretCcidStatus(readCcidStatus, listSlotsUpdated, isNotification = false)

                        if(error.code != SCardError.ErrorCodes.NO_ERROR) {
                            scardReaderList.commLayer.onCommunicationError(error)
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unhandled characteristic read : ${characteristic.uuid}")
                    }
                }

                indexCharToBeRead++
                if (indexCharToBeRead < characteristicsToRead.size) {
                    mBluetoothGatt.readCharacteristic(characteristicsToRead[indexCharToBeRead])
                }
                else {
                    Log.d(TAG, "Reading Information finished")
                    /* Trigger 1st subscribing */
                    enableNotifications(characteristicsCanIndicate[0])
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                Log.i(TAG, "BLE event (${object{}.javaClass.enclosingMethod!!.name})")
                if(status != BluetoothGatt.GATT_SUCCESS) {
                    scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.WRITE_CHARACTERISTIC_FAILED, "Failed to write on characteristic $characteristic"))
                    return
                }

                if (!ccidWriteCharSequenced()) {
                    Log.d(TAG, "There is still data to write")
                }
            }

            override// Characteristic notification
            fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                Log.i(TAG, "BLE event (${object{}.javaClass.enclosingMethod!!.name})")
                Log.d(TAG, "Characteristic ${characteristic.uuid} changed, value : ${characteristic.value.toHexString()}")

                when {
                    characteristic.uuid == GattAttributesSpringCore.UUID_CCID_STATUS_CHAR -> {

                        /* Update Device state */
                        val error = interpretFirstByteCcidStatusBle(characteristic.value[0])
                        if(error.code != SCardError.ErrorCodes.NO_ERROR) {
                            scardReaderList.commLayer.onCommunicationError(error)
                            return
                        }

                        /* Update readers status */
                        val readCcidStatus = characteristic.value.drop(1).toByteArray()
                        scardReaderList.commLayer.onStatusReceived(readCcidStatus)
                    }
                    characteristic.uuid == GattAttributesSpringCore.UUID_CCID_RDR_TO_PC_CHAR -> {

                        rxBuffer.addAll(characteristic.value.toList())
                        val ccidLength = scardReaderList.ccidHandler.getCcidLength(rxBuffer.toByteArray())

                        /* Check if the Response is compete or not */
                        if(rxBuffer.size - CcidFrame.HEADER_SIZE != ccidLength) {
                            Log.d(TAG, "Frame not complete, excepted length = $ccidLength")
                        }
                        else {
                            Log.d(TAG, "Write finished")
                            scardReaderList.commLayer.onResponseReceived(rxBuffer.toByteArray())
                            rxBuffer.clear()
                        }
                    }
                    else -> Log.w(TAG, "Notification arrived on wrong characteristic ${characteristic.uuid}")
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.i(TAG, "BLE event (${object{}.javaClass.enclosingMethod!!.name})")
                if(status != BluetoothGatt.GATT_SUCCESS) {
                    scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.ENABLE_CHARACTERISTIC_EVENTS_FAILED, "Failed to subscribe to notification for characteristic ${descriptor.characteristic}"))
                    return
                }

                indexCharToBeSubscribed++
                if (indexCharToBeSubscribed < characteristicsCanIndicate.size) {
                    val chr = characteristicsCanIndicate[indexCharToBeSubscribed]
                    enableNotifications(chr)
                }
                else if(indexCharToBeSubscribed == characteristicsCanIndicate.size) {
                    Log.d(TAG, "Subscribing finished")
                    scardReaderList.commLayer.onCreateFinished()
                }
                else if(indexCharToBeSubscribed > characteristicsCanIndicate.size) {
                    /* We subscribe again on CCID status*/
                    if(descriptor.uuid == charCcidStatus.uuid) {
                        scardReaderList.commLayer.onDeviceState(false)
                    }
                    else {
                        Log.w(TAG, "Useless subscribing (again) on characteristic ${descriptor.uuid }")
                    }

                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                Log.i(TAG, "BLE event (${object{}.javaClass.enclosingMethod!!.name})")
                Log.d(TAG, "MTU size = $mtu")
                super.onMtuChanged(gatt, mtu, status)
            }
        }

    /* Utilities methods */

    override fun connect(ctx: Context) {
        Log.d(TAG, "Connect")
        /* Context is useless and could be set to null */
        /* cf https://stackoverflow.com/questions/56642912/why-android-bluetoothdevice-conenctgatt-require-context-if-it-not-use-it */
        mBluetoothGatt = bluetoothDevice.connectGatt(ctx, false, mGattCallback)
    }

    override fun disconnect() {
        Log.d(TAG, "Disconnect")
        mBluetoothGatt.disconnect()
        Log.i(TAG, "BLE action (${object{}.javaClass.enclosingMethod!!.name})")
    }

    override fun close() {
        Log.d(TAG, "Close")
        mBluetoothGatt.close()
    }


    override fun write(data: List<Byte>) {
        putDataToBeWrittenSequenced(data)
        /* Trigger 1st write */
        ccidWriteCharSequenced()
    }

    private fun putDataToBeWrittenSequenced(data: List<Byte>) {
        txBuffer.clear()
        txBuffer.addAll(data)
        txBufferCursorBegin = 0
        txBufferCursorEnd = 0
    }

    /* return Boolean true if finished */
    private fun ccidWriteCharSequenced(): Boolean {
        /* Temporary workaround: we can not send to much data in one write */
        /* (we can write more than MTU but less than ~512 bytes) */
        val maxSize = 512
        return if(txBufferCursorBegin < txBuffer.size) {
            txBufferCursorEnd =  minOf(txBufferCursorBegin+maxSize, txBuffer.size)
            charCcidPcToRdr.value = txBuffer.toByteArray().sliceArray(txBufferCursorBegin until txBufferCursorEnd)
            /* If the data length is greater than MTU, Android will automatically send multiple packets */
            /* There is no need to split the data ourself  */
            Log.d(TAG, "Writing ${charCcidPcToRdr.value.toHexString()}")
            if(!mBluetoothGatt.writeCharacteristic(charCcidPcToRdr)) {
                scardReaderList.commLayer.onCommunicationError(SCardError(SCardError.ErrorCodes.WRITE_CHARACTERISTIC_FAILED, "Failed to write in characteristic ${charCcidPcToRdr.uuid}"))
                return true
            }

            txBufferCursorBegin = txBufferCursorEnd
            Log.i(TAG, "BLE action (${object{}.javaClass.enclosingMethod!!.name})")
            false
        } else {
            true
        }
    }

    private fun enableNotifications(chr : BluetoothGattCharacteristic) {
        mBluetoothGatt.setCharacteristicNotification(chr, true)
        val descriptor = chr.descriptors[0]
        descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        if (!mBluetoothGatt.writeDescriptor(descriptor)) {
            scardReaderList.commLayer.onCommunicationError(
                SCardError(SCardError.ErrorCodes.ENABLE_CHARACTERISTIC_EVENTS_FAILED,
                    "Failed to write in descriptor, to enable notification on characteristic ${chr.uuid}"))
            return
        }
    }

    fun interpretFirstByteCcidStatusBle(data: Byte): SCardError {

        val slotCount = data and CcidHandler.LOW_POWER_NOTIFICATION.inv()

        if(slotCount.toInt() != scardReaderList.readers.size) {
            return SCardError(
                SCardError.ErrorCodes.PROTOCOL_ERROR,
                "Error, the number of slots in the CCID Status ($slotCount) does not match the number of slots of the device (${scardReaderList.readers.size})"
            )
        }

        /* Is slot count matching nb of readers in scardReaderList obj */
        if (slotCount.toInt() != scardReaderList.readers.size) {
            return SCardError(
                SCardError.ErrorCodes.PROTOCOL_ERROR,
                "Error, slotCount in frame ($slotCount) does not match slotCount in scardReaderList (${scardReaderList.readers.size})"
            )
        }

        /* If msb is set the device is gone to sleep, otherwise it is awake */
        val isSleeping = data and CcidHandler.LOW_POWER_NOTIFICATION == CcidHandler.LOW_POWER_NOTIFICATION

        /* Product waking-up */
        if (scardReaderList.isSleeping && !isSleeping) {
            /* Set var before sending callback */
            scardReaderList.isSleeping = isSleeping
            scardReaderList.commLayer.onDeviceState(isSleeping)
        }
        /* Device going to sleep */
        else if (!scardReaderList.isSleeping && isSleeping) {
            /* Set var before sending callback */
            scardReaderList.isSleeping = isSleeping
            scardReaderList.commLayer.onDeviceState(isSleeping)
        } else if (scardReaderList.isSleeping && isSleeping) {
            Log.i(TAG, "Device is still sleeping...")
        } else if (!scardReaderList.isSleeping && !isSleeping) {
            Log.i(TAG, "Device is still awake...")
        }

        /* Update device state */
        scardReaderList.isSleeping = isSleeping

        return SCardError(SCardError.ErrorCodes.NO_ERROR)
    }

    fun enableNotifOnCcidStatus() {
        enableNotifications(charCcidStatus)
    }
}