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
import java.util.ArrayList
import android.view.*
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.springcard.pcsclike.communication.*
import com.springcard.pcsclike_sample.*
import com.springcard.pcsclike_sample_ble.databinding.FragmentScanBinding
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.springcard.pcsclike_sample.ScanFragment
import com.springcard.pcsclike_sample.R

class ScanFragment : ScanFragment() {


    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private var deviceList = ArrayList<DeviceListElement>()
    private var adapter: DeviceListAdapter? = null
    private var bleDeviceList = ArrayList<BluetoothDevice>()
    private var mScanning: Boolean = false
    private var mBluetoothScanner: BluetoothLeScanner? = null

    private val mBluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = mainActivity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val android.bluetooth.BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        mainActivity = activity as MainActivity
        mainActivity.setActionBarTitle(resources.getString(R.string.menu_device_list))


        /* Check if device  support BLE */
        mainActivity.packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            /* Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()*/
        }

        checkAndRequestPermissions()

        /* Set up BLE */
        val REQUEST_ENABLE_BT = 6
        /* Ensures Bluetooth is available on the device and it is enabled. If not, */
        /* displays a dialog requesting user permission to enable Bluetooth. */
        mBluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
        mBluetoothScanner = mBluetoothAdapter?.bluetoothLeScanner

        /* Inflate the layout for this fragment */
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity.setDrawerState(true)

        /* Create ListView */
        adapter = DeviceListAdapter(this.requireContext(), deviceList)
        binding.deviceListView.adapter = adapter

        /* Click on item of ListView */
        binding.deviceListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            //mainActivity.logInfo("Device ${bleDeviceList[position].name} selected")
            mainActivity.goToDeviceFragment(bleDeviceList[position])
        }

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(com.springcard.pcsclike_sample_ble.R.menu.scan_app_bar, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    com.springcard.pcsclike_sample_ble.R.id.scan_button -> {
                        deviceList.clear()
                        bleDeviceList.clear()
                        adapter?.notifyDataSetChanged()
                        scanLeDevice(true)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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

                    val scanFilterS320Bonded = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_S320_BONDED_SERVICE))
                        .build()

                    val scanFilterS320Plain = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_S320_PLAIN_SERVICE))
                        .build()

                    val scanFilterS370Bounded = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_S370_BONDED_SERVICE))
                        .build()

                    val scanFilterS370Plain = ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_S370_PLAIN_SERVICE))
                        .build()

                    scanFilters.add(scanFilterD600)
                    scanFilters.add(scanFilterSpringCorePlain)
                    scanFilters.add(scanFilterSpringCoreBonded)

                    scanFilters.add(scanFilterS320Bonded)
                    scanFilters.add(scanFilterS320Plain)
                    scanFilters.add(scanFilterS370Bounded)
                    scanFilters.add(scanFilterS370Plain)

                } catch (e: Exception) {
                    Log.e(TAG, e.message.toString())
                }

                /* Reset devices list anyway */
                deviceList.clear()
                bleDeviceList.clear()
                adapter?.notifyDataSetChanged()

                if (!mScanning) {
                    mScanning = true

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                            return
                        }
                    }
                    else{
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            return
                        }
                    }

                    mBluetoothScanner?.startScan(scanFilters, settingsBuilt, mLeScanCallback)
                    binding.progressBarScanning?.visibility = ProgressBar.VISIBLE
                    mainActivity.logInfo("Scan started")
                }
            }
            else -> {
                mScanning = false
                if(mBluetoothAdapter?.isEnabled!!)
                {
                    mBluetoothScanner?.stopScan(mLeScanCallback)
                }
                binding.progressBarScanning.visibility = ProgressBar.GONE
                mainActivity.logInfo("Scan stopped")
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }

            if (allPermissionsGranted) {
                // Toutes les permissions ont été accordées
                // Vous pouvez maintenant procéder avec les opérations nécessitant les permissions
            } else {
                // Au moins une permission a été refusée
                // Gérez le cas où les permissions ne sont pas accordées
            }
        }


    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Pour Android 12 (API niveau 31) et versions ultérieures
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        } else {
            // Pour Android 6 (Marshmallow, API niveau 23) à Android 11 (API niveau 30)
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Vérifier si les permissions sont déjà accordées
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            // Demander les permissions manquantes
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Toutes les permissions nécessaires sont déjà accordées
            // Continuez avec les opérations nécessitant les permissions
        }
    }


    override fun onPause() {
        super.onPause()
        /* Stop scan when leaving */
        scanLeDevice(false)
    }

    override fun onResume() {
        super.onResume()
        deviceList.clear()
        bleDeviceList.clear()
        adapter?.notifyDataSetChanged()

        /* Connect to specific device automatically */
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
