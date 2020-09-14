/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample_usb

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import java.util.ArrayList
import android.view.*
import android.widget.AdapterView
import com.springcard.pcsclike_sample.*
import kotlinx.android.synthetic.main.fragment_scan.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log
import android.widget.Toast
import org.xmlpull.v1.XmlPullParser


@Suppress("IMPLICIT_CAST_TO_ANY")
class ScanFragment : com.springcard.pcsclike_sample.ScanFragment() {

    private var deviceList = ArrayList<DeviceListElement>()
    private var adapter: DeviceListAdapter? = null
    private var usbDeviceList = ArrayList<UsbDevice>()

    /* List containing all VID/PID of authorized devices */
    private val deviceFilter = mutableListOf<String>()

    private val usbManager: UsbManager by lazy {
        mainActivity.getSystemService(Context.USB_SERVICE) as UsbManager
    }
    private val mPermissionIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(mainActivity, 0, Intent(ACTION_USB_PERMISSION), 0)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        setHasOptionsMenu(true)
        mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle(resources.getString(R.string.device_list))

        /* Check if device  support USB */
        mainActivity.packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_USB_HOST) }?.also {
            Toast.makeText(mainActivity, R.string.usb_host_not_supported, Toast.LENGTH_SHORT).show()
        }

        val mUsbAttachReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val usbDevice =  intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as UsbDevice

                when {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action -> addDevice(usbDevice)
                    UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action -> removeDevice(usbDevice)
                    ACTION_USB_PERMISSION == intent.action -> synchronized(this) {

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            usbDevice.apply {
                                /* call method to set up device communication */
                                mainActivity.goToDeviceFragment(usbDevice)
                            }

                        } else {
                            Log.d(TAG, "Permission denied for device $usbDevice")
                        }
                    }
                }
            }
        }

        mainActivity.applicationContext.registerReceiver(mUsbAttachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))
        mainActivity.applicationContext.registerReceiver(mUsbAttachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
        mainActivity.applicationContext.registerReceiver(mUsbAttachReceiver, IntentFilter(ACTION_USB_PERMISSION))

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setDrawerState(true)

        /* Parse device_filter.xml */
        val xmlResourceParser = context!!.resources.getXml(R.xml.device_filter)
        var eventType = xmlResourceParser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if(xmlResourceParser.name == "usb-device") {
                        val vid = xmlResourceParser.getAttributeIntValue(1, 0)
                        val pid = xmlResourceParser.getAttributeIntValue(0, 0)
                        if(pid!=0 && vid !=0) {
                            deviceFilter.add(getDeviceIdsAsString(vid, pid))
                        }
                    }
                }
            }
            eventType = xmlResourceParser.next()
        }


        /* Create ListView */
        adapter = DeviceListAdapter(mainActivity.applicationContext, deviceList)
        device_list_view.adapter = adapter

        /* Click on item of ListView */
        device_list_view.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            mainActivity.logInfo("Device ${usbDeviceList[position].deviceName} selected")
            usbManager.requestPermission(usbDeviceList[position], mPermissionIntent)
        }

        /* Request permission */

        /* load device list  from XML file (the same used by the intent part */
        /* val compliantDevice = ArrayList<Int>()
         try {
             val devicesFromResources = context?.resources?.getXml(R.xml.device_filter)
             devicesFromResources!!.next()
             var eventType = devicesFromResources.eventType
             while (eventType != XmlPullParser.END_DOCUMENT) {
                 if (eventType == XmlPullParser.START_TAG) {
                     if (devicesFromResources.name == "usb-device") {
                         /* if vendor id is SpringCard */
                         if (devicesFromResources.getAttributeIntValue(0, 0) == spVendorId) {
                             compliantDevice.add(devicesFromResources.getAttributeIntValue(1, 0))
                         }
                     }
                 }
                 eventType = devicesFromResources.next()
             }

         } catch (e1: XmlPullParserException) {
             e1.printStackTrace()
         } catch (e1: IOException) {
             e1.printStackTrace()
         }
 */
    }


    override fun onResume() {
        super.onResume()
        deviceList.clear()
        usbDeviceList.clear()
        adapter?.notifyDataSetChanged()

        /* Scan USB devices already present */
        val usbManager = mainActivity.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
        deviceList.values.forEach { device -> addDevice(device) }
    }

    private fun addDevice(device: UsbDevice) {
        val newItem = DeviceListElement("${device.manufacturerName} ${device.productName}", getDeviceExtraInfo(device))

        val deviceIdentifier = getDeviceIdsAsString(device.vendorId, device.productId)
        if(!deviceFilter.contains(deviceIdentifier)) {
            Log.d(TAG, "Device is not in device filter list (not a SpringCard device?)")
        }

        if (!deviceList.contains(newItem)) {
            deviceList.add(newItem)
            usbDeviceList.add(device)
            adapter?.notifyDataSetChanged()
            mainActivity.logInfo("New device found: ${device.manufacturerName} ${device.productName} ${getDeviceExtraInfo(device)} (Thread = ${Thread.currentThread().name})")
        }
    }

    private fun removeDevice(device: UsbDevice) {
        val item = DeviceListElement("${device.manufacturerName} ${device.productName}", getDeviceExtraInfo(device))

        if (deviceList.contains(item)) {
            deviceList.remove(item)
            usbDeviceList.remove(device)
            adapter?.notifyDataSetChanged()
            mainActivity.logInfo("Device removed: ${device.manufacturerName} ${device.productName} ${getDeviceExtraInfo(device)}")
        }
    }

    fun getDeviceExtraInfo(device: UsbDevice): String {
        return if(device.serialNumber != null) {
            device.serialNumber!!
        } else {
            device.deviceId.toString()
        }
    }

    private fun getDeviceIdsAsString(vid: Int, pid: Int): String {
        return "0x${vid.toString(16).toUpperCase().padStart(2, '0')}|0x${pid.toString(16).toUpperCase().padStart(2, '0')}"
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    }
}
