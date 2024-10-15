/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.os.Bundle
import com.google.android.material.textfield.TextInputLayout
import androidx.fragment.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import com.springcard.pcsclike_sample.databinding.FragmentOptionsBinding


class OptionsFragment : Fragment() {

    private var _binding: FragmentOptionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var  mainActivity: MainActivity
    private val indexkey = listOf("User", "Admin")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle(getString(R.string.menu_options))
        _binding = FragmentOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setDrawerState(true)

        binding.switchLog.isChecked = mainActivity.preferences.enableLog
        binding.switchStopOnError.isChecked = mainActivity.preferences.stopOnError
        binding.switchEnableTimeMeasurement.isChecked = mainActivity.preferences.enableTimeMeasurement

        binding.switchLog.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.preferences.enableLog = isChecked
            mainActivity.logInfo("Enable logs = $isChecked")
        }

        binding.switchStopOnError.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.preferences.stopOnError = isChecked
            mainActivity.logInfo("Stop on error = $isChecked")
        }

        binding.switchEnableTimeMeasurement.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.preferences.enableTimeMeasurement = isChecked
            mainActivity.logInfo("Enable time measurement = $isChecked")
        }

        val dataAdapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            indexkey
        )
        (binding.spinnerKeyIndex as? AutoCompleteTextView)?.apply {
            setAdapter(dataAdapter)
            setText(dataAdapter.getItem(mainActivity.preferences.authenticationKeyIndex), false)
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                mainActivity.preferences.authenticationKeyIndex = position
            }
        }

        if(mainActivity.supportCrypto) {

            binding.switchUseAuthentication.isChecked = mainActivity.preferences.useAuthentication

            binding.editTextAuthenticationKey.setText(mainActivity.preferences.authenticationKey, TextView.BufferType.EDITABLE)
            if(binding.editTextAuthenticationKey.text?.length != 32) {
                binding.editTextAuthenticationKey.error = getString(R.string.error_key_length)
            }
            binding.editTextAuthenticationKey.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable?) {
                    if(binding.editTextAuthenticationKey.text?.length != 32) {
                        binding.editTextAuthenticationKey.error = getString(R.string.error_key_length)
                    }
                    else {
                        mainActivity.preferences.authenticationKey = binding.editTextAuthenticationKey.text.toString()
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            binding.spinnerKeyIndex.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    mainActivity.preferences.authenticationKeyIndex = position
                }
            }

            setAuthenticationFieldsEnabled(binding.switchUseAuthentication.isChecked)

        }
        else {
            binding.switchUseAuthentication.isEnabled = false
            binding.switchUseAuthentication.visibility = Switch.INVISIBLE
            setAuthenticationFieldsEnabled(false)
        }

        binding.switchUseAuthentication.setOnCheckedChangeListener { _, isChecked ->
            mainActivity.preferences.useAuthentication = isChecked
            mainActivity.logInfo("Enable authentication = $isChecked")

            setAuthenticationFieldsEnabled(isChecked)
        }
    }

    private fun setAuthenticationFieldsEnabled(isEnabled: Boolean) {
        val visibility = if (isEnabled) View.VISIBLE else View.INVISIBLE
        with(binding) {
            editTextAuthenticationKey.isEnabled = isEnabled
            keyWrapper.isEnabled = isEnabled
            spinnerKeyIndex.isEnabled = isEnabled
            textViewKeyIndex.isEnabled = isEnabled

            editTextAuthenticationKey.visibility = visibility
            keyWrapper.visibility = visibility
            spinnerLayout.visibility = visibility
            spinnerKeyIndex.visibility = visibility
            textViewKeyIndex.visibility = visibility
        }
    }
}
