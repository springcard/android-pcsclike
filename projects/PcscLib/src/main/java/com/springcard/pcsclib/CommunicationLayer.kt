package com.springcard.pcsclib

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log


internal enum class State{
    Disconnected,
    Connecting,
    Connected,
    DiscoveringGatt,
    ReadingInformation,
    SubscribingNotifications,
    ReadingSlotsName,
    Authenticate,
    ConnectingToCard,
    Idle,
    ReadingPowerInfo,
    WritingCommand,
    WaitingResponse,
    Disconnecting
}


internal sealed class ActionEvent {
    class ActionConnect(val ctx: Context) : ActionEvent()
    class EventConnected : ActionEvent()
    class ActionCreate : ActionEvent()
    class EventServicesDiscovered(val status: Int) : ActionEvent()
    class EventDescriptorWrite(val descriptor: BluetoothGattDescriptor, val status: Int) : ActionEvent()
    class EventCharacteristicChanged(val characteristic: BluetoothGattCharacteristic) : ActionEvent()
    class EventCharacteristicWrite(val characteristic: BluetoothGattCharacteristic, val status: Int) : ActionEvent()
    class EventCharacteristicRead(val characteristic: BluetoothGattCharacteristic, val status: Int) : ActionEvent()
    class ActionWriting(val command: ByteArray) : ActionEvent()
    class ActionAuthenticate : ActionEvent()
    class ActionDisconnect : ActionEvent()
    class EventDisconnected : ActionEvent()
    class ActionReadPowerInfo : ActionEvent()
}


internal abstract class CommunicationLayer(private var callbacks: SCardReaderListCallback, private var scardReaderList : SCardReaderList) {

    private val TAG = this::class.java.simpleName
    protected var currentState = State.Disconnected
    protected lateinit var context: Context

    abstract fun process(event: ActionEvent)

    /* Post error callbacks */

    internal fun postReaderListError(code : SCardError.ErrorCodes, detail: String, isFatal: Boolean = true) {
        Log.e(TAG, "Error readerList: ${code.name}, $detail")

        scardReaderList.mHandler.post {
            callbacks.onReaderListError(scardReaderList, SCardError(code, detail, isFatal))
        }

        /* irrecoverable error --> disconnect */
        if(isFatal) {
            process(ActionEvent.ActionDisconnect())
        }
    }

    internal fun postCardOrReaderError(code : SCardError.ErrorCodes, detail: String, reader: SCardReader) {
        Log.e(TAG, "Error reader or card: ${code.name}, $detail")
        scardReaderList.mHandler.post {
            callbacks.onReaderOrCardError(reader, SCardError(code, detail))
        }
    }


}