/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.springcard.pcsclike_sample.R

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
        val viewHolder: ViewHolder
        val rowView: View

        if (convertView == null) {
            rowView = inflater.inflate(R.layout.row_device, parent, false)
            viewHolder = ViewHolder().apply {
                titleTextView = rowView.findViewById(R.id.name)
                subtitleTextView = rowView.findViewById(R.id.rssi)
                signalImageView = rowView.findViewById(R.id.signalIcon)
            }
            rowView.tag = viewHolder
        } else {
            rowView = convertView
            viewHolder = rowView.tag as ViewHolder
        }

        val device = getItem(position) as DeviceListElement
        viewHolder.titleTextView.text = device.name
        viewHolder.subtitleTextView.text = device.info.toString()

        try{
            val rssi = device.info.toInt()
            when {
                rssi < -100 -> {
                    viewHolder.signalImageView.setImageResource(R.drawable.ic_signal_cellular_0_bar)
                }
                rssi < -75 -> {
                    viewHolder.signalImageView.setImageResource(R.drawable.ic_signal_cellular_1_bar)
                }
                rssi < -50 -> {
                    viewHolder.signalImageView.setImageResource(R.drawable.ic_signal_cellular_2_bar)
                }
                rssi < -25 -> {
                    viewHolder.signalImageView.setImageResource(R.drawable.ic_signal_cellular_3_bar)
                }
                else -> {
                    viewHolder.signalImageView.setImageResource(R.drawable.ic_signal_cellular_4_bar)
                }
            }
        }
        catch (e: Exception){
            viewHolder.signalImageView.setImageResource(R.drawable.ic_signal_cellular_4_bar)
        }


        return rowView
    }
}

private class ViewHolder {
    lateinit var titleTextView: TextView
    lateinit var subtitleTextView: TextView
    lateinit var signalImageView: ImageView
}