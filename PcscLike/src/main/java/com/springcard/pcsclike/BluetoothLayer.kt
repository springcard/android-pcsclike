/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

import android.bluetooth.*
import android.content.Context
import android.util.Log
import java.util.*
import kotlin.experimental.and


internal class BluetoothLayer(private var bluetoothDevice: BluetoothDevice, private var callbacks: SCardReaderListCallback, private var scardReaderList : SCardReaderList): CommunicationLayer(callbacks, scardReaderList) {

    private val TAG = this::class.java.simpleName

    private lateinit var mBluetoothGatt: BluetoothGatt

    private val charUuidToRead = mutableListOf<UUID>(
        GattAttributesSpringCore.UUID_MODEL_NUMBER_STRING_CHAR,
        GattAttributesSpringCore.UUID_SERIAL_NUMBER_STRING_CHAR,
        GattAttributesSpringCore.UUID_FIRMWARE_REVISION_STRING_CHAR,
        GattAttributesSpringCore.UUID_HARDWARE_REVISION_STRING_CHAR,
        GattAttributesSpringCore.UUID_SOFTWARE_REVISION_STRING_CHAR,
        GattAttributesSpringCore.UUID_MANUFACTURER_NAME_STRING_CHAR,
        GattAttributesSpringCore.UUID_PNP_ID_CHAR)

    private val charUuidToReadPower = mutableListOf<UUID>(
        GattAttributesSpringCore.UUID_BATTERY_POWER_STATE_CHAR,
        GattAttributesSpringCore.UUID_BATTERY_LEVEL_CHAR)

    private var characteristicsToRead : MutableList<BluetoothGattCharacteristic> = mutableListOf<BluetoothGattCharacteristic>()
    private var characteristicsCanIndicate : MutableList<BluetoothGattCharacteristic> = mutableListOf<BluetoothGattCharacteristic>()
    private var characteristicsToReadPower : MutableList<BluetoothGattCharacteristic> = mutableListOf<BluetoothGattCharacteristic>()

    /* Various callback methods defined by the BLE API */
    private val mGattCallback: BluetoothGattCallback by lazy {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    process(ActionEvent.EventConnected())
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    process(ActionEvent.EventDisconnected())
                }
                // TODO CRA else ...
            }

            override// New services discovered
            fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                process(ActionEvent.EventServicesDiscovered(status))
            }

            override// Result of a characteristic read operation
            fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                process(ActionEvent.EventCharacteristicRead(characteristic, status))
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                process(ActionEvent.EventCharacteristicWrite(characteristic, status))
            }

            override// Characteristic notification
            fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                process(ActionEvent.EventCharacteristicChanged(characteristic))
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                process(ActionEvent.EventDescriptorWrite(descriptor, status))
            }
        }
    }


    /* Utilities methods */

     private fun create(ctx: Context) {
         mBluetoothGatt = bluetoothDevice.connectGatt(ctx, false, mGattCallback)
    }


    private fun disconnect(): Boolean {

        Log.d(TAG, "Disconnect")

        mBluetoothGatt.disconnect()
        return true
    }



    private fun getCharacteristic(charUuid: UUID) : BluetoothGattCharacteristic? {
        return  mBluetoothGatt.getService(GattAttributesSpringCore.UUID_SPRINGCARD_CCID_SERVICE)?.getCharacteristic(charUuid)
    }

    private var dataToWrite = mutableListOf<Byte>()
    private var dataToWriteCursorBegin = 0
    private var dataToWriteCursorEnd = 0

    private fun ccidWriteCharSequenced() {
        val characteristicCAPDU = getCharacteristic(GattAttributesSpringCore.UUID_CCID_PC_TO_RDR_CHAR)

        /* Temporary workaround: we can not send to much data in one write */
        /* (we can write more than MTU but less than ~512 bytes) */
        val maxSize = 512
        if(dataToWriteCursorBegin < dataToWrite.size) {
            dataToWriteCursorEnd =  minOf(dataToWriteCursorBegin+maxSize, dataToWrite.size)
            characteristicCAPDU?.value = dataToWrite.toByteArray().sliceArray(dataToWriteCursorBegin until dataToWriteCursorEnd)
            /* If the data length is greater than MTU, Android will automatically send multiple packets */
            /* There is no need to split the data ourself  */
            mBluetoothGatt.writeCharacteristic(characteristicCAPDU)
            Log.d(TAG, "Writing ${dataToWriteCursorEnd - dataToWriteCursorBegin} bytes")
            dataToWriteCursorBegin = dataToWriteCursorEnd
        }
        else {
            Log.d(TAG, "Write finished")
            currentState = State.WaitingResponse
        }
    }

    /* Warning only use this method if you are sure to have less than 512 byte  */
    /* or if you want to use a specific characteristic */
    private fun writeChar(charUuid: UUID, data: ByteArray) {
        val characteristicCAPDU = getCharacteristic(charUuid)
        characteristicCAPDU?.value = data
        mBluetoothGatt.writeCharacteristic(characteristicCAPDU)
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
            State.ReadingPowerInfo -> handleStateReadingPowerInfo(event)
            State.WritingCommand -> handleStateWritingCommand(event)
            State.WaitingResponse -> handleStateWaitingResponse(event)
            State.Disconnecting ->  handleStateDisconnecting(event)
            else -> Log.w(TAG,"Unhandled State : $currentState")
        }
    }

    private fun handleStateDisconnected(event: ActionEvent) {
        when (event) {
            is ActionEvent.ActionCreate-> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
                currentState = State.Connecting
                create(event.ctx)
                /* save context if we need to try to reconnect */
                context = event.ctx
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleStateConnecting(event: ActionEvent) {
        when (event) {
            is ActionEvent.EventConnected -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
                currentState = State.DiscoveringGatt

                mBluetoothGatt.discoverServices()
                Log.i(TAG, "Attempting to start service discovery")
            }
            is ActionEvent.EventDisconnected -> {
                // Retry connecting
                currentState = State.Disconnected
                process(ActionEvent.ActionCreate(context))
                //TODO add cpt
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleStateDiscovering(event: ActionEvent) {
        when (event) {
            is ActionEvent.EventServicesDiscovered -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")

                if (event.status == BluetoothGatt.GATT_SUCCESS) {
                    currentState = State.ReadingInformation

                    val services =  mBluetoothGatt.services
                    Log.d(TAG, services.toString())

                    for (srv in services!!) {
                        Log.d(TAG, "Service = " + srv.uuid.toString())

                        /* Detect if CCID service is bonded or not */
                        if(srv.uuid == GattAttributesSpringCore.UUID_SPRINGCARD_CCID_PLAIN_SERVICE) {
                            GattAttributesSpringCore.isCcidServiceBonded = false
                            /* Add plain CCID status char*/
                            charUuidToRead.add(GattAttributesSpringCore.UUID_CCID_STATUS_CHAR)
                        }
                        else if(srv.uuid == GattAttributesSpringCore.UUID_SPRINGCARD_CCID_BONDED_SERVICE){
                            GattAttributesSpringCore.isCcidServiceBonded = true
                            /* Add bonded CCID status char*/
                            charUuidToRead.add(GattAttributesSpringCore.UUID_CCID_STATUS_CHAR)
                        }


                        for (chr in srv.characteristics) {
                            Log.d(TAG, "Characteristic = ${chr.uuid}")
                            val property = chr.properties

                            /* If characteristic can notify/indicate */
                            if (property and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                                Log.d(TAG, "Could be notified")
                                characteristicsCanIndicate.add(chr)
                            }

                            /* If characteristic must be read to find information */
                            if (charUuidToRead.contains(chr.uuid)) {
                                Log.d(TAG, "Could be read")
                                characteristicsToRead.add(chr)
                            }


                            if(charUuidToReadPower.contains(chr.uuid)) {
                                Log.d(TAG, "Found characteristic power")
                                characteristicsToReadPower.add(chr)
                            }
                        }
                    }

                    // trigger 1st read
                    val chr = characteristicsToRead[0]
                    mBluetoothGatt.readCharacteristic(chr)

                } else {
                    Log.w(TAG, "onServicesDiscovered received: ${event.status}")
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private var indexCharToBeRead: Int = 0
    private fun handleStateReadingInformation(event: ActionEvent) {
        when (event) {
            is ActionEvent.EventCharacteristicRead -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
                if(event.status != BluetoothGatt.GATT_SUCCESS) {
                    postReaderListError(SCardError.ErrorCodes.READ_CHARACTERISTIC_FAILED, "Failed to subscribe to read characteristic ${event.characteristic}")
                    return
                }

                when(event.characteristic.uuid) {
                    GattAttributesSpringCore.UUID_MODEL_NUMBER_STRING_CHAR -> scardReaderList.productName = event.characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_SERIAL_NUMBER_STRING_CHAR -> {
                        scardReaderList.serialNumber = event.characteristic.value.toString(charset("ASCII"))
                        scardReaderList.serialNumberRaw = event.characteristic.value.toString(charset("ASCII")).hexStringToByteArray()
                    }
                    GattAttributesSpringCore.UUID_FIRMWARE_REVISION_STRING_CHAR -> scardReaderList.softwareVersion = event.characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_HARDWARE_REVISION_STRING_CHAR -> scardReaderList.hardwareVersion = event.characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_SOFTWARE_REVISION_STRING_CHAR -> getVersionFromRevString(event.characteristic.value.toString(charset("ASCII")))
                    GattAttributesSpringCore.UUID_MANUFACTURER_NAME_STRING_CHAR -> scardReaderList.vendorName = event.characteristic.value.toString(charset("ASCII"))
                    GattAttributesSpringCore.UUID_PNP_ID_CHAR -> scardReaderList.pnpId = event.characteristic.value.toHexString()
                    GattAttributesSpringCore.UUID_CCID_STATUS_CHAR -> {
                        val slotCount = event.characteristic.value[0]

                        if(slotCount.toInt() == 0) {
                            postReaderListError(SCardError.ErrorCodes.DUMMY_DEVICE, "This device has 0 slots")
                            return
                        }

                        /* Add n new readers */
                        for (i in 0 until slotCount) {
                            scardReaderList.readers.add(SCardReader(scardReaderList))
                        }

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
                    mBluetoothGatt.readCharacteristic(chr)
                }
                else {
                    Log.d(TAG, "Reading Information finished")
                    currentState = State.SubscribingNotifications
                    // Trigger 1st subscribing
                    val chr = characteristicsCanIndicate[0]
                    enableNotifications(chr)
                }
            }
            else ->  Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private var indexCharToBeSubscribed: Int = 0
    private fun handleStateSubscribingNotifications(event: ActionEvent) {
        when (event) {
            is ActionEvent.EventDescriptorWrite -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
                if(event.status != BluetoothGatt.GATT_SUCCESS) {
                    postReaderListError(SCardError.ErrorCodes.ENABLE_CHARACTERISTIC_EVENTS_FAILED, "Failed to subscribe to notification for characteristic ${event.descriptor.characteristic}")
                    return
                }

                indexCharToBeSubscribed++
                if (indexCharToBeSubscribed < characteristicsCanIndicate.size) {
                    val chr = characteristicsCanIndicate[indexCharToBeSubscribed]
                    enableNotifications(chr)
                }
                else {
                    Log.d(TAG, "Subscribing finished")

                    currentState = State.ReadingSlotsName
                    // Trigger 1st APDU to get slot name
                    writeChar(GattAttributesSpringCore.UUID_CCID_PC_TO_RDR_CHAR, scardReaderList.ccidHandler.scardControl("582100".hexStringToByteArray()))
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleStateReadingSlotsName(event: ActionEvent) {
        when (event) {
            is ActionEvent.EventCharacteristicChanged -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")

                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_RDR_TO_PC_CHAR)
                {
                    /* Response */
                    val slotName = event.characteristic.value.slice(11 until event.characteristic.value.size).toByteArray().toString(charset("ASCII"))
                    Log.d(TAG, "Slot $indexSlots name : $slotName")
                    scardReaderList.readers[indexSlots].name = slotName
                    scardReaderList.readers[indexSlots].index = indexSlots

                    /* Get next slot name */
                    indexSlots++
                    if (indexSlots < scardReaderList.readers.size) {
                        writeChar(GattAttributesSpringCore.UUID_CCID_PC_TO_RDR_CHAR, scardReaderList.ccidHandler.scardControl("58210$indexSlots".hexStringToByteArray()))
                    }
                    else {
                        Log.d(TAG, "Reading readers name finished")

                        /* Check if there is some card already presents on the slots */
                        listReadersToConnect.clear()
                        for (slot in scardReaderList.readers) {
                            if(slot.cardPresent && !slot.cardPowered) {
                                Log.d(TAG, "Slot: ${slot.name}, card present but not powered --> must connect to this card")
                                listReadersToConnect.add(slot)
                            }
                        }

                        /* Go to authenticate state if necessary */
                        if(scardReaderList.ccidHandler.isSecure) {
                            currentState = State.Authenticate
                            process(ActionEvent.ActionAuthenticate())
                        }
                        else {
                            /* If there are one card present on one or more slot --> go to state ConnectingToCard */
                            processNextSlotConnection()
                        }
                    }
                }
                else {
                    Log.w(TAG, "Received notification/indication on an unexpected characteristic  ${event.characteristic.uuid}")
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private var authenticateStep = 0
    private fun handleStateAuthenticate(event: ActionEvent) {
        when (event) {
            is ActionEvent.ActionAuthenticate -> {
                writeChar(GattAttributesSpringCore.UUID_CCID_PC_TO_RDR_CHAR, scardReaderList.ccidHandler.scardControl(scardReaderList.ccidHandler.ccidSecure.hostAuthCmd()))
                authenticateStep = 1
            }
            is ActionEvent.EventCharacteristicChanged -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")

                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_RDR_TO_PC_CHAR)
                {
                    val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(event.characteristic.value)
                    if(authenticateStep == 1) {

                        scardReaderList.ccidHandler.ccidSecure.deviceRespStep1(ccidResponse.payload)
                        writeChar(
                            GattAttributesSpringCore.UUID_CCID_PC_TO_RDR_CHAR,
                            scardReaderList.ccidHandler.scardControl(
                                scardReaderList.ccidHandler.ccidSecure.hostCmdStep2(ccidResponse.payload.toMutableList())
                            )
                        )
                        authenticateStep = 2
                    }
                    else if(authenticateStep == 2) {
                        scardReaderList.ccidHandler.ccidSecure.deviceRespStep3(ccidResponse.payload)

                        scardReaderList.ccidHandler.authenticateOk = true
                        processNextSlotConnection()
                    }
                }
                else {
                    Log.w(TAG, "Received notification/indication on an unexpected characteristic  ${event.characteristic.uuid}")
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }


    private var rxBuffer = mutableListOf<Byte>()
    private fun handleStateConnectingToCard(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionWriting -> {
                Log.d(TAG, "Writing ${event.command.toHexString()}")

                /* Clear and set data to write */
                dataToWrite.clear()
                dataToWrite.addAll(event.command.toList())

                dataToWriteCursorBegin = 0
                dataToWriteCursorEnd = 0

                /* Trigger 1st write operation */
                ccidWriteCharSequenced()
            }
            is ActionEvent.EventCharacteristicWrite -> {
                Log.d(TAG, "Write succeed")
            }
            is ActionEvent.EventCharacteristicChanged -> {

                if (event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_RDR_TO_PC_CHAR) {

                    rxBuffer.addAll(event.characteristic.value.toList())
                    val ccidLength = scardReaderList.ccidHandler.getCcidLength(rxBuffer.toByteArray())

                    /* Check if the response is compete or not */
                    if( rxBuffer.size- CcidFrame.HEADER_SIZE != ccidLength) {
                        Log.d(TAG, "Frame not complete, excepted length = $ccidLength")
                    }
                    else {
                        /* Put data in ccid frame */
                        val ccidResponse = scardReaderList.ccidHandler.getCcidResponse(rxBuffer.toByteArray())
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
                                slot,
                                false // do not post callback
                            )
                        ) {
                            Log.d(TAG, "Error, do not process CCID packet, returning to Idle state")
                            /* reset rxBuffer */
                            rxBuffer = mutableListOf<Byte>()

                            /* Remove reader we just processed */
                            listReadersToConnect.remove(slot)

                            processNextSlotConnection()

                            /* Do not go further */
                            return
                        }

                        Log.d(TAG, "Frame complete, length = ${ccidResponse.length}")

                        /* reset rxBuffer */
                        rxBuffer = mutableListOf<Byte>()
                        if (ccidResponse.code == CcidResponse.ResponseCode.RDR_To_PC_DataBlock.value) {

                            // save ATR
                            slot.channel.atr = ccidResponse.payload
                            // set cardPowered flag
                            slot.cardPowered = true

                            /* Remove reader we just processed */
                            listReadersToConnect.remove(slot)

                            /* Change state if we are at the end of the list */
                            processNextSlotConnection()

                        }
                    }
                }
                else {
                    Log.w(TAG,"Received notification/indication on an unexpected characteristic  ${event.characteristic.uuid}")
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }



    private fun handleStateIdle(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.ActionDisconnect -> {
                currentState = State.Disconnecting
                disconnect()
            }
            is ActionEvent.ActionWriting -> {
                currentState = State.WritingCommand
                Log.d(TAG, "Writing ${event.command.toHexString()}")

                /* Clear and set data to write */
                dataToWrite.clear()
                dataToWrite.addAll(event.command.toList())

                dataToWriteCursorBegin = 0
                dataToWriteCursorEnd = 0

                /* Trigger 1st write operation */
                ccidWriteCharSequenced()
            }
            is ActionEvent.EventCharacteristicChanged -> {
                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_STATUS_CHAR) {
                    /* Update readers status */
                    interpretSlotsStatus(event.characteristic.value)
                }
                else {
                    Log.w(TAG,"Received notification/indication on an unexpected characteristic  ${event.characteristic.uuid}")
                }
            }
            is ActionEvent.ActionReadPowerInfo -> {
                currentState = State.ReadingPowerInfo
                process(event)
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
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
                    mBluetoothGatt.readCharacteristic(chr)
                }
                else {
                    currentState = State.Idle
                    /* read done --> send callback */
                    scardReaderList.handler.post {
                        callbacks.onPowerInfo(
                            scardReaderList,
                            powerState,
                            batteryLevel
                        )
                    }
                }
            }
            is ActionEvent.ActionReadPowerInfo -> {
                /* Trigger 1st read */
                indexCharToReadPower = 0
                val chr = characteristicsToReadPower[indexCharToReadPower]
                mBluetoothGatt.readCharacteristic(chr)
            }
            is ActionEvent.EventCharacteristicChanged -> {
                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_STATUS_CHAR) {
                    /* Update readers status */
                    interpretSlotsStatus(event.characteristic.value)
                }
                else {
                    Log.w(TAG,"Received notification/indication on an unexpected characteristic  ${event.characteristic.uuid}")
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleStateWritingCommand(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventCharacteristicWrite -> {

                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_PC_TO_RDR_CHAR) {
                    if(event.status == BluetoothGatt.GATT_SUCCESS) {
                        ccidWriteCharSequenced()
                    }
                    else {
                        currentState = State.Idle
                        postReaderListError(SCardError.ErrorCodes.WRITE_CHARACTERISTIC_FAILED,"Writing on characteristic ${event.characteristic.uuid} failed with status ${event.status} (BluetoothGatt constant)")
                    }
                }
                else {
                    Log.w(TAG,"Received written indication on an unexpected characteristic  ${event.characteristic.uuid}")
                }
            }
            is ActionEvent.EventCharacteristicChanged -> {
                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_STATUS_CHAR) {
                    /* Update readers status */
                    interpretSlotsStatus(event.characteristic.value)
                }
                else {
                    Log.w(TAG,"Received notification/indication on an unexpected characteristic  ${event.characteristic.uuid}")
                }
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }


    private fun handleStateWaitingResponse(event: ActionEvent) {
        Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
        when (event) {
            is ActionEvent.EventCharacteristicChanged -> {

                if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_RDR_TO_PC_CHAR) {

                    rxBuffer.addAll(event.characteristic.value.toList())
                    val ccidLength = scardReaderList.ccidHandler.getCcidLength(rxBuffer.toByteArray())

                    /* Check if the response is compete or not */
                    if( rxBuffer.size- CcidFrame.HEADER_SIZE != ccidLength) {
                        Log.d(TAG, "Frame not complete, excepted length = $ccidLength")
                    }
                    else {
                        analyseResponse(rxBuffer.toByteArray())

                        /* reset rxBuffer */
                        rxBuffer = mutableListOf<Byte>()

                    }
                }
                else if(event.characteristic.uuid == GattAttributesSpringCore.UUID_CCID_STATUS_CHAR) {
                    /* Update readers status */
                    interpretSlotsStatus(event.characteristic.value)
                }
                else {
                    Log.w(TAG,"Received notification/indication on an unexpected characteristic  ${event.characteristic.uuid}")
                }
            }
            else -> Log.w(TAG ,"Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun handleStateDisconnecting(event: ActionEvent) {
        when (event) {
            is ActionEvent.EventDisconnected -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
                currentState = State.Disconnected
                scardReaderList.handler.post {callbacks.onReaderListClosed(scardReaderList)}

                // Reset all lists
                characteristicsCanIndicate.clear()
                indexCharToBeSubscribed = 0

                characteristicsToRead.clear()
                indexCharToBeRead = 0

                scardReaderList.readers.clear()
                indexSlots = 0

                mBluetoothGatt.close()
            }
            else -> Log.w(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }


    private fun enableNotifications(chr : BluetoothGattCharacteristic) {
        mBluetoothGatt.setCharacteristicNotification(chr, true)
        val descriptor = chr.descriptors[0]
        descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        if (!mBluetoothGatt.writeDescriptor(descriptor)) {
            postReaderListError(SCardError.ErrorCodes.ENABLE_CHARACTERISTIC_EVENTS_FAILED,"Failed to write in descriptor, to enable notification on characteristic ${chr.uuid}")
            return
        }
    }
}