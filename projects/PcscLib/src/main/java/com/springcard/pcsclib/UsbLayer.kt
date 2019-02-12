package com.springcard.pcsclib

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue

internal class UsbLayer(private var usbDevice: UsbDevice, private var callbacks: SCardReaderListCallback, private var scardReaderList : SCardReaderList): CommunicationLayer(callbacks, scardReaderList) {

    private val TAG = this::class.java.simpleName

    /* useful constants */
    private val BULK_TIMEOUT_MS: Byte = 100
    private val spVendorId = 7220

    /* communication constants */
    private val RDR_to_PC_NotifySlotChange = 0x50.toByte()
    private val RDR_To_PC_DataBlock = 0x80.toByte()
    private val RDR_To_PC_SlotStatus = 0x81.toByte()

    /* current action type */
    private val WAIT_NONE: Byte = 0
    private val WAIT_ATR: Byte = 1
    private val WAIT_APDU: Byte = 2


    /* USB Device Endpoints */
    private lateinit var bulkOut: UsbEndpoint
    private lateinit var  bulkIn: UsbEndpoint
    private lateinit var  interruptIn: UsbEndpoint

    /* USB access points */
    private var reader_connection: UsbDeviceConnection? = null
    private var manufacturer_name: String? = null
    private var product_name: String? = null

    val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    /* tasking */
    private var localThread: Thread? = null
    private var operationThread: Thread? = null

    /* reader part */
    private var bMaxSlotIndex = 0
    private val awaitingOperation = ArrayBlockingQueue<Int>(5)
    private val apduResult = ArrayBlockingQueue<CcidResponse>(5)

    /* current operation */
    private var currentOperation = WAIT_NONE
    private val currentSlot = 0
    val RAPDU_ERROR = byteArrayOf(0xFF.toByte(), 0xFF.toByte())


    override fun process(event: ActionEvent) {

        scardReaderList.mHandler.post {

            Log.d(TAG, "Current state = ${currentState.name}")
            // Memo CRA : SCardDevice instance = 0x${System.identityHashCode(scardDevice).toString(16).toUpperCase()}

            when (currentState) {
                State.Disconnected -> handleStateDisconnected(event)
                /*State.Connecting -> handleStateConnecting(event)
            State.Connected -> handleStateConnected(event)
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
            State.Disconnecting ->  handleStateDisconnecting(event)*/
                else -> Log.w(TAG, "Unhandled State : $currentState")
            }
        }
    }

    private fun handleStateDisconnected(event: ActionEvent) {
        when (event) {
            is ActionEvent.ActionConnect -> {
                Log.d(TAG, "ActionEvent ${event.javaClass.simpleName}")
                currentState = State.Connecting

                /* save context if we need to try to reconnect */
                context = event.ctx

                if(connect()) {
                    scardReaderList.mHandler.post {callbacks.onConnect(scardReaderList)}
                }
                else {
                    postReaderListError(
                        SCardError.ErrorCodes.DEVICE_NOT_CONNECTED,
                        "Could not connect to device")
                }
            }
            else -> Log.e(TAG, "Unwanted ActionEvent ${event.javaClass.simpleName}")
        }
    }

    private fun connect(): Boolean {
        val usbDeviceConnection: UsbDeviceConnection

        /* query for interface */
        if (usbDevice.interfaceCount == 0) {
            Log.e(TAG, "Could not find interface ")
            return false
        }
        val usbInterface = usbDevice.getInterface(0)

        /* check for endpoint */
        if (usbInterface.endpointCount == 0) {
            Log.e(TAG, "could not find endpoint")
            return false
        }

        /* connect to device */
        try {
            usbDeviceConnection = usbManager.openDevice(usbDevice)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Could not open device $usbDevice")
            return false
        }


        if (usbDeviceConnection == null) {
            Log.e(TAG, "Unable to attach to new plugged device $usbDevice")
            return false
        }



        return true
    }
}
