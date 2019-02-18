/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscapp

import android.os.Bundle
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Switch
import android.widget.TextView
import com.springcard.pcsclike.toHexString
import kotlinx.android.synthetic.main.fragment_options.*


class OptionsFragment : Fragment(), TextWatcher {

    private lateinit var  mainActivity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        setHasOptionsMenu(false)

        mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle("Options")

        /* Inflate the layout for this fragment */
        return inflater.inflate(R.layout.fragment_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setDrawerState(true)

        switchLog.isChecked = mainActivity.enableLog
        switchStopOnError.isChecked = mainActivity.stopOnError
        switchEnableTimeMeasurment.isChecked = mainActivity.enableTimeMeasurement

        switchLog.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.enableLog = isChecked
            mainActivity.logInfo("Enable logs = $isChecked")
        }

        switchStopOnError.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.stopOnError = isChecked
            mainActivity.logInfo("Stop on error = $isChecked")
        }

        switchEnableTimeMeasurment.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.enableTimeMeasurement = isChecked
            mainActivity.logInfo("Enable time measurement = $isChecked")
        }

        if(mainActivity.supportCrypto) {

            /* Initial state */
            editTextAuthenticationKey.isEnabled = mainActivity.useAuthentication
            editTextAuthenticationKey.setText(mainActivity.authenticationKey, TextView.BufferType.EDITABLE)
            if(editTextAuthenticationKey.text.length != 32) {
                editTextAuthenticationKey.error = "The key must be 16 bytes long"
            }
            editTextAuthenticationKey.addTextChangedListener(this)

            switchUseAuthentication.setOnCheckedChangeListener { _, isChecked ->
                mainActivity.useAuthentication = isChecked
                mainActivity.logInfo("Enable authentication = $isChecked")

                /* Enable or not key text box */
                editTextAuthenticationKey.isEnabled = isChecked
            }

        }
        else {
            switchUseAuthentication.isEnabled = false
            switchUseAuthentication.visibility = Switch.INVISIBLE

            editTextAuthenticationKey.isEnabled = false
            editTextAuthenticationKey.visibility = Switch.INVISIBLE
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun afterTextChanged(s: Editable?) {
        if(editTextAuthenticationKey.text.length != 32) {
            editTextAuthenticationKey.error = "The key must be 16 bytes long"
        }
        mainActivity.authenticationKey = editTextAuthenticationKey.text.toString()
    }
}
