/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.app.ProgressDialog
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.app.Fragment
import android.support.v4.view.GravityCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.*
import android.widget.*
import com.springcard.pcsclike.*
import com.springcard.pcsclike.utils.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_device.*
import kotlinx.android.synthetic.main.content_main.*
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.experimental.inv


abstract class DeviceFragment : Fragment() {

    protected val TAG = this::class.java.simpleName

    protected lateinit var scardDevice: SCardReaderList
    protected lateinit var deviceName: String
    protected lateinit var device : Any
    protected lateinit var progressDialog: ProgressDialog
    private lateinit var currentChannel: SCardChannel
    private var currentSlot: SCardReader? = null

    private var cApdu = mutableListOf<ByteArray>()
    private var cptApdu = 0

    private val sendCommands = listOf("Transmit", "Control")
    private var modelsApdus = mutableListOf<ApduModel>()
    var connectToNewDevice = true

    protected lateinit var  mainActivity: MainActivity
    private var isUInitialized = false
    private var isDeviceInitialized = false
    private var readingPower = false


    /* Various callback methods defined by the ScardReaderLis */
    protected var scardCallbacks: SCardReaderListCallback = object : SCardReaderListCallback() {
        override fun onReaderListCreated(readerList: SCardReaderList) {
            mainActivity.logInfo("onReaderListCreated")

            if(mainActivity.preferences.enableTimeMeasurement) {
                apduListStopTime = SystemClock.elapsedRealtime()
                val elapsedTime = apduListStopTime - apduListStartTime
                Toast.makeText(activity, "Device instantiated in ${"%.3f".format(elapsedTime.toFloat() / 1000F)}s", Toast.LENGTH_LONG).show()
            }
            scardDevice = readerList
            isDeviceInitialized = true
            if(isResumed) {
                initializeUI(readerList)
            }
            else {
                isUInitialized = false
            }

            loadDefaultApdus()
        }

        override fun onReaderListClosed(readerList: SCardReaderList?) {
            mainActivity.logInfo("onReaderListClosed")

            if(readerList == null || !isDeviceInitialized) {
                mainActivity.logInfo("SCardReaderList not initialized")
                progressDialog.dismiss()
                mainActivity.backToScanFragment()
                return
            }

            if(readerList != scardDevice) {
                mainActivity.logInfo("Error: wrong SCardReaderList")
                return
            }

            isUInitialized = false
            isDeviceInitialized = false
            mainActivity.backToScanFragment()
        }

        override fun onControlResponse(readerList: SCardReaderList, response: ByteArray) {
            mainActivity.logInfo("onControlResponse")
            
            if(readerList != scardDevice) {
                mainActivity.logInfo("Error: wrong SCardReaderList")
                return
            }
            handleRapdu(response)
        }

        override fun onReaderStatus(slot: SCardReader, cardPresent: Boolean, cardConnected: Boolean) {
            mainActivity.logInfo("onReaderStatus")

            if(slot != currentSlot) {
                mainActivity.logInfo("Wrong slot, do not update UI")
                return
            }

            /* Is update concerning selected slot */
            if(spinnerSlots?.selectedItemPosition == slot.index) {
                updateCardStatus(slot, cardPresent, cardConnected)
            }
        }

        override fun onCardConnected(channel: SCardChannel) {
            mainActivity.logInfo("onCardConnected")
            currentChannel = channel
            textState.text = getString(R.string.connected)
            textAtr.text = channel.atr.toHexString()

            connectCardButton.isEnabled = false
            disconnectCardButton.isEnabled = true
        }


        override fun onCardDisconnected(channel: SCardChannel) {
            mainActivity.logInfo("onCardDisconnected")

            if(channel != currentChannel) {
                mainActivity.logInfo("Error: wrong channel")
                return
            }

            currentChannel = channel
            textState.text = getString(R.string.disconnected)
            textAtr.text = getString(R.string.atr)

            connectCardButton.isEnabled = true
            disconnectCardButton.isEnabled = false
        }

        override fun onTransmitResponse(channel: SCardChannel, response: ByteArray) {
            mainActivity.logInfo("onTransmitResponse")

            if(channel != currentChannel) {
                mainActivity.logInfo("Error: wrong channel")
                return
            }

            handleRapdu(response)
        }

        override fun onReaderListState(readerList: SCardReaderList, isInLowPowerMode: Boolean) {
            mainActivity.logInfo("onReaderListState")

            /* Check if device is sleeping */
            /* Could also be checked via readerList.isSleeping */
            if(isInLowPowerMode) {
                mainActivity.setActionBarTitle("${this@DeviceFragment.deviceName } (z)")

                /* Disable all UI */
                updateCardStatus(currentSlot!!, cardPresent = false, cardConnected = false)
                disconnectCardButton.isEnabled = false
                connectCardButton.isEnabled = false
                transmitButton.isEnabled = false
            }
            else{
                mainActivity.setActionBarTitle(this@DeviceFragment.deviceName)
                transmitButton.isEnabled = true
            }
        }

        /* Errors callbacks */

        override fun onReaderListError(readerList: SCardReaderList?, error: SCardError) {
            mainActivity.logInfo("onReaderListError")

            if(readerList == null || !isDeviceInitialized) {
                mainActivity.logInfo("SCardReaderList not initialized")
                return
            }

            if(readerList != scardDevice) {
                mainActivity.logInfo("Error: wrong SCardReaderList")
                return
            }

            val text = "Error: ${error.message} \n${error.detail}"
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
            mainActivity.logInfo(text)

            progressDialog.dismiss()
            mainActivity.backToScanFragment()

        }

        override fun onReaderOrCardError(readerOrCard: Any, error: SCardError) {
            mainActivity.logInfo("onReaderOrCardError")

            if(!(readerOrCard == currentChannel || readerOrCard == currentSlot)) {
                mainActivity.logInfo("Error: wrong channel or slot")
                return
            }

            rapduTextBox.text.clear()
            rapduTextBox.text.append(getString(R.string.cardMute))
            transmitButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        mainActivity = activity as MainActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device, container, false)
    }

    override fun onCreateOptionsMenu(
        menu: Menu, inflater: MenuInflater
    ) {
        inflater.inflate(R.menu.device_app_bar, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        try {
            // handle item selection
            when (item.itemId) {
                R.id.action_info -> {
                    readingPower = true
                    scardDevice.control("5820BC".hexStringToByteArray())
                    return true
                }
                R.id.action_shutdown -> {
                    scardDevice.control("58AF".hexStringToByteArray())
                    return true
                }
                R.id.action_wakeup -> {
                    scardDevice.wakeUp()
                    return true
                }
                else -> return super.onOptionsItemSelected(item)
            }
        }
        catch (e: Exception) {
            mainActivity.logInfo("Impossible to send APDU (maybe the device is sleeping?)")
            return false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setDrawerState(false)

        val toolbar = mainActivity.toolbar
        mainActivity.setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            // do something here, such as start an Intent to the parent activity.

            quitAndDisconnect()
            mainActivity.drawer_layout.closeDrawer(GravityCompat.START)
        }

        if(!isUInitialized && ::scardDevice.isInitialized) {
            initializeUI(scardDevice)
        }
    }


    override fun onResume() {
        super.onResume()

        if(connectToNewDevice) {
            progressDialog = ProgressDialog(activity)
            progressDialog.isIndeterminate = true
            progressDialog.setTitle(getString(R.string.retrievingDeviceInformation))
            progressDialog.setMessage(getString(R.string.loading))
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            progressDialog.setInverseBackgroundForced(false)
            progressDialog.setCancelable(false)
            progressDialog.show()

            rapduTextBox.text.clear()

            //-------------------------------------------------------------------

            connectToDevice()

            if (mainActivity.preferences.enableTimeMeasurement) {
                apduListStartTime = SystemClock.elapsedRealtime()
            }

            mainActivity.setActionBarTitle(deviceName)

            // No auto-correct
            //capduTextBox.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            transmitButton.setOnClickListener {

                mainActivity.logInfo("Click on Run APDU")

                if((!currentSlot?.cardPresent!! || !currentSlot?.cardConnected!! || !currentSlot?.cardPowered!!) &&
                    spinnerTransmitControl.selectedItemPosition == sendCommands.indexOf("Transmit")) {
                    updateCardStatus(currentSlot!!, false, false)
                    rapduTextBox.text.clear()
                    rapduTextBox.text.append(getString(R.string.no_card))
                }
                else {
                    transmitButton.isEnabled = false
                    /* save command */
                    /* TODO CRA */
                    // addExecutedApdu(capduTextBox.text.toString())

                    cApdu = mutableListOf<ByteArray>()
                    for (line in capduTextBox.text.lines()) {

                        /* Remove all nasty characters */
                        var line2 = line.trim()
                        line2 = line2.replace(":", "")
                        line2 = line2.replace("\n", "")
                        line2 = line2.replace("\r", "")
                        line2 = line2.replace(" ", "")
                        line2 = line2.toUpperCase()

                        if (line2.isNotEmpty()) {
                            if (line2.isHex()) {
                                cApdu.add(line2.hexStringToByteArray())
                            } else {
                                mainActivity.logInfo("Warning: $line2 is not an hexadecimal string")
                            }
                        }
                    }

                    cptApdu = 0
                    rapduTextBox.text.clear()

                    if(cApdu.size == 0) {
                        rapduTextBox.text.append(getString(R.string.no_capdu))
                        transmitButton.isEnabled = true
                    }
                    else {

                        /* Save default APDU */
                        mainActivity.preferences.defaultApdus = ApduModel(0, "default APDUs", capduTextBox.text.toString(), spinnerTransmitControl.selectedItemPosition,"", "" )

                        if (mainActivity.preferences.enableTimeMeasurement) {
                            apduListStartTime = SystemClock.elapsedRealtime()
                        }
                        /* Trigger 1st APDU */
                        sendApdu()
                    }
                }
            }

            disconnectCardButton.setOnClickListener {
                currentChannel.disconnect()
            }

            connectCardButton.setOnClickListener{
                currentSlot?.cardConnect()
            }

            //------------------------------------------------------------

            /* Load Model List  */

            val listApduString = mutableListOf<String>()

            for (apdu in modelsApdus) {
                listApduString.add("${apdu.id} - ${apdu.title}")
            }

            val dataAdapter = ArrayAdapter<String>(
                activity?.applicationContext!!,
                android.R.layout.simple_spinner_item, listApduString
            )
            // Drop down layout style - list view
            dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerModels.adapter = dataAdapter


            spinnerModels.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent: AdapterView<*>?) {

                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    capduTextBox.text.clear()
                    capduTextBox.text.append(modelsApdus[spinnerModels.selectedItemPosition].apdu)
                    spinnerTransmitControl.setSelection(modelsApdus[spinnerModels.selectedItemPosition].mode)
                }
            }

            connectToNewDevice = false
        }
        else {
            mainActivity.logInfo("DeviceFragment onResume, device already connected")
        }
    }

    private fun loadDefaultApdus() {
        if(mainActivity.preferences.defaultApdus != null) {
            capduTextBox.text.clear()
            capduTextBox.text.append(mainActivity.preferences.defaultApdus!!.apdu)
            spinnerTransmitControl.setSelection(mainActivity.preferences.defaultApdus!!.mode)
        }
    }

    abstract fun connectToDevice()

    abstract fun init(_device: Any)


    private var apduListStartTime: Long = 0
    private var apduListStopTime: Long = 0

    private fun sendApdu() {
        mainActivity.logInfo("sendApdu")
        mainActivity.logInfo("<${cApdu[cptApdu].toHexString()}")

        // TODO CRA create resource string
        if(spinnerTransmitControl.selectedItemPosition == sendCommands.indexOf("Transmit")) {
            currentChannel.transmit(cApdu[cptApdu])
        }
        else if (spinnerTransmitControl.selectedItemPosition == sendCommands.indexOf("Control")) {
            scardDevice.control(cApdu[cptApdu])
        }
    }

    private fun handleRapdu(response: ByteArray) {

        if(readingPower) {
            onPowerInfo(response)
            return
        }

        val responseString = response.toHexString()
        mainActivity.logInfo(">$responseString")
        rapduTextBox.text.append(responseString + "\n")

        if(responseString.takeLast(4) != "9000" && mainActivity.preferences.stopOnError) {
            mainActivity.logInfo("Stop on error : ${responseString.takeLast(4)}")
        }
        else {
            cptApdu++
            if(cptApdu < cApdu.size) {
                sendApdu()
            }
            else {
                if(mainActivity.preferences.enableTimeMeasurement) {
                    apduListStopTime = SystemClock.elapsedRealtime()
                    val elapsedTime = apduListStopTime - apduListStartTime
                    Toast.makeText(activity, "${cApdu.size} APDU executed in ${"%.3f".format(elapsedTime.toFloat() / 1000F)}s", Toast.LENGTH_LONG).show()
                }
                transmitButton.isEnabled = true
            }
        }
    }

    fun setApduModels(models: MutableList<ApduModel>) {
        modelsApdus = models
    }


    private fun onPowerInfo(data: ByteArray) {

        mainActivity.logInfo("onPowerInfo")
        readingPower = false

        var batteryLevel = "Unknown"
        var powerState = "Unknown"

        /* Remove 00 */
        val batteryData = data.drop(1).toByteArray()

        if(batteryData.size != 9) {
            mainActivity.logInfo("Wrong size : ${batteryData.size}")
        }
        else {
            batteryLevel = "${batteryData[0]}%"

            val batteryCurrentArray =  batteryData.slice(7 .. 8).toByteArray()
            val buffer = ByteBuffer.wrap(batteryCurrentArray)
            val batteryCurrent = buffer.short

            powerState = if(batteryCurrent > 0) {
                "Charging"
            } else {
                "Discharging"
            }
        }

        /* Info dialog */
        val builder = AlertDialog.Builder(activity!!)

        builder.setTitle(deviceName)

        val deviceInfo = "Vendor: ${scardDevice.vendorName}\n" +
                "Product: ${scardDevice.productName}\n" +
                "Serial Number: ${scardDevice.serialNumber}\n" +
                "FW Version: ${scardDevice.firmwareVersion}\n" +
                "FW Version Major: ${scardDevice.firmwareVersionMajor}\n" +
                "FW Version Minor: ${scardDevice.firmwareVersionMinor}\n" +
                "FW Version Build: ${scardDevice.firmwareVersionBuild}\n" +
                "Battery Level: $batteryLevel\n" +
                "Current: $powerState"


        // Do something when user press the positive button
        builder.setMessage(deviceInfo)

        // Set a positive button and its click listener on alert dialog
        builder.setPositiveButton("OK") { _, _ ->
            // Do something when user press the positive button
        }

        // Finally, make the alert dialog using builder
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun updateCardStatus(slot: SCardReader, cardPresent: Boolean, cardConnected: Boolean) {

        rapduTextBox.text.clear()

        if(cardPresent && !cardConnected) {
            slot.cardConnect()
            textAtr?.text = getString(R.string.atr)
            textState?.text = getString(R.string.present)
            connectCardButton.isEnabled = true
            disconnectCardButton.isEnabled = false
        }
        else if(cardPresent && cardConnected) {
            textAtr.text = currentSlot?.channel!!.atr.toHexString()
            textState?.text = getString(R.string.connected)
            currentChannel = slot.channel
            connectCardButton.isEnabled = false
            disconnectCardButton.isEnabled = true
        }
        else if(!cardPresent && !cardConnected) {
            textAtr?.text = getString(R.string.atr)
            textState?.text = getString(R.string.absent)
            connectCardButton.isEnabled = false
            disconnectCardButton.isEnabled = false
        }
        else {
           mainActivity.logInfo("Impossible value: card not present but powered!")
        }
    }

    fun quitAndDisconnect() {
        progressDialog.dismiss()
        scardDevice.close()
        mainActivity.backToScanFragment()
    }

    private fun initializeUI(readerList: SCardReaderList) {

        val spinnerList =  mutableListOf<String>()


        for(i in 0 until readerList.slotCount) {
            spinnerList.add("$i - ${readerList.slots[i]}")
        }

        val adapter = ArrayAdapter<String>(
            activity?.applicationContext!!,
            android.R.layout.simple_spinner_item, spinnerList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSlots.adapter = adapter

        val dataAdapter = ArrayAdapter<String>(
            activity?.applicationContext!!,
            android.R.layout.simple_spinner_item, sendCommands
        )
        // Drop down layout style - list view
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTransmitControl.adapter = dataAdapter

        textState.text = getString(R.string.absent)
        connectCardButton.isEnabled = false
        disconnectCardButton.isEnabled = false

        currentSlot = readerList.getReader(spinnerSlots.selectedItemPosition)

        spinnerSlots.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSlot = readerList.getReader(spinnerSlots.selectedItemPosition)
                currentChannel = currentSlot?.channel!!
                updateCardStatus(currentSlot!!, currentSlot?.cardPresent!!, currentSlot?.cardConnected!!)
            }
        }
        progressDialog.dismiss()

        isUInitialized = true
    }
}
