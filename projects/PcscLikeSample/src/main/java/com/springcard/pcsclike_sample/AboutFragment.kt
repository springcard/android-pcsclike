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
import android.widget.TextView
import android.widget.TableLayout
import android.widget.TableRow
import androidx.core.content.ContextCompat
import com.springcard.pcsclike_sample.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    private lateinit var appInfo: MainActivity.ApplicationInfo
    private lateinit var libInfo: MainActivity.LibraryInfo

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle(getString(R.string.menu_about))

        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addNewLine(binding.appInfoTable,"Version Code", appInfo.VERSION_CODE.toString())
        addNewLine(binding.appInfoTable,"Version Name", appInfo.VERSION_NAME)
        addNewLine(binding.appInfoTable,"Debug", appInfo.DEBUG.toString())

        addNewLine(binding.libInfoTable,"Library Name", libInfo.libraryName)
        addNewLine(binding.libInfoTable,"Version Name", libInfo.libraryVersion)
        addNewLine(binding.libInfoTable,"Debug", libInfo.libraryDebug.toString())
    }

    private fun addNewLine(table: TableLayout, key: String, value: String) {
        val row = TableRow(activity)
        val tv1 = TextView(activity)
        val tv2 = TextView(activity)

        tv1.text =  key
        tv1.typeface = Typeface.DEFAULT_BOLD
        tv1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
        val params1 = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        tv1.layoutParams = params1

        tv2.text = value
        tv2.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
        val params2 = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        tv2.layoutParams = params2

        row.addView(tv1)
        row.addView(tv2)

        table.addView(row)
    }

    fun setAboutInfo(app: MainActivity.ApplicationInfo, lib: MainActivity.LibraryInfo) {
        appInfo = app
        libInfo = lib
    }
}
