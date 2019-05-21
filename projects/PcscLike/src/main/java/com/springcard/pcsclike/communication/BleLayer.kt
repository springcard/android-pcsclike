/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import android.bluetooth.*
import android.os.Build
import android.support.annotation.RequiresApi
import android.util.Log
import kotlin.experimental.and
import android.bluetooth.BluetoothDevice
import com.springcard.pcsclike.*
import com.springcard.pcsclike.ccid.*
import com.springcard.pcsclike.utils.*
import java.lang.Exception
import java.util.*
import kotlin.experimental.inv


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class BleLayer(internal var bluetoothDevice: BluetoothDevice, callbacks: SCardReaderListCallback, private var scardReaderList : SCardReaderList): CommunicationLayer(callbacks, scardReaderList) {

    private val TAG = this::class.java.simpleName
    private val lowLayer: BleLowLevel =
        BleLowLevel(this)


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

    private class Response {
        var ackReceived = false
        var notifyReceived = false
        val isResponseComplete: Boolean
            get() { return ackReceived and notifyReceived }
        fun resetReceivedFlags()
        {
            ackReceived = false
            notifyReceived = false
        }

        var rxBuffer = mutableListOf<Byte>()
    }
    private var response: Response = Response()


    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Fail fast in case somebody ignored the @RequiresApi annotation
            throw UnsupportedOperationException("BLE not available on Android SDK < ${Build.VERSION_CODES.LOLLIPOP}")
        }
    }

    private fun beginWriteCommand(value: ByteArray) {

        /* WARNING: THIS FUNCTION MUST NOT MODIFY CURRENT STATE */

        /* reset Ack and Notify Response flags */
        response.resetReceivedFlags()

        /* Reset Response Buffer */
        response.rxBuffer.clear()

        /* Clear and set data to write */
        lowLayer.putDataToBeWrittenSequenced(value.toList())

        /* Trigger 1st write operation */
        lowLayer.ccidWriteCharSequenced()
    }


    /* State machine */

    override fun process(event: ActionEvent) {

        Log.d(TAG, "Current state = ${currentState.name}")
        // Memo CRA : SCardDevice instance = 0x${System.identityHashCode(scardReaderList).toString(16).toUpperCase()}

        when (currentState) {
            State.Disconnected -> handleStateDisconnected(event)
            State.Connecting -> handleStateConnecting(event)
            State.DiscoveringGatt -> handleStateDiscovering(event)
            State.ReadingInformation -> handleStateReadingInformation(event)
            State.SubscribingNotifications -> handleStateSubscribingNotifications(event)
            State.ReadingSlotsName ->  handleStateReadingSlotsName(event)
            State.Authenticate -> handleStateAuthenticate(event)
            State.ConnectingToCard -> handleStateConnectingToCard(event)
            State.Idle ->  handleStateIdle(event)
            State.Sleeping -> handleStateSleeping(event)
            State.ReadingPowerInfo -> handleStateReadingPowerInfo(event)
            State.WritingCmdAndWaitingResp -> handleStateWritingCmdAndWaitingResp(event)
            State.Disconnecting ->  handleStateDisconnecting(event)
            else -> Log.w(TAG,"Unhandled State : $currentState")
        }
    }

    private fun handleStateDisconnected(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionCreate -> {
                currentState = State.Connecting
                /* save context if we need to try to reconnect */
                context = event.ctx
                lowLayer.connect()
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private var cptConnectAttempts = 0
    private fun handleStateConnecting(event: ActionEvent) {
        when (event) {
            is ActionEvent.EventConnected -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
                currentState = State.DiscoveringGatt

                scardReaderList.isConnected = true

                lowLayer.discoverGatt()
                Log.i(TAG, "Attempting to start service discovery")
            }
            is ActionEvent.EventDisconnected -> {
                cptConnectAttempts++
                if(cptConnectAttempts < 3) {
                    /* Retry connecting */
                    currentState = State.Disconnected
                    process(ActionEvent.ActionCreate(context))
                }
                else {
                    cptConnectAttempts = 0
                    handleCommonActionEvents(event)
                }
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateDiscovering(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventServicesDiscovered -> {
                if (event.status == BluetoothGatt.GATT_SUCCESS) {

                    /* If device is already known, do not read any char except CCID status */
                    if(scardReaderList.isCorrectlyKnown) {
                        uuidCharacteristicsToRead.clear()
                        uuidCharacteristicsToRead.add(GattAttributesSpringCore.UUID_CCID_STATUS_CHAR)
                    }

                    val services =  lowLayer.getServices()
                    Log.d(TAG, services.toString())

                    if(services.isEmpty()) {
                        postReaderListError(SCardError.ErrorCodes.MISSING_SERVICE, "Android thinks that the GATT of the device is empty")
                        return
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
                        postReaderListError(SCardError.ErrorCodes.MISSING_CHARACTERISTIC, "One or more characteristic are missing in the GATT")
                        return
                    }

                    Log.d(TAG, "Go to ReadingInformation")
                    currentState = State.ReadingInformation
                    /* trigger 1st read */
                    val chr = characteristicsToRead[0]
                    lowLayer.readCharacteristic(chr)

                } else {
                    Log.w(TAG, "onServicesDiscovered received: ${event.status}")
                }
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private var indexCharToBeRead: Int = 0
    private fun handleStateReadingInformation(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventCharacteristicRead -> {
                if(event.status != BluetoothGatt.GATT_SUCCESS) {
                    postReaderListError(SCardError.ErrorCodes.READ_CHARACTERISTIC_FAILED, "Failed to subscribe to read characteristic ${event.characteristic}")
                    return
                }

                when(event.characteristic.uuid) {
                    GattAttributesSpringCore.UUID_MODEL_NUMBER_STRING_CHAR -> scardReaderList.constants.productName = event.characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_SERIAL_NUMBER_STRING_CHAR -> {
                        scardReaderList.constants.serialNumber = event.characteristic.value.toString(charset("ASCII"))
                        scardReaderList.constants.serialNumberRaw = event.characteristic.value.toString(charset("ASCII")).hexStringToByteArray()
                    }
                    GattAttributesSpringCore.UUID_FIRMWARE_REVISION_STRING_CHAR -> scardReaderList.constants.softwareVersion = event.characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_HARDWARE_REVISION_STRING_CHAR -> scardReaderList.constants.hardwareVersion = event.characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_SOFTWARE_REVISION_STRING_CHAR -> getVersionFromRevString(event.characteristic.value.toString(charset("ASCII")))
                    GattAttributesSpringCore.UUID_MANUFACTURER_NAME_STRING_CHAR -> scardReaderList.constants.vendorName = event.characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_PNP_ID_CHAR -> scardReaderList.constants.pnpId = event.characteristic.value.toHexString()
                    GattAttributesSpringCore.UUID_CCID_STATUS_CHAR -> {
                        val slotCount = event.characteristic.value[0] and LOW_POWER_NOTIFICATION.inv()

                        if(slotCount.toInt() == 0) {
                            postReaderListError(SCardError.ErrorCodes.DUMMY_DEVICE, "This device has 0 slots")
                            return
                        }

                        /* Add n new readers */
                        for (i in 0 until slotCount) {
                            scardReaderList.readers.add(SCardReader(scardReaderList))
                        }

                        /* Retrieve readers name */
                        if(scardReaderList.isCorrectlyKnown) {
                            for (i in 0 until slotCount) {
                                scardReaderList.readers[i].name = scardReaderList.constants.slotsName[i]
                                scardReaderList.readers[i].index = i
                            }
                        }
                        else {
                            /* Otherwise set temporary names */
                            for (i in 0 until slotCount) {
                                scardReaderList.readers[i].name =  "Slot $i"
                                scardReaderList.readers[i].index = i
                            }
                        }

                        /* Recreate dummy data with just slotCount and card absent on all slots */
                        val ccidStatusData = ByteArray(event.characteristic.value.size)
                        ccidStatusData[0] = event.characteristic.value[0]
                        for (i in 1 until event.characteristic.value.size) {
                            ccidStatusData[i] = 0x00
                        }

                        listReadersToConnect.clear()

                        /* Update readers status */
                        interpretSlotsStatus(event.characteristic.value)
                    }
                    else -> {
                        Log.w(TAG, "Unhandled characteristic read : ${event.characteristic.uuid}")
                    }
                }

                indexCharToBeRead++
                if (indexCharToBeRead < characteristicsToRead.size) {
                    val chr = characteristicsToRead[indexCharToBeRead]
                    lowLayer.readCharacteristic(chr)
                }
                else {
                    Log.d(TAG, "Reading Information finished")
                    currentState = State.SubscribingNotifications
                    // Trigger 1st subscribing
                    val chr = characteristicsCanIndicate[0]
                    lowLayer.enableNotifications(chr)
                }
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private var indexCharToBeSubscribed: Int = 0
    private fun handleStateSubscribingNotifications(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventDescriptorWritten -> {
                if(event.status != BluetoothGatt.GATT_SUCCESS) {
                    postReaderListError(SCardError.ErrorCodes.ENABLE_CHARACTERISTIC_EVENTS_FAILED, "Failed to subscribe to notification for characteristic ${event.descriptor.characteristic}")
                    return
                }

                indexCharToBeSubscribed++
                if (indexCharToBeSubscribed < characteristicsCanIndicate.size) {
                    val chr = characteristicsCanIndicate[indexCharToBeSubscribed]
                    lowLayer.enableNotifications(chr)
                }
                else {
                    Log.d(TAG, "Subscribing finished")

                    if(scardReaderList.isCorrectlyKnown) {
                        Log.d(TAG, "Device already known: go to processNextSlotConnection or authenticate")
                        /* Go to authenticate state if necessary */
                        if(scardReaderList.ccidHandler.isSecure) {
                            currentState = State.Authenticate
                            process(ActionEvent.ActionAuthenticate())
                        }
                        else {
                            /* If there are one card present on one or more slot, go to state ConnectingToCard */
                            /* Otherwise we post the onReaderListCreated() callback */
                            mayPostReaderListCreated()
                        }
                    }
                    else {
                        Log.d(TAG, "Device unknown: go to ReadingSlotsName")
                        currentState = State.ReadingSlotsName
                        beginWriteCommand(scardReaderList.ccidHandler.scardControl("582100".hexStringToByteArray()))
                    }
                }
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateReadingSlotsName(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventCharacteristicChanged,
            is ActionEvent.EventCharacteristicWritten -> handleResponseNotifyAndAck(event)
            else -> handleCommonActionEvents(event)
        }
    }

    private var authenticateStep = 0
    private fun handleStateAuthenticate(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionAuthenticate -> {
                beginWriteCommand(scardReaderList.ccidHandler.scardControl(scardReaderList.ccidHandler.ccidSecure.hostAuthCmd()))
                authenticateStep = 1
            }
            is ActionEvent.EventCharacteristicChanged,
            is ActionEvent.EventCharacteristicWritten -> handleResponseNotifyAndAck(event)
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateConnectingToCard(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionWriting -> beginWriteCommand(event.command)
            is ActionEvent.EventCharacteristicChanged,
            is ActionEvent.EventCharacteristicWritten -> handleResponseNotifyAndAck(event)
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateIdle(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionWriting -> {
                currentState = State.WritingCmdAndWaitingResp

                beginWriteCommand(event.command)
            }
            is ActionEvent.ActionReadPowerInfo -> {
                currentState = State.ReadingPowerInfo
                process(event)
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateSleeping(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventCharacteristicChanged -> {
                /* Set var before sending callback */
                scardReaderList.isSleeping = false
                currentState = State.Idle
                /* Send callback when device is waking-up */
                scardReaderList.postCallback({ callbacks.onReaderListState(scardReaderList, scardReaderList.isSleeping) })

                handleCommonActionEvents(event)
            }
            is ActionEvent.EventCharacteristicWritten,
            is ActionEvent.EventDescriptorWritten -> {
                /* Set var before sending callback */
                scardReaderList.isSleeping = false
                currentState = State.Idle

                /* Send callback when device is waking-up */
                scardReaderList.postCallback({ callbacks.onReaderListState(scardReaderList, scardReaderList.isSleeping) })

            }
            is ActionEvent.ActionWakeUp -> {
                /* Subscribe to Service changed to wake-up device */
                lowLayer.enableNotifications(charCcidStatus)
            }
            is ActionEvent.ActionDisconnect -> {
                currentState = State.Disconnecting
                lowLayer.disconnect()
                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))
            }
            is ActionEvent.EventDisconnected -> {
                currentState = State.Disconnected
                scardReaderList.isConnected = false
                scardReaderList.isAlreadyCreated = false
                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))

                scardReaderList.postCallback({ callbacks.onReaderListClosed(scardReaderList) })

                // Reset all lists
                indexCharToBeSubscribed = 0
                indexCharToBeRead = 0
                indexSlots = 0

                lowLayer.close()
            }
            else -> throw Exception("Forbidden to do anything when the device is sleeping (apart from waking-up)")
        }
    }

    private var indexCharToReadPower = 0
    private var batteryLevel = 0
    private var powerState = 0
    private fun handleStateReadingPowerInfo(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventCharacteristicRead -> {

                when(event.characteristic.uuid) {
                    GattAttributesSpringCore.UUID_BATTERY_LEVEL_CHAR -> batteryLevel = event.characteristic.value[0].toInt()
                    GattAttributesSpringCore.UUID_BATTERY_POWER_STATE_CHAR -> {
                        /* cf https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.battery_power_state.xml*/
                        /* Check Charging (Chargeable) state */
                        val charging = 0b00110000.toByte()
                        powerState = if(event.characteristic.value[0] and charging == charging) {
                            1
                        } else {
                            2
                        }
                    }
                }

                /* Read next */
                indexCharToReadPower++
                if(indexCharToReadPower<characteristicsToReadPower.size) {
                    val chr = characteristicsToReadPower[indexCharToReadPower]
                    lowLayer.readCharacteristic(chr)
                }
                else {
                    currentState = State.Idle

                    /* read done --> send callback */
                    scardReaderList.postCallback({
                        callbacks.onPowerInfo(
                            scardReaderList,
                            powerState,
                            batteryLevel
                        )
                    })
                }
            }
            is ActionEvent.ActionReadPowerInfo -> {
                /* Trigger 1st read */
                indexCharToReadPower = 0
                val chr = characteristicsToReadPower[indexCharToReadPower]
                lowLayer.readCharacteristic(chr)
            }
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateWritingCmdAndWaitingResp(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventCharacteristicWritten,
            is ActionEvent.EventCharacteristicChanged -> handleResponseNotifyAndAck(event)
            else -> handleCommonActionEvents(event)
        }
    }

    private fun handleStateDisconnecting(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventDisconnected -> {
                scardReaderList.isConnected = false
                currentState = State.Disconnected
                scardReaderList.isAlreadyCreated = false
                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))

                scardReaderList.postCallback({ callbacks.onReaderListClosed(scardReaderList) })

                // Reset all lists
                indexCharToBeSubscribed = 0
                indexCharToBeRead = 0
                indexSlots = 0

                lowLayer.close()
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleCommonActionEvents(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName} (Common)")
        when (event) {
            is ActionEvent.ActionDisconnect -> {
                currentState = State.Disconnecting
                lowLayer.disconnect()
                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))
            }
            is ActionEvent.EventDisconnected -> {
                val deviceNotCreated = !scardReaderList.isAlreadyCreated

                currentState = State.Disconnected
                scardReaderList.isConnected = false
                scardReaderList.isAlreadyCreated = false
                SCardReaderList.connectedScardReaderList.remove(SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice))

                /* Even if device is not here Android tell us that the device is here */
                if(deviceNotCreated)
                {
                    postReaderListError(SCardError.ErrorCodes.DEVICE_NOT_CONNECTED,"The device may be disconnected or powered off", false)
                }
                else
                {
                    scardReaderList.postCallback({ callbacks.onReaderListClosed(scardReaderList) }, true)
                }

                // Reset all lists
                indexCharToBeSubscribed = 0
                indexCharToBeRead = 0
                indexSlots = 0

                lowLayer.close()
            }
            is ActionEvent.EventCharacteristicChanged -> {
                when {
                    event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_STATUS_CHAR -> {
                        /* Update readers status */
                        interpretSlotsStatus(event.characteristic.value)
                        scardReaderList.mayConnectCard()
                    }
                    event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_RDR_TO_PC_CHAR -> interpretResponseConnectingToCard(event.characteristic.value)
                    else -> Log.w(TAG,"Received notification/indication on an unexpected characteristic ${event.characteristic.uuid} (value: ${event.characteristic.value.toHexString()})")
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    /*********** Response handling **********/

    private fun handleResponseNotifyAndAck(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName} (handleResponseNotifyAndAck)")
        when (event) {
            is ActionEvent.EventCharacteristicChanged -> {
                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_RDR_TO_PC_CHAR)
                {
                    /* If there is still something to write */
                    if(lowLayer.ccidWriteCharSequenced()) {
                        response.rxBuffer.addAll(event.characteristic.value.toList())
                        val ccidLength = scardReaderList.ccidHandler.getCcidLength(response.rxBuffer.toByteArray())

                        /* Check if the Response is compete or not */
                        if(response.rxBuffer.size - CcidFrame.HEADER_SIZE != ccidLength) {
                            Log.d(TAG, "Frame not complete, excepted length = $ccidLength")
                        }
                        else {
                            response.notifyReceived = true
                            Log.d(TAG, "Write finished")
                            /* To interpret Response we must receive all the write ack and the RDR_to_PC notif */
                            if (response.isResponseComplete) {
                                response.resetReceivedFlags()
                                interpretResponse(response.rxBuffer.toByteArray())
                            }
                        }
                    }
                }
                else {
                    handleCommonActionEvents(event)
                }
            }
            is ActionEvent.EventCharacteristicWritten -> {
                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_PC_TO_RDR_CHAR) {
                    if(event.status == BluetoothGatt.GATT_SUCCESS) {
                        /* If there is still something to write */
                        if (lowLayer.ccidWriteCharSequenced()) {
                            response.ackReceived = true
                            Log.d(TAG, "Write finished")
                            /* To interpret Response we must receive all the write ack and the RDR_to_PC notif */
                            if (response.isResponseComplete) {
                                response.resetReceivedFlags()
                                interpretResponse(response.rxBuffer.toByteArray())
                            }
                        }
                    }
                    else {
                        currentState = State.Idle
                        postReaderListError(SCardError.ErrorCodes.WRITE_CHARACTERISTIC_FAILED,"Writing on characteristic ${event.characteristic.uuid} failed with status ${event.status} (BluetoothGatt constant)")
                    }
                }
            }
            else -> handleCommonActionEvents(event)
        }
    }


    private fun interpretResponse(value: ByteArray) {
        when (currentState) {
            State.ReadingSlotsName -> interpretResponseSlotName(value)
            State.Authenticate -> interpretResponseAuthenticate(value)
            State.ConnectingToCard -> interpretResponseConnectingToCard(value)
            State.WritingCmdAndWaitingResp -> interpretResponseToCommand(value)
            else -> {
                Log.e(TAG, "Wrong state for interpreting response: $currentState")
            }
        }
    }



    private fun interpretResponseAuthenticate(value: ByteArray) {
        val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(value)
        if(authenticateStep == 1) {

            if(scardReaderList.ccidHandler.ccidSecure.deviceRespStep1(ccidResponse.payload)) {
                beginWriteCommand(scardReaderList.ccidHandler.scardControl(
                    scardReaderList.ccidHandler.ccidSecure.hostCmdStep2(ccidResponse.payload.toMutableList())
                ))
                authenticateStep = 2
            }
            else {
                postReaderListError(SCardError.ErrorCodes.AUTHENTICATION_ERROR, "Authentication failed at step 1")
                return
            }
        }
        else if(authenticateStep == 2) {
            if(scardReaderList.ccidHandler.ccidSecure.deviceRespStep3(ccidResponse.payload)) {
                scardReaderList.ccidHandler.authenticateOk = true
                mayPostReaderListCreated()
            }
            else {
                postReaderListError(SCardError.ErrorCodes.AUTHENTICATION_ERROR, "Authentication failed at step 3")
                return
            }
        }
    }

    private fun interpretResponseSlotName(value: ByteArray) {
        /* Response */
        val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(value)
        val slotName = ccidResponse.payload.slice(1 until ccidResponse.payload.size).toByteArray().toString(charset("ASCII"))
        Log.d(TAG, "Slot $indexSlots name : $slotName")
        scardReaderList.readers[indexSlots].name = slotName
        scardReaderList.readers[indexSlots].index = indexSlots

        /* Get next slot name */
        indexSlots++
        if (indexSlots < scardReaderList.readers.size) {
            beginWriteCommand(scardReaderList.ccidHandler.scardControl("58210$indexSlots".hexStringToByteArray()))
        }
        else {
            Log.d(TAG, "Reading readers name finished")

            /* Go to authenticate state if necessary */
            if(scardReaderList.ccidHandler.isSecure) {
                currentState = State.Authenticate
                process(ActionEvent.ActionAuthenticate())
            }
            else {
                /* If there are one card present on one or more slot --> go to state ConnectingToCard */
                mayPostReaderListCreated()
            }
        }
    }

    private fun interpretResponseConnectingToCard(value: ByteArray) {
        /* Put data in ccid frame */
        val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(value)
        val slot = scardReaderList.readers[ccidResponse.slotNumber.toInt()]

        /* Update slot status (present, powered) */
        interpretSlotsStatusInCcidHeader(
            ccidResponse.slotStatus,
            slot
        )

        /* Check slot error */
        if (!interpretSlotsErrorInCcidHeader(
                ccidResponse.slotError,
                ccidResponse.slotStatus,
                slot
            )
        ) {
            Log.d(TAG, "Error, do not process CCID packet, returning to Idle state")
            /* reset rxBuffer */
            response.rxBuffer.clear()

            /* Remove reader we just processed */
            listReadersToConnect.remove(slot)

            mayPostReaderListCreated()

            /* Do not go further */
            return
        }

        Log.d(TAG, "Frame complete, length = ${ccidResponse.length}")

        /* reset rxBuffer */
        response.rxBuffer.clear()
        if (ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value) {

            /* Remove reader we just processed */
            listReadersToConnect.remove(slot)

            /* save ATR */
            slot.channel.atr = ccidResponse.payload

            /* Send callback */
            scardReaderList.postCallback({
                callbacks.onReaderStatus(
                    slot,
                    slot.cardPresent,
                    slot.cardConnected
                )
            })

            mayPostReaderListCreated()
        }
    }

    private fun interpretResponseToCommand(value: ByteArray) {

        analyseResponse(value)

        /* reset rxBuffer */
        response.rxBuffer.clear()

        mayPostReaderListCreated()
    }
}