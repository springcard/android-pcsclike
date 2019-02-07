/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscapp

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import kotlinx.android.synthetic.main.fragment_options.*


class OptionsFragment : Fragment() {

    private lateinit var  mainActivity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        setHasOptionsMenu(false)

        mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle("Options")

        // Inflate the layout for this fragment
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
    }
}
