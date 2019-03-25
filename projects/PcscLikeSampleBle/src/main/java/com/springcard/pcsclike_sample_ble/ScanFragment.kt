/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample_ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.AdapterView
import kotlinx.android.synthetic.main.fragment_scan.*
import java.util.ArrayList
import android.view.*
import android.widget.ProgressBar
import com.springcard.pcsclike.communication.*
import com.springcard.pcsclike_sample.*

class ScanFragment : com.springcard.pcsclike_sample.ScanFragment() {

    private var deviceList = ArrayList<DeviceListElement>()
    private var adapter: DeviceListAdapter? = null
    private var bleDeviceList = ArrayList<BluetoothDevice>()
    private var mScanning: Boolean = false
    private var mBluetoothScanner: BluetoothLeScanner? = null

    private val android.bluetooth.BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        setHasOptionsMenu(true)
        mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle(resources.getString(R.string.device_list))


        /* Check if device  support BLE */
        mainActivity.packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            /* Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()*/
        }

        /* Location permission */

        /* var lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
         var gps_enabled = false
         var network_enabled = false

         try {
             gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
         } catch(ex: Exception) {}

         try {
             network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
         } catch(ex: Exception) {}

         if(!gps_enabled && !network_enabled) {
             // notify user
             var dialog = AlertDialog.Builder(this)
             dialog.setMessage(resources.getString(R.string.gps_network_not_enabled))
             dialog.setPositiveButton(resources.getString(R.string.open_location_settings),  DialogInterface.OnClickListener {

                 fun onClick(paramDialogInterface: DialogInterface , paramInt: Int ) {
                     // TODO Auto-generated method stub
                     var myIntent: Intent = Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                     startActivity(myIntent)
                 }
             })
             dialog.setNegativeButton(getString(R.string.Cancel), DialogInterface.OnClickListener() {
                 fun onClick(paramDialogInterface: DialogInterface , paramInt: Int ) {
                         // TODO Auto-generated method stub
                 }
             })
             dialog.show()
         }*/

        // TODO CRA : check if location already activated
        /* val intent: Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
         startActivity(intent)*/

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val PERMISSION_REQUEST_COARSE_LOCATION = 5
            /* Android M Permission check */
            if (mainActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSION_REQUEST_COARSE_LOCATION
                )
            }
        }

        //------------------------------------------------------------------------------------------------------

        /* Set up BLE */

        /* Bluetooth Adapter */
        val mBluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
            val bluetoothManager = mainActivity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        }
        val REQUEST_ENABLE_BT = 6
        /* Ensures Bluetooth is available on the device and it is enabled. If not, */
        /* displays a dialog requesting user permission to enable Bluetooth. */
        mBluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        mBluetoothScanner = mBluetoothAdapter?.bluetoothLeScanner

        /* Inflate the layout for this fragment */
        return inflater.inflate(R.layout.fragment_scan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setDrawerState(true)

        /* Create ListView */
        adapter = DeviceListAdapter(this.context!!, deviceList)
        device_list_view.adapter = adapter

        /* Click on item of ListView */
        device_list_view.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            mainActivity.logInfo("Device ${bleDeviceList[position].name} selected")
            mainActivity.goToDeviceFragment(bleDeviceList[position])
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // handle item selection
        when (item.itemId) {
            R.id.scan_button -> {
                scanLeDevice(true)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }


    override fun onCreateOptionsMenu(
        menu: Menu, inflater: MenuInflater
    ) {
        inflater.inflate(com.springcard.pcsclike_sample_ble.R.menu.scan_app_bar, menu)
    }


    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                /* Scan settings */
                val settings = ScanSettings.Builder()
                settings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //  settings.setCallbackType(ScanSettings.CALLBACK_TYPE_MATCH_LOST)
                //}
                val settingsBuilt = settings.build()

                /* Filter for SpringCard service */
                val scanFilters = ArrayList<ScanFilter>()
                try {
                    val scanFilterD600 = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesD600.UUID_SPRINGCARD_RFID_SCAN_PCSC_LIKE_SERVICE))
                        .build()
                    val scanFilterSpringCorePlain = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_CCID_PLAIN_SERVICE)).build()
                    val scanFilterSpringCoreBonded = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_CCID_BONDED_SERVICE))
                        .build()
                    scanFilters.add(scanFilterD600)
                    scanFilters.add(scanFilterSpringCorePlain)
                    scanFilters.add(scanFilterSpringCoreBonded)
                } catch (e: Exception) {
                    Log.e(TAG, e.message)
                }

                /* Reset devices list anyway */
                deviceList.clear()
                bleDeviceList.clear()
                adapter?.notifyDataSetChanged()

                if (!mScanning) {
                    mScanning = true
                    mBluetoothScanner?.startScan(scanFilters, settingsBuilt, mLeScanCallback)
                    progressBarScanning?.visibility = ProgressBar.VISIBLE
                    mainActivity.logInfo("Scan started")
                }
            }
            else -> {
                mScanning = false
                mBluetoothScanner?.stopScan(mLeScanCallback)
                progressBarScanning?.visibility = ProgressBar.GONE
                mainActivity.logInfo("Scan stopped")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        scanLeDevice(false)
    }

    override fun onResume() {
        super.onResume()
        deviceList.clear()
        bleDeviceList.clear()
        adapter?.notifyDataSetChanged()


       /* val mBluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
            val bluetoothManager = mainActivity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        }

        var deviceDummy = mBluetoothAdapter!!.getRemoteDevice("00:0D:6F:47:3D:6A")

        mainActivity.goToDeviceFragment(deviceDummy)*/

        /* Begin scan directly when arriving to this fragment */
        scanLeDevice(true)
    }

    private val mLeScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val newItem: DeviceListElement = if (result.scanRecord!!.deviceName != null) {
                DeviceListElement(result.scanRecord!!.deviceName!!, result.rssi.toString())
            } else {
                DeviceListElement(result.device.address, result.rssi.toString())
            }

            if(callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                if (!deviceListContains(newItem)) {
                    deviceList.add(newItem)
                    bleDeviceList.add(result.device)
                    adapter?.notifyDataSetChanged()
                    mainActivity.logInfo("New device found: ${newItem.name}")
                }
            }
            else if(callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
                removeFromDeviceList(newItem)
                bleDeviceList.remove(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }

    private fun deviceListContains(device: DeviceListElement): Boolean {
        for (item in deviceList) {
            if (device.name == item.name)
                return true
        }
        return false
    }

    private fun removeFromDeviceList(device: DeviceListElement) {
        var itemToRemove: DeviceListElement? = null
        for (item in deviceList) {
            if (device.name == item.name) {
                itemToRemove = item
            }
        }

        if(itemToRemove != null) {
            deviceList.remove(itemToRemove)

            adapter?.notifyDataSetChanged()
        }
    }
}
