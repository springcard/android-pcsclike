/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class DeviceListAdapter(private val context: Context,
                        private val dataSource: ArrayList<DeviceListElement>) : BaseAdapter() {

    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getCount(): Int {
        return dataSource.size
    }

    override fun getItem(position: Int): Any {
        return dataSource[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // Get view for row item
        val rowView = inflater.inflate(R.layout.row_device, parent, false)

        // Get title element
        val titleTextView = rowView.findViewById(R.id.name) as TextView

        // Get subtitle element
        val subtitleTextView = rowView.findViewById(R.id.rssi) as TextView

        val device = getItem(position) as DeviceListElement

        titleTextView.text = device.name
        subtitleTextView.text = device.info.toString()

        return rowView
    }
}