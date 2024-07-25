/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.app.ProgressDialog
import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.core.view.GravityCompat
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.view.*
import android.widget.*
import com.springcard.pcsclike.*
import com.springcard.pcsclike.utils.*
import com.springcard.pcsclike_sample.databinding.FragmentDeviceBinding
import java.lang.Exception
import java.nio.ByteBuffer

abstract class DeviceFragment : Fragment() {

    private var _binding: FragmentDeviceBinding? = null
    private val binding get() = _binding!!

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

    private var currentSlotIndex = 0
    private var torcIndex = 0
    private var modelIndex = 0

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
            if(currentSlotIndex == slot.index) {
                updateCardStatus(slot, cardPresent, cardConnected)
            }
        }

        override fun onCardConnected(channel: SCardChannel) {
            mainActivity.logInfo("onCardConnected")
            currentChannel = channel
            binding.textState.text = getString(R.string.connected)
            binding.textAtr.text = channel.atr.toHexString()

            binding.connectCardButton.isEnabled = false
            binding.disconnectCardButton.isEnabled = true
        }


        override fun onCardDisconnected(channel: SCardChannel) {
            mainActivity.logInfo("onCardDisconnected")

            if(channel != currentChannel) {
                mainActivity.logInfo("Error: wrong channel")
                return
            }

            currentChannel = channel
            binding.textState.text = getString(R.string.disconnected)
            binding.textAtr.text = getString(R.string.atr)

            binding.connectCardButton.isEnabled = true
            binding.disconnectCardButton.isEnabled = false
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
                mainActivity.setActionBarTitle("${this@DeviceFragment.deviceName } \uD83D\uDCA4")

                /* Disable all UI */
                updateCardStatus(currentSlot!!, cardPresent = false, cardConnected = false)
                binding.disconnectCardButton.isEnabled = false
                binding.connectCardButton.isEnabled = false
                binding.transmitButton.isEnabled = false
            }
            else{
                mainActivity.setActionBarTitle(this@DeviceFragment.deviceName)
                binding.transmitButton.isEnabled = true
            }
        }

        /* Errors callbacks */

        override fun onReaderListError(readerList: SCardReaderList?, error: SCardError) {
            mainActivity.logInfo("onReaderListError")

            Toast.makeText(activity, "${error.message}\n${error.detail}", Toast.LENGTH_LONG).show()

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

            Toast.makeText(activity, "${error.message}\n${error.detail}", Toast.LENGTH_LONG).show()

            if(!(readerOrCard == currentChannel || readerOrCard == currentSlot)) {
                mainActivity.logInfo("Error: wrong channel or slot")
                return
            }

            binding.rapduTextBox.text.clear()
            binding.rapduTextBox.text.append(getString(R.string.cardMute))
            binding.transmitButton.isEnabled = true
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
        _binding = FragmentDeviceBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
            readingPower = false
            mainActivity.logInfo("Impossible to send APDU (maybe the device is busy or sleeping)")
            return false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setDrawerState(false)

        val toolbar = mainActivity.getToolbar()
        mainActivity.setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            // do something here, such as start an Intent to the parent activity.

            quitAndDisconnect()
            mainActivity.getDrawerLayout().closeDrawer(GravityCompat.START)
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

            binding.rapduTextBox.text.clear()

            //-------------------------------------------------------------------

            connectToDevice()

            if (mainActivity.preferences.enableTimeMeasurement) {
                apduListStartTime = SystemClock.elapsedRealtime()
            }

            mainActivity.setActionBarTitle(deviceName)

            // No auto-correct
            //capduTextBox.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            binding.transmitButton.setOnClickListener {

                mainActivity.logInfo("Click on Run APDU")

                if((!currentSlot?.cardPresent!! || !currentSlot?.cardConnected!! || !currentSlot?.cardPowered!!) &&
                    torcIndex == sendCommands.indexOf("Transmit")) {
                    updateCardStatus(currentSlot!!, false, false)
                    binding.rapduTextBox.text.clear()
                    binding.rapduTextBox.text.append(getString(R.string.no_card))
                }
                else {
                    binding.disconnectCardButton.isEnabled = false
                    binding.connectCardButton.isEnabled = false
                    binding.transmitButton.isEnabled = false
                    /* save command */
                    /* TODO CRA */
                    // addExecutedApdu(capduTextBox.text.toString())

                    cApdu = mutableListOf<ByteArray>()
                    for (line in binding.capduTextBox.text.lines()) {

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
                    binding.rapduTextBox.text.clear()

                    if(cApdu.size == 0) {
                        binding.rapduTextBox.text.append(getString(R.string.no_capdu))
                        binding.disconnectCardButton.isEnabled = true
                        binding.connectCardButton.isEnabled = true
                        binding.transmitButton.isEnabled = true
                    }
                    else {

                        /* Save default APDU */
                        mainActivity.preferences.defaultApdus = ApduModel(0, "default APDUs", binding.capduTextBox.text.toString(), torcIndex,"", "" )

                        if (mainActivity.preferences.enableTimeMeasurement) {
                            apduListStartTime = SystemClock.elapsedRealtime()
                        }
                        /* Trigger 1st APDU */
                        sendApdu()
                    }
                }
            }

            binding.disconnectCardButton.setOnClickListener {
                currentChannel.disconnect()
            }

            binding.connectCardButton.setOnClickListener{
                currentSlot?.cardConnect()
            }

            //------------------------------------------------------------

            /* Load Model List  */



            connectToNewDevice = false
        }
        else {
            mainActivity.logInfo("DeviceFragment onResume, device already connected")
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
        if(torcIndex == sendCommands.indexOf("Transmit")) {
            currentChannel.transmit(cApdu[cptApdu])
        }
        else if (torcIndex == sendCommands.indexOf("Control")) {
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
        binding.rapduTextBox.text.append(responseString + "\n")

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
                    Log.d(TAG, "${cApdu.size} APDU executed in ${"%.3f".format(elapsedTime.toFloat() / 1000F)}s")
                    Toast.makeText(activity, "${cApdu.size} APDU executed in ${"%.3f".format(elapsedTime.toFloat() / 1000F)}s", Toast.LENGTH_LONG).show()
                }
                binding.disconnectCardButton.isEnabled = true
                binding.connectCardButton.isEnabled = true
                binding.transmitButton.isEnabled = true
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
        val builder = AlertDialog.Builder(requireActivity())

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

        binding.rapduTextBox.text.clear()

        if(cardPresent && !cardConnected) {
            slot.cardConnect()
            binding.textAtr?.text = getString(R.string.atr)
            binding.textState?.text = getString(R.string.present)
            binding.connectCardButton.isEnabled = true
            binding.disconnectCardButton.isEnabled = false
        }
        else if(cardPresent && cardConnected) {
            binding.textAtr.text = currentSlot?.channel!!.atr.toHexString()
            binding.textState?.text = getString(R.string.connected)
            currentChannel = slot.channel
            binding.connectCardButton.isEnabled = false
            binding.disconnectCardButton.isEnabled = true
        }
        else if(!cardPresent && !cardConnected) {
            binding.textAtr?.text = getString(R.string.atr)
            binding.textState?.text = getString(R.string.absent)
            binding.connectCardButton.isEnabled = false
            binding.disconnectCardButton.isEnabled = false
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

        // SLOT
        val arrayAdapter = ArrayAdapter<String>(
            activity?.applicationContext!!,
            android.R.layout.simple_spinner_item,
            spinnerList
        )
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSlots.setText(arrayAdapter.getItem(currentSlotIndex).toString(), false)
        binding.spinnerSlots.apply {
            setAdapter(arrayAdapter)
            setOnItemClickListener { _, _, position, _ ->
                currentSlotIndex = position
                currentSlot = readerList.getReader(currentSlotIndex)
                currentChannel = currentSlot?.channel!!
                updateCardStatus(currentSlot!!, currentSlot?.cardPresent!!, currentSlot?.cardConnected!!)
            }
        }

        // T&C
        val dataAdapter = ArrayAdapter<String>(
            activity?.applicationContext!!,
            android.R.layout.simple_spinner_item,
            sendCommands
        )
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTransmitControl.setText(dataAdapter.getItem(torcIndex).toString(), false)
        binding.spinnerTransmitControl.apply {
            setAdapter(dataAdapter)
            setOnItemClickListener { _, _, position, _ ->
                torcIndex = position
            }
        }

        val listApduString = mutableListOf<String>()

        for (apdu in modelsApdus) {
            listApduString.add("${apdu.id} - ${apdu.title}")
        }

        val modelAdapter = ArrayAdapter(
            activity?.applicationContext!!,
            android.R.layout.simple_spinner_item,
            listApduString
        )

        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerModels.setText(modelAdapter.getItem(modelIndex).toString(), false)
        binding.spinnerModels.apply {
            setAdapter(modelAdapter)
            setOnItemClickListener { _, _, position, _ ->
                modelIndex = position
                binding.capduTextBox.text.clear()
                var model = modelsApdus[position]
                binding.capduTextBox.text.append(model.apdu)
                torcIndex = model.mode
                binding.spinnerTransmitControl.setText(dataAdapter.getItem(torcIndex).toString(), false)
            }
        }


        binding.textState.text = getString(R.string.absent)
        binding.connectCardButton.isEnabled = false
        binding.disconnectCardButton.isEnabled = false

        currentSlot = readerList.getReader(currentSlotIndex)

        progressDialog.dismiss()

        isUInitialized = true
    }
}
