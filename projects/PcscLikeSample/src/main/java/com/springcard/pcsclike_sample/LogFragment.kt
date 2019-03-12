/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.TypedValue
import android.view.*
import android.widget.TableRow
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_log.*
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.*


class LogFragment : Fragment() {

    private var logString = mutableListOf<String>()
    private val mainActivity  by lazy {
        activity as MainActivity
    }

    fun appendToLog(message: String) {
        logString.add(message)
    }

    override fun onCreateOptionsMenu(
        menu: Menu, inflater: MenuInflater
    ) {
        inflater.inflate(R.menu.log_app_bar, menu)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)

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

    @SuppressLint("SimpleDateFormat")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // handle item selection
        when (item.itemId) {
            R.id.send_button -> {

                val now = Date()
                val simpleDate = SimpleDateFormat("dd/MM/yyyy HH:mm")
                val date = simpleDate.format(now)
                sendMail("${getString(R.string.app_name)} Log - $date", logString.joinToString("\n"))

                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun sendMail(subject: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"// "vnd.android.cursor.dir/email"
        intent.putExtra(Intent.EXTRA_EMAIL,  arrayOf("support@springcard.com"))
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, content)
        startActivity(Intent.createChooser(intent, "Send Email"))
    }
}
