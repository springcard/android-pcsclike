package com.springcard.pcsclike

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.annotation.RequiresApi
import android.util.Log

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class BleLowLevel(private val highLayer: BluetoothLayer) {

    private val TAG = this::class.java.simpleName
    private lateinit var mBluetoothGatt: BluetoothGatt
    private var bleSupervisionTimeoutCallback: Runnable = Runnable {
        Log.i(TAG, "Timeout BLE ${SCardReaderListBle.timeout}")
        highLayer.postReaderListError(SCardError.ErrorCodes.DEVICE_NOT_CONNECTED,"The device may be disconnected or powered off")
    }
    private val bleSupervisionTimeout: Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    /* Various callback methods defined by the BLE API */
    private val mGattCallback: BluetoothGattCallback by lazy {
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                cancelTimer(object{}.javaClass.enclosingMethod!!.name)
                if (newState == BluetoothProfile.STATE_CONNECTED) {

                    //mBluetoothGatt.requestMtu(250)
                    mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)

                    highLayer.process(ActionEvent.EventConnected())
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    highLayer.process(ActionEvent.EventDisconnected())
                }
                // TODO CRA else ...
            }

            override// New services discovered
            fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                cancelTimer(object{}.javaClass.enclosingMethod!!.name)
                highLayer.process(ActionEvent.EventServicesDiscovered(status))
            }

            override// Result of a characteristic read operation
            fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                cancelTimer(object{}.javaClass.enclosingMethod!!.name)
                Log.d(TAG, "Read ${characteristic.value.toHexString()} on characteristic ${characteristic.uuid}")
                highLayer.process(ActionEvent.EventCharacteristicRead(characteristic, status))
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                cancelTimer(object{}.javaClass.enclosingMethod!!.name)
                highLayer.process(ActionEvent.EventCharacteristicWrite(characteristic, status))
            }

            override// Characteristic notification
            fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                cancelTimer(object{}.javaClass.enclosingMethod!!.name)
                Log.d(TAG, "Characteristic ${characteristic.uuid} changed, value : ${characteristic.value.toHexString()}")
                highLayer.process(ActionEvent.EventCharacteristicChanged(characteristic))
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                cancelTimer(object{}.javaClass.enclosingMethod!!.name)
                highLayer.process(ActionEvent.EventDescriptorWrite(descriptor, status))
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                cancelTimer(object{}.javaClass.enclosingMethod!!.name)
                Log.d(TAG, "MTU size = $mtu")
                highLayer.process(ActionEvent.EventConnected())
                super.onMtuChanged(gatt, mtu, status)
            }
        }
    }

    /* Utilities methods */

    fun connect(ctx: Context) {
        Log.d(TAG, "Connect")
        mBluetoothGatt = highLayer.bluetoothDevice.connectGatt(ctx, false, mGattCallback)
        beginTimer(object{}.javaClass.enclosingMethod!!.name, 5000)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnect")
        mBluetoothGatt.disconnect()
        beginTimer(object{}.javaClass.enclosingMethod!!.name)
    }

    fun close() {
        Log.d(TAG, "Close")
        mBluetoothGatt.close()
        /* No timer needed */
    }

    fun discoverGatt() {
        mBluetoothGatt.discoverServices()
        beginTimer(object{}.javaClass.enclosingMethod!!.name)
    }

    fun getServices(): List<BluetoothGattService> {
        return mBluetoothGatt.services
    }

    fun readCharacteristic(chr: BluetoothGattCharacteristic) {
        mBluetoothGatt.readCharacteristic(chr)
        beginTimer(object{}.javaClass.enclosingMethod!!.name)
    }


    private var dataToWrite = mutableListOf<Byte>()
    private var dataToWriteCursorBegin = 0
    private var dataToWriteCursorEnd = 0


    fun putDataToBeWrittenSequenced(data: List<Byte>) {
        dataToWrite.clear()
        dataToWrite.addAll(data)
        dataToWriteCursorBegin = 0
        dataToWriteCursorEnd = 0
    }

    /* return Boolean true if finished */
    fun ccidWriteCharSequenced(): Boolean {
        Log.d(TAG, "Writing ${dataToWrite.toHexString()}")
        /* Temporary workaround: we can not send to much data in one write */
        /* (we can write more than MTU but less than ~512 bytes) */
        val maxSize = 512
        return if(dataToWriteCursorBegin < dataToWrite.size) {
            dataToWriteCursorEnd =  minOf(dataToWriteCursorBegin+maxSize, dataToWrite.size)
            highLayer.charCcidPcToRdr.value = dataToWrite.toByteArray().sliceArray(dataToWriteCursorBegin until dataToWriteCursorEnd)
            /* If the data length is greater than MTU, Android will automatically send multiple packets */
            /* There is no need to split the data ourself  */
            mBluetoothGatt.writeCharacteristic(highLayer.charCcidPcToRdr)
            Log.d(TAG, "Writing ${dataToWriteCursorEnd - dataToWriteCursorBegin} bytes")
            dataToWriteCursorBegin = dataToWriteCursorEnd
            beginTimer(object{}.javaClass.enclosingMethod!!.name)
            false
        } else {
            true
        }
    }

    /* Warning only use this method if you are sure to have less than 512 byte  */
    /* or if you want to use a specific characteristic */
    fun ccidWriteChar(data: ByteArray) {
        Log.d(TAG, "Writing ${data.toHexString()}")
        highLayer.charCcidPcToRdr.value = data
        mBluetoothGatt.writeCharacteristic(highLayer.charCcidPcToRdr)
        beginTimer(object{}.javaClass.enclosingMethod!!.name)
    }

    fun enableNotifications(chr : BluetoothGattCharacteristic) {
        mBluetoothGatt.setCharacteristicNotification(chr, true)
        val descriptor = chr.descriptors[0]
        descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        if (!mBluetoothGatt.writeDescriptor(descriptor)) {
            highLayer.postReaderListError(SCardError.ErrorCodes.ENABLE_CHARACTERISTIC_EVENTS_FAILED,"Failed to write in descriptor, to enable notification on characteristic ${chr.uuid}")
            return
        }

    }


    /* Timeout utilities */

    private fun beginTimer(callingMethod: String = "", duration: Long = SCardReaderListBle.timeout) {
        Log.i(TAG, "Begin BLE timer ($callingMethod)")
        bleSupervisionTimeout.postDelayed(bleSupervisionTimeoutCallback, duration)
    }

    private fun cancelTimer(callingMethod: String = "") {
        Log.i(TAG, "Stop BLE timer ($callingMethod)")
        bleSupervisionTimeout.removeCallbacks(bleSupervisionTimeoutCallback)
    }

}