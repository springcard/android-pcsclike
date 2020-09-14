/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.graphics.Typeface
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import kotlinx.android.synthetic.main.fragment_about.*
import android.widget.TextView
import android.widget.TableLayout
import android.widget.TableRow


class AboutFragment : Fragment() {

    private lateinit var appInfo: MainActivity.ApplicationInfo
    private lateinit var libInfo: MainActivity.LibraryInfo

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(false)
        val mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle("About")

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addNewLine(appInfoTable,"Version Code", appInfo.VERSION_CODE.toString())
        addNewLine(appInfoTable,"Version Name", appInfo.VERSION_NAME)
        addNewLine(appInfoTable,"Debug", appInfo.DEBUG.toString())

        addNewLine(libInfoTable,"Library Name", libInfo.libraryName)
        addNewLine(libInfoTable,"Version Name", libInfo.libraryVersion)
        addNewLine(libInfoTable,"Debug", libInfo.libraryDebug.toString())
    }

    private fun addNewLine(table: TableLayout, key: String, value: String) {
        val row = TableRow(activity)
        val tv1 = TextView(activity)
        val tv2 = TextView(activity)

        // cell 1
        tv1.text =  key
        tv1.typeface = Typeface.DEFAULT_BOLD

        // cell 2
        tv2.text = value

        // add cell to row
        row.addView(tv1)
        row.addView(tv2)

        // add row to table
        table.addView(row)
    }

    fun setAboutInfo(app: MainActivity.ApplicationInfo, lib: MainActivity.LibraryInfo) {
        appInfo = app
        libInfo = lib
    }
}
