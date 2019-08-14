/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import android.bluetooth.BluetoothDevice
import android.os.Build
import android.support.annotation.RequiresApi
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.SCardReaderListCallback

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class BleLayer(scardReaderList : SCardReaderList, bluetoothDevice: BluetoothDevice ): CommunicationLayer(scardReaderList) {

    private val TAG = this::class.java.simpleName

    override var lowLayer = BleLowLevel(scardReaderList, bluetoothDevice) as LowLevelLayer

    override fun wakeUp() {

    }
}