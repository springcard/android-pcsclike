/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import kotlinx.android.synthetic.main.fragment_options.*


class OptionsFragment : Fragment(), TextWatcher {

    private lateinit var  mainActivity: MainActivity
    private val indexkey = listOf("User", "Admin")

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

        switchLog.isChecked = mainActivity.preferences.enableLog
        switchStopOnError.isChecked = mainActivity.preferences.stopOnError
        switchEnableTimeMeasurement.isChecked = mainActivity.preferences.enableTimeMeasurement

        switchLog.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.preferences.enableLog = isChecked
            mainActivity.logInfo("Enable logs = $isChecked")
        }

        switchStopOnError.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.preferences.stopOnError = isChecked
            mainActivity.logInfo("Stop on error = $isChecked")
        }

        switchEnableTimeMeasurement.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.preferences.enableTimeMeasurement = isChecked
            mainActivity.logInfo("Enable time measurement = $isChecked")
        }

        val dataAdapter = ArrayAdapter<String>(
            activity?.applicationContext!!,
            android.R.layout.simple_spinner_item, indexkey
        )
        // Drop down layout style - list view
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerKeyIndex.adapter = dataAdapter

        spinnerKeyIndex.setSelection(mainActivity.preferences.authenticationKeyIndex)

        if(mainActivity.supportCrypto) {

            /* Initial state */
            editTextAuthenticationKey.isEnabled = mainActivity.preferences.useAuthentication
            editTextAuthenticationKey.setText(mainActivity.preferences.authenticationKey, TextView.BufferType.EDITABLE)
            if(editTextAuthenticationKey.text.length != 32) {
                editTextAuthenticationKey.error = "The key must be 16 bytes long"
            }
            editTextAuthenticationKey.addTextChangedListener(this)

            switchUseAuthentication.isChecked = mainActivity.preferences.useAuthentication

            spinnerKeyIndex.isEnabled = mainActivity.preferences.useAuthentication
            textViewKeyIndex.isEnabled = mainActivity.preferences.useAuthentication

            /* On key index changed*/
            spinnerKeyIndex.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    mainActivity.preferences.authenticationKeyIndex = position
                }
            }

            /* On key value changed */
            switchUseAuthentication.setOnCheckedChangeListener { _, isChecked ->
                mainActivity.preferences.useAuthentication = isChecked
                mainActivity.logInfo("Enable authentication = $isChecked")

                /* Enable or not key text box */
                editTextAuthenticationKey.isEnabled = isChecked
                spinnerKeyIndex.isEnabled = isChecked
                textViewKeyIndex.isEnabled = isChecked
            }

        }
        else {
            switchUseAuthentication.isEnabled = false
            switchUseAuthentication.visibility = Switch.INVISIBLE

            editTextAuthenticationKey.isEnabled = false
            editTextAuthenticationKey.visibility = Switch.INVISIBLE

            keyWrapper.isEnabled = false
            keyWrapper.visibility = TextInputLayout.INVISIBLE

            spinnerKeyIndex.isEnabled = false
            spinnerKeyIndex.visibility = Spinner.INVISIBLE

            textViewKeyIndex.isEnabled = false
            textViewKeyIndex.visibility = TextView.INVISIBLE
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
        mainActivity.preferences.authenticationKey = editTextAuthenticationKey.text.toString()
    }
}
