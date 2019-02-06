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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import android.support.design.widget.NavigationView
import android.support.v4.widget.DrawerLayout
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import com.springcard.pscblelib.BuildConfig
import org.json.JSONArray


class MainActivity  :  AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    OptionsFragment.OnFragmentInteractionListener,
    ScanFragment.OnFragmentInteractionListener,
    DeviceFragment.OnFragmentInteractionListener,
    AboutFragment.OnFragmentInteractionListener,
    LogFragment.OnFragmentInteractionListener {

    override fun onFragmentInteraction(uri: Uri) {
        Log.d(TAG, "test")
       // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /* System support BLE ? */
    private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val TAG = this::class.java.simpleName
    private val scanFragment = ScanFragment()
    private val optionsFragment = OptionsFragment()
    private val deviceFragment = DeviceFragment()
    private val aboutFragment = AboutFragment()
    private val logFragment = LogFragment()

    private lateinit var drawerToggle: ActionBarDrawerToggle


    var enableLog = true
    var stopOnError = false
    var enableTimeMeasurement = false

    // Store the start time
    var startTime = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        logInfo("Application started")

        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.add(R.id.fragment_container, scanFragment)
        fragmentTransaction.commit()
        nav_view.setCheckedItem(R.id.nav_scan)

        /* Check if device  support BLE */
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
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
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                val builder = AlertDialog.Builder(this)
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

        /* Set up BLE */

        /* Bluetooth Adapter */
        val mBluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
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


        //------------------------------------------------------------------------------------------------------

        /* Get Apdu model List */
        // Instantiate the RequestQueue.
        val queue = Volley.newRequestQueue(this)
        val url = "http://models.springcard.com/api/models"
        var modelsApdus: MutableList<ApduModel>

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                // Display the first 500 characters of the response string.
                var jsonArray = JSONArray(response.toString())
                var gson =  GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssX").create()

                if (jsonArray.length() > 0) {
                    logInfo("Nb of apdu examples in model = ${jsonArray.length()}" )
                    modelsApdus = gson.fromJson(jsonArray.toString(), Array<ApduModel>::class.java).toMutableList()
                    deviceFragment.setApduModels(modelsApdus)
                }


            },
            Response.ErrorListener {
                /* Disable spinner */
                // TODO
                //spinnerModels.isEnabled = false
            })

        // Add the request to the RequestQueue.
        queue.add(stringRequest)

        logInfo("Lib rev = ${BuildConfig.VERSION_NAME}")
        logInfo("App rev = ${com.springcard.pcscoverble.BuildConfig.VERSION_NAME}")
    }

    fun setDrawerState(isEnabled: Boolean) {
        if (isEnabled) {

            drawerToggle = ActionBarDrawerToggle(
                this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
            )
            drawer_layout.addDrawerListener(drawerToggle)

            drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            drawerToggle.onDrawerStateChanged(DrawerLayout.LOCK_MODE_UNLOCKED)
            drawerToggle.syncState()


            supportActionBar?.setHomeButtonEnabled(true)
            nav_view.setNavigationItemSelectedListener(this)

        } else {

            drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerToggle.onDrawerStateChanged(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerToggle.syncState()
            toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material)
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.

        val transaction = supportFragmentManager.beginTransaction()
        when (item.itemId) {
            R.id.nav_scan -> {
                transaction.replace(R.id.fragment_container, scanFragment)
            }
            R.id.nav_about -> {
                transaction.replace(R.id.fragment_container, aboutFragment)
            }
            R.id.nav_options -> {
                transaction.replace(R.id.fragment_container, optionsFragment)
            }
            R.id.nav_log -> {
                transaction.replace(R.id.fragment_container, logFragment)
            }
        }

        transaction.addToBackStack(null)
        transaction.commit()

        drawer_layout.closeDrawer(GravityCompat.START)

        return true
    }

    fun goToDeviceFragment(bleDevice: BluetoothDevice) {

        deviceFragment.init(bleDevice)

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, deviceFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    fun backToScanFragment() {
        deviceFragment.connectToNewDevice = true
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, scanFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    fun setActionBarTitle(title: String) {
        supportActionBar!!.title = title
        supportActionBar?.setHomeButtonEnabled(false)
    }

    fun logInfo(message: String) {
        if(enableLog) {
            val timeInterval = SystemClock.elapsedRealtime() - startTime
            logFragment.appendToLog("${"%.3f".format(timeInterval.toFloat() / 1000F).padStart(8, '0')} - $message")
        }
        // Always log in the console
        Log.d(TAG, message)
    }
}
