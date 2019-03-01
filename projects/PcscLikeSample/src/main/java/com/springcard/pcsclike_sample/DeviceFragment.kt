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
import android.view.*
import android.widget.*
import com.springcard.pcsclike.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_device.*
import kotlinx.android.synthetic.main.content_main.*


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

    /* Various callback methods defined by the ScardReaderLis */
    protected var scardCallbacks: SCardReaderListCallback = object : SCardReaderListCallback() {
        override fun onReaderListCreated(readerList: SCardReaderList) {
            mainActivity.logInfo("onReaderListCreated")

            if(mainActivity.enableTimeMeasurement) {
                apduListStopTime = SystemClock.elapsedRealtime()
                val elapsedTime = apduListStopTime - apduListStartTime
                Toast.makeText(activity, "Device instantiated in ${"%.3f".format(elapsedTime.toFloat() / 1000F)}s", Toast.LENGTH_LONG).show()
            }

            val spinnerList =  mutableListOf<String>()
            scardDevice = readerList

            for(i in 0 until scardDevice.slotCount) {
                spinnerList.add("$i - ${scardDevice.slots[i]}")
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

            currentSlot = scardDevice.getReader(spinnerSlots.selectedItemPosition)

            spinnerSlots.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent: AdapterView<*>?) {

                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    currentSlot = scardDevice.getReader(spinnerSlots.selectedItemPosition)
                    currentChannel = currentSlot?.channel!!
                    updateCardStatus(currentSlot!!, currentSlot?.cardPresent!!, currentSlot?.cardPowered!!)
                }
            }
            progressDialog.dismiss()
        }

        override fun onReaderListClosed(readerList: SCardReaderList) {
            mainActivity.logInfo("onReaderListClosed")
            mainActivity.backToScanFragment()
        }

        override fun onControlResponse(readerList: SCardReaderList, response: ByteArray) {
            mainActivity.logInfo("onControlResponse")

            handleRapdu(response)
        }

        override fun onPowerInfo(readerList: SCardReaderList, powerState: Int, batteryLevel: Int) {
            mainActivity.logInfo("onPowerInfo")

            /* Info dialog */
            val builder = AlertDialog.Builder(activity!!)

            builder.setTitle(deviceName)

            var deviceInfo = "Vendor: ${scardDevice.vendorName}\n" +
                    "Product: ${scardDevice.productName}\n" +
                    "Serial Number: ${scardDevice.serialNumber}\n" +
                    "FW Version: ${scardDevice.firmwareVersion}\n" +
                    "FW Version Major: ${scardDevice.firmwareVersionMajor}\n" +
                    "FW Version Minor: ${scardDevice.firmwareVersionMinor}\n" +
                    "FW Version Build: ${scardDevice.firmwareVersionBuild}\n" +
                    "Battery Level: $batteryLevel%\n"

            when (powerState) {
                0 -> deviceInfo += "Unknown source of power"
                1 -> deviceInfo += "External power supply present"
                2 -> deviceInfo += "Running on battery"
            }

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

        override fun onReaderStatus(slot: SCardReader, cardPresent: Boolean, cardPowered: Boolean) {
            mainActivity.logInfo("onReaderStatus")
            /* Is update concerning selected slot */
            if(spinnerSlots?.selectedItemPosition == slot.index) {
                updateCardStatus(slot, cardPresent, cardPowered)
            }
        }

        override fun onCardConnected(channel: SCardChannel) {
            mainActivity.logInfo("onCardConnected")
            currentChannel = channel
            textState.text = getString(R.string.connected)
            textAtr.text = channel.atr.toHexString()
        }


        override fun onCardDisconnected(channel: SCardChannel) {
            mainActivity.logInfo("onCardDisconnected")
            currentChannel = channel
            textState.text = getString(R.string.disconnected)
            textAtr.text = getString(R.string.atr)
        }

        override fun onTransmitResponse(channel: SCardChannel, response: ByteArray) {
            mainActivity.logInfo("onTransmitResponse")
            // TODO CRA check channel is current channel
            handleRapdu(response)
        }

        /* Errors callbacks */

        override fun onReaderListError(readerList: SCardReaderList, error: SCardError) {
            mainActivity.logInfo("onReaderListError")

            val text = "Error: ${error.message} \n${error.detail}"
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
            mainActivity.logInfo(text)

            progressDialog.dismiss()
            mainActivity.backToScanFragment()

        }

        override fun onReaderOrCardError(readerOrCard: Any, error: SCardError) {
            mainActivity.logInfo("onReaderOrCardError")
            rapduTextBox.text.clear()
            rapduTextBox.text.append(getString(R.string.cardMute))
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
        // handle item selection
        when (item.itemId) {
            R.id.action_info -> {
                scardDevice.getPowerInfo()
                return true
            }
            R.id.action_shutdown -> {
                scardDevice.control("58AF".hexStringToByteArray())
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setDrawerState(false)

        val toolbar = mainActivity.toolbar
        mainActivity.setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            // do something here, such as start an Intent to the parent activity.

            progressDialog.dismiss()
            scardDevice.close()
            mainActivity.backToScanFragment()
            mainActivity.drawer_layout.closeDrawer(GravityCompat.START)
        }

        nextButton.setOnClickListener {
            goToNextCommand()
        }

        prevButton.setOnClickListener {
            goToPreviousCommand()
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

            if (mainActivity.enableTimeMeasurement) {
                apduListStartTime = SystemClock.elapsedRealtime()
            }

            mainActivity.setActionBarTitle(deviceName)

            // No auto-correct
            //capduTextBox.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            transmitButton.setOnClickListener {

                mainActivity.logInfo("Click on Run APDU")

                if((!currentSlot?.cardPresent!! || !currentSlot?.cardPowered!!) &&
                    spinnerTransmitControl.selectedItemPosition == sendCommands.indexOf("Transmit")) {
                    rapduTextBox.text.clear()
                    rapduTextBox.text.append("no card")
                }
                else {

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

                        if (!line2.isEmpty()) {
                            if (line2.isHex()) {
                                cApdu.add(line2.hexStringToByteArray())
                            } else {
                                mainActivity.logInfo("Warning: $line2 is not an hexadecimal string")
                            }
                        }
                    }

                    cptApdu = 0
                    rapduTextBox.text.clear()

                    if (mainActivity.enableTimeMeasurement) {
                        apduListStartTime = SystemClock.elapsedRealtime()
                    }

                    /* Trigger 1st APDU */
                    sendApdu()
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

    abstract fun connectToDevice()

    abstract fun init(_device: Any)

    // TODO  override fun onBackPressed()

    private var apduListStartTime: Long = 0
    private var apduListStopTime: Long = 0

    private fun sendApdu() {

        mainActivity.logInfo("sendApdu")
        // TODO CRA create resource string
        if(spinnerTransmitControl.selectedItemPosition == sendCommands.indexOf("Transmit")) {
            currentChannel.transmit(cApdu[cptApdu])
        }
        else if (spinnerTransmitControl.selectedItemPosition == sendCommands.indexOf("Control")) {
            scardDevice.control(cApdu[cptApdu])
        }
        mainActivity.logInfo("<${cApdu[cptApdu].toHexString()}")
    }

    private fun handleRapdu(response: ByteArray) {
        val responseString = response.toHexString()
        mainActivity.logInfo(">$responseString")
        rapduTextBox.text.append(responseString + "\n")

        if(responseString.length >= 4)
        {
            if(responseString.takeLast(4) != "9000" && mainActivity.stopOnError) {
                mainActivity.logInfo("Stop on error : ${responseString.takeLast(4)}")
            }
            else {
                cptApdu++
                if(cptApdu < cApdu.size) {
                    sendApdu()
                }
                else {
                    if(mainActivity.enableTimeMeasurement) {
                        apduListStopTime = SystemClock.elapsedRealtime()
                        val elapsedTime = apduListStopTime - apduListStartTime
                        Toast.makeText(activity, "${cApdu.size} APDU executed in ${"%.3f".format(elapsedTime.toFloat() / 1000F)}s", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun setApduModels(models: MutableList<ApduModel>) {
        modelsApdus = models
    }


    /* handle Prev/next Apdu list history */

    private var apduSend = mutableListOf<String>()
    private var indexCommand = 0

    private fun addExecutedApdu(commandExecuted: String) {
        /* Add command if the previous one is not the same */
        if(apduSend.size > 0) {
            if (apduSend[apduSend.size - 1] != commandExecuted) {
                apduSend.add(commandExecuted)
                indexCommand = apduSend.size-1
            }
        }
        else {
            /* 1st element */
            apduSend.add(commandExecuted)
            indexCommand = apduSend.size-1
        }


        enableButton(prevButton, true)
        enableButton(nextButton, false)

    }

    private fun goToPreviousCommand() {
        enableButton(nextButton, true)

        if(indexCommand == 0) {
            enableButton(prevButton, false)
        }
        else {
            indexCommand--
            capduTextBox.setText(apduSend[indexCommand])
        }
    }

    private fun goToNextCommand() {
        enableButton(prevButton, true)

        if(indexCommand == apduSend.size-1) {
            enableButton(nextButton, false)
        }
        else {
            indexCommand++
            capduTextBox.setText(apduSend[indexCommand])
        }
    }


    private fun enableButton(button: ImageButton, enable: Boolean) {
        if(enable) {
            button.isClickable = true
            button.visibility = Button.VISIBLE
        }
        else {
            button.isClickable = false
            button.visibility = Button.INVISIBLE
        }
    }

    private fun updateCardStatus(slot: SCardReader, cardPresent: Boolean, cardPowered: Boolean) {
        if(cardPresent && !cardPowered) {
            slot.cardConnect()
            textAtr?.text = getString(R.string.atr)
            textState?.text = getString(R.string.present)
        }
        else if(cardPresent && cardPowered) {
            textAtr.text = currentSlot?.channel!!.atr.toHexString()
            textState?.text = getString(R.string.connected)
            currentChannel = slot.channel
        }
        else if(!cardPresent && !cardPowered) {
            textAtr?.text = getString(R.string.atr)
            textState?.text = getString(R.string.absent)
        }
        else{
           mainActivity.logInfo("Impossible value: card not present but powered!")
        }
    }
}
