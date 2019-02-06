/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverble

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import com.springcard.pscblelib.BuildConfig
import kotlinx.android.synthetic.main.fragment_about.*
import android.widget.TextView
import android.widget.TableLayout
import android.widget.TableRow


class AboutFragment : Fragment() {

    private var listener: OnFragmentInteractionListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle("About")

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addNewLine(appInfoTable,"Version Code", com.springcard.pcscoverble.BuildConfig.VERSION_CODE.toString())
        addNewLine(appInfoTable,"Version Name", com.springcard.pcscoverble.BuildConfig.VERSION_NAME)
        addNewLine(appInfoTable,"Debug", com.springcard.pcscoverble.BuildConfig.DEBUG.toString())

        addNewLine(libInfoTable,"Library Name", BuildConfig.libraryName)
        addNewLine(libInfoTable,"Version Name", BuildConfig.libraryVersion)
        addNewLine(libInfoTable,"Debug", BuildConfig.libraryDebug.toString())
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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
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
