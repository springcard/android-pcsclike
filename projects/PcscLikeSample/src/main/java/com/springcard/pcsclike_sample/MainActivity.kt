/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.util.Log
import android.view.MenuItem
import android.support.design.widget.NavigationView
import android.support.v4.widget.DrawerLayout
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.json.JSONArray


abstract class MainActivity  :  AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = this::class.java.simpleName
    abstract val scanFragment : ScanFragment
    private val optionsFragment = OptionsFragment()
    abstract val deviceFragment : DeviceFragment
    private val aboutFragment = AboutFragment()
    private val logFragment = LogFragment()

    private lateinit var drawerToggle: ActionBarDrawerToggle


    private val options = "optionsPcscApp"
    private val enableLogName = "enableLog"
    private val stopOnErrorName = "stopOnError"
    private val enableTimeMeasurementName = "enableTimeMeasurement"
    private val useAuthentificationName = "useAuthentication"
    private val authenticatorKeyName = "authenticationKey"
    var enableLog: Boolean
        get() {
            val sp = getSharedPreferences(options, 0)
            return sp.getBoolean(enableLogName, true)
        }
        set(value) {
            val editor =  getSharedPreferences(options, 0).edit()
            editor.putBoolean(enableLogName, value)
            editor.apply()
        }

    var stopOnError: Boolean
        get() {
            val sp = getSharedPreferences(options, 0)
            return sp.getBoolean(stopOnErrorName, false)
        }
        set(value) {
            val editor =  getSharedPreferences(options, 0).edit()
            editor.putBoolean(stopOnErrorName, value)
            editor.apply()
        }

    var enableTimeMeasurement: Boolean
        get() {
            val sp = getSharedPreferences(options, 0)
            return sp.getBoolean(enableTimeMeasurementName, false)
        }
        set(value) {
            val editor =  getSharedPreferences(options, 0).edit()
            editor.putBoolean(enableTimeMeasurementName, value)
            editor.apply()
        }

    abstract var supportCrypto: Boolean

    var useAuthentication: Boolean
        get() {
            val sp = getSharedPreferences(options, 0)
            return sp.getBoolean(useAuthentificationName, false)
        }
        set(value) {
            val editor =  getSharedPreferences(options, 0).edit()
            editor.putBoolean(useAuthentificationName, value)
            editor.apply()
        }

    var authenticationKey: String
        get() {
            val sp = getSharedPreferences(options, 0)
            return sp.getString(authenticatorKeyName, "00000000000000000000000000000000")!!
        }
        set(value) {
            val editor =  getSharedPreferences(options, 0).edit()
            editor.putString(authenticatorKeyName, value)
            editor.apply()
        }

    /* Store the start time */
    private var startTime = SystemClock.elapsedRealtime()

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


        /* Get APDU model List */

        /* Instantiate the RequestQueue */
        val queue = Volley.newRequestQueue(this)
        val url = "http://models.springcard.com/api/models"
        var modelsApdus: MutableList<ApduModel>

        /* Request a string response from the provided URL */
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                /* Display the first 500 characters of the response string */
                val jsonArray = JSONArray(response.toString())
                val gson =  GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()

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

        logInfo("Lib rev = ${com.springcard.pcsclike.BuildConfig.VERSION_NAME}")
        logInfo("App rev = ${com.springcard.pcsclike_sample.BuildConfig.VERSION_NAME}")
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

    fun goToDeviceFragment(_device: Any) {

        deviceFragment.init(_device)

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
        /* Always log in the console */
        Log.d(TAG, message)
    }

    protected fun setAboutInfo(appInfo: ApplicationInfo, libInfo: LibraryInfo) {
        aboutFragment.setAboutInfo(appInfo, libInfo)
    }

    data class ApplicationInfo(
        val DEBUG: Boolean,
        val APPLICATION_ID: String,
        val BUILD_TYPE: String,
        val FLAVOR: String,
        val VERSION_CODE: Int,
        val VERSION_NAME: String,
        val appDebug: Boolean)

    data class LibraryInfo(
        val DEBUG: Boolean,
        val APPLICATION_ID: String,
        val BUILD_TYPE: String,
        val FLAVOR: String,
        val VERSION_CODE: Int,
        val VERSION_NAME: String,
        val libraryDebug: Boolean,
        val libraryName: String,
        val librarySpecial: String,
        val libraryVersion: String,
        val libraryVersionBuild: Int,
        val libraryVersionMajor: Int,
        val libraryVersionMinor: Int)
}
