/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverble

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import android.widget.ToggleButton
import kotlinx.android.synthetic.main.fragment_options.*


class OptionsFragment : Fragment() {

    private var listener: OnFragmentInteractionListener? = null
    private lateinit var  mainActivity: MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle("Options")

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_options, container, false)
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
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


    override fun onStart() {
        super.onStart()

        try {
            listener = activity as OnFragmentInteractionListener?
        } catch (e: ClassCastException) {
            throw ClassCastException(activity!!.toString() + " must implement OnFragmentInteractionListener")
        }

    }


    override fun onDetach() {
        super.onDetach()
        listener = null
    }


    interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onFragmentInteraction(uri: Uri)
    }

}
