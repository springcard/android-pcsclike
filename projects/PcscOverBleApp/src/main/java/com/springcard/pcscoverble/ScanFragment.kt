/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverble

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
import android.os.Handler
import android.os.ParcelUuid
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.AdapterView
import com.springcard.pcscblelib.GattAttributesD600
import com.springcard.pcscblelib.GattAttributesSpringCore
import kotlinx.android.synthetic.main.fragment_scan.*
import java.util.ArrayList
import android.view.*
import android.widget.ProgressBar
import com.springcard.pcscapp.*

class ScanFragment : com.springcard.pcscapp.ScanFragment() {

    /* System support BLE ? */
    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

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
        mainActivity.setActionBarTitle("Scan")


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
            // Android M Permission check
            if (mainActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                val builder = AlertDialog.Builder(mainActivity)
                builder.setTitle("This app needs location access")
                builder.setMessage("Please grant location access so this app can detect beacons.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                        PERMISSION_REQUEST_COARSE_LOCATION
                    )
                }
                builder.show()
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
        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        mBluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            //Toast.makeText(applicationContext, R.string.ble_disabled, Toast.LENGTH_SHORT).show()
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        mBluetoothScanner = mBluetoothAdapter?.bluetoothLeScanner

        // Inflate the layout for this fragment
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
        inflater.inflate(com.springcard.pcscoverble.R.menu.scan_app_bar, menu)
    }


    private fun scanLeDevice(enable: Boolean) {
        var mHandler = Handler()
        when (enable) {
            true -> {
                /* filter for SpringCard service */
                val scanFilters = ArrayList<ScanFilter>()
                //default setting.
                val settings = ScanSettings.Builder().build()
                try {
                    val scanFilterD600 = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesD600.UUID_SPRINGCARD_RFID_SCAN_PCSC_LIKE_SERVICE)).build()
                    val scanFilterSpringCorePlain = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_CCID_PLAIN_SERVICE)).build()
                    val scanFilterSpringCoreBonded = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_CCID_BONDED_SERVICE)).build()
                    scanFilters.add(scanFilterD600)
                    scanFilters.add(scanFilterSpringCorePlain)
                    scanFilters.add(scanFilterSpringCoreBonded)
                } catch (e: Exception) {
                    Log.e(TAG, e.message)
                }


                // Stops scanning after a pre-defined scan period.
                mHandler.postDelayed({
                    mScanning = false
                    mBluetoothScanner?.stopScan(mLeScanCallback)
                    //activity?.buttonScan?.text = getString(R.string.startScan)
                    progressBarScanning?.visibility = ProgressBar.GONE
                    mainActivity.logInfo("Scan stopped after ${SCAN_PERIOD/1000}s")
                }, SCAN_PERIOD)

                if(!mScanning) {
                    mScanning = true
                    mBluetoothScanner?.startScan(scanFilters, settings, mLeScanCallback)
                    //activity?.buttonScan?.text = getString(R.string.stopScan)
                    progressBarScanning?.visibility = ProgressBar.VISIBLE
                    deviceList.clear()
                    bleDeviceList.clear()
                    adapter?.notifyDataSetChanged()
                    mainActivity.logInfo("Scan started")
                }
            }
            else -> {
                mScanning = false
                mBluetoothScanner?.stopScan(mLeScanCallback)
                //activity?.buttonScan?.text = getString(R.string.startScan)
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

        // Begin scan directly when arriving to this fragment
        scanLeDevice(true)
    }

    private val mLeScanCallback = object : ScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val newItem: DeviceListElement = if (result.scanRecord!!.deviceName != null) {
                DeviceListElement(result.scanRecord!!.deviceName, result.rssi)
            } else {
                DeviceListElement(result.device.address, result.rssi)
            }

            if (!deviceListContains(newItem)) {
                deviceList.add(newItem)
                bleDeviceList.add(result.device)
                adapter?.notifyDataSetChanged()
                mainActivity.logInfo("New device found: ${newItem.name}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }

    private fun deviceListContains(device: DeviceListElement): Boolean {

        // Loop over argument list.
        for (item in deviceList) {
            if (device.name == item.name)
                return true
        }
        return false
    }

    companion object {
        private const val SCAN_PERIOD: Long = 10000
    }

}
