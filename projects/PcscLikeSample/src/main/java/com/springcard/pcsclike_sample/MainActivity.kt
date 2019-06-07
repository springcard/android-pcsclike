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
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.json.JSONArray
import com.android.volley.toolbox.*


abstract class MainActivity  :  AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG = this::class.java.simpleName
    abstract val scanFragment : ScanFragment
    private val optionsFragment = OptionsFragment()
    abstract val deviceFragment : DeviceFragment
    private val aboutFragment = AboutFragment()
    private val logFragment = LogFragment()

    private lateinit var drawerToggle: ActionBarDrawerToggle
    val preferences by lazy { AppPreferences(this.applicationContext) }
    abstract var supportCrypto: Boolean

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
        val requestQueue = Volley.newRequestQueue(this)
        val url = "https://models.springcard.com/api/models"

        /* Use JSON previously stored in config */
        loadJsonApduModel(preferences.modelsApdusJson)

        /* Request a string response from the provided URL */
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                /* Store the JSON response string */
                preferences.modelsApdusJson = response.toString()
                loadJsonApduModel(preferences.modelsApdusJson)
            },
            Response.ErrorListener {
                /* Do nothing, we already used the JSON previously stored in config */
            })

        /* Add the request to the RequestQueue */
        requestQueue.add(stringRequest)


        logInfo("Lib rev = ${com.springcard.pcsclike.BuildConfig.VERSION_NAME}")
        logInfo("App rev = ${com.springcard.pcsclike_sample.BuildConfig.VERSION_NAME}")
    }

    private fun loadJsonApduModel(json: String) {
        val modelsApdus: MutableList<ApduModel>
        val jsonArray = JSONArray(json)
        val gson =  GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()

        if (jsonArray.length() > 0) {
            logInfo("Nb of apdu examples in model = ${jsonArray.length()}" )
            modelsApdus = gson.fromJson(jsonArray.toString(), Array<ApduModel>::class.java).toMutableList()
            /* Sort by id and not by title alphabetically */
            modelsApdus.sortBy { it.id }
            deviceFragment.setApduModels(modelsApdus)
        }
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
        if(deviceFragment.isResumed) {
            deviceFragment.quitAndDisconnect()
        }
        else {
            if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
                drawer_layout.closeDrawer(GravityCompat.START)
            } else {
                super.onBackPressed()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        /* Handle navigation view item clicks here */
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
        /* TODO CRA: cf https://medium.com/@elye.project/handling-illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-d4ee8b630066 */
        transaction.commitAllowingStateLoss()
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
        if(preferences.enableLog) {
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
