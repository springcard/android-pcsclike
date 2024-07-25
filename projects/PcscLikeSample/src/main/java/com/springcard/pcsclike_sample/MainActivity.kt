/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.widget.Toolbar
import com.google.android.material.navigation.NavigationView
import androidx.drawerlayout.widget.DrawerLayout
import com.android.volley.BuildConfig
import com.android.volley.Request
import com.android.volley.Response
import com.google.gson.GsonBuilder
import org.json.JSONArray
import com.android.volley.toolbox.*
import com.springcard.pcsclike_sample.R
import com.springcard.pcsclike_sample.databinding.ActivityMainBinding

abstract class MainActivity  :  AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var binding: ActivityMainBinding

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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.contentMain.toolbar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }

        logInfo("Application started")

        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.add(R.id.fragment_container, scanFragment)
        fragmentTransaction.commit()
        binding.navView.setCheckedItem(R.id.nav_scan)


        /* Get APDU model List */

        /* Instantiate the RequestQueue */
        val requestQueue = Volley.newRequestQueue(this)
        val url = "https://models.springcard.com/api/models"

        /* Use JSON previously stored in config */
        loadJsonApduModel(preferences.modelsApdusJson)

        /* Request a string response from the provided URL */
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                preferences.modelsApdusJson = response.toString()
                loadJsonApduModel(preferences.modelsApdusJson)
            },
            { error ->
                Log.e("HTTP Request Error", error.toString())
            })

        requestQueue.add(stringRequest)

        logInfo("Lib rev = ${BuildConfig.VERSION_NAME}")
        logInfo("App rev = ${BuildConfig.VERSION_NAME}")
    }

    fun getToolbar(): Toolbar = binding.contentMain.toolbar
    fun getDrawerLayout(): DrawerLayout = binding.drawerLayout

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

        drawerToggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.contentMain.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)

        if (isEnabled) {
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            /* A MODIFIER */
            //drawerToggle.onDrawerStateChanged(DrawerLayout.LOCK_MODE_UNLOCKED)
            drawerToggle.syncState()

            supportActionBar?.setHomeButtonEnabled(true)
            binding.navView.setNavigationItemSelectedListener(this)
        } else {
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            /* A MODIFIER */
            //drawerToggle.onDrawerStateChanged(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            drawerToggle.syncState()
            binding.contentMain.toolbar.setNavigationIcon(androidx.constraintlayout.widget.R.drawable.abc_ic_ab_back_material)
        }
    }

    override fun onBackPressed() {
        if(deviceFragment.isResumed) {
            deviceFragment.quitAndDisconnect()
        }
        else {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
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

        binding.drawerLayout.closeDrawer(GravityCompat.START)

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
