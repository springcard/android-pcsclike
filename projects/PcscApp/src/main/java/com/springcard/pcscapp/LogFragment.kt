/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscapp

import android.graphics.Typeface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.TypedValue
import android.view.*
import android.widget.TableRow
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_log.*


class LogFragment : Fragment() {

    private var logString = mutableListOf<String>()

    fun appendToLog(message: String) {
        logString.add(message)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(false)

        val mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle("Log")

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        for(msg in logString) {
            addNewLine(msg)
        }
    }

    private fun addNewLine(message: String) {
        val row = TableRow(activity)
        val tv1 = TextView(activity)

        // cell 1
        tv1.text =  message
        tv1.typeface = Typeface.MONOSPACE
        tv1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10F)
        row.addView(tv1)

        // add row to table
        logTable.addView(row)
    }
}
