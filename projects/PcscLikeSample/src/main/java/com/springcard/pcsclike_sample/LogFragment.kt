/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.util.TypedValue
import android.view.*
import android.widget.TableRow
import android.widget.TextView
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.springcard.pcsclike_sample.R
import com.springcard.pcsclike_sample.databinding.FragmentLogBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private var logString = mutableListOf<String>()
    private val mainActivity  by lazy {
        activity as MainActivity
    }

    fun appendToLog(message: String) {
        logString.add(message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mainActivity.setActionBarTitle(getString(R.string.menu_logs))
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        for(msg in logString) {
            addNewLine(msg)
        }
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.log_app_bar, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.send_button -> {
                        val now = Date()
                        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
                        val date = formatter.format(now)
                        sendMail("${getString(R.string.local_app_name)} Log - $date", logString.joinToString("\n"))
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun addNewLine(message: String) {
        val row = TableRow(activity)
        val tv1 = TextView(activity)

        tv1.text =  message
        tv1.typeface = Typeface.MONOSPACE
        tv1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10F)
        tv1.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
        row.addView(tv1)

        binding.logTable.addView(row)
    }


    private fun sendMail(subject: String, content: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_EMAIL,  arrayOf("support@springcard.com"))
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, content)
        startActivity(Intent.createChooser(intent, "Send Email"))
    }
}
