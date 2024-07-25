/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample_ble

import android.os.Bundle
import com.android.volley.BuildConfig
import com.springcard.pcsclike_sample.MainActivity
import com.springcard.pcsclike_sample_ble.ScanFragment

class MainActivity : MainActivity() {

    override val deviceFragment: DeviceFragment = DeviceFragment()
    override val scanFragment: ScanFragment = ScanFragment()
    override var supportCrypto = true // BLE support AES authentication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setAboutInfo()
    }

    private fun setAboutInfo() {

        val appInfo = ApplicationInfo(
            BuildConfig.DEBUG,
            BuildConfig.APPLICATION_ID,
            BuildConfig.BUILD_TYPE,
            "NO",
            BuildConfig.VERSION_CODE,
            BuildConfig.VERSION_NAME,
            BuildConfig.DEBUG
        )

        val libInfo = LibraryInfo(
            BuildConfig.DEBUG,
            BuildConfig.APPLICATION_ID,
            BuildConfig.BUILD_TYPE,
            "NO",
            BuildConfig.VERSION_CODE,
            BuildConfig.VERSION_NAME,
            com.springcard.pcsclike.BuildConfig.DEBUG,
            com.springcard.pcsclike.BuildConfig.libraryName,
            com.springcard.pcsclike.BuildConfig.librarySpecial,
            com.springcard.pcsclike.BuildConfig.libraryVersion,
            com.springcard.pcsclike.BuildConfig.libraryVersionBuild,
            com.springcard.pcsclike.BuildConfig.libraryVersionMajor,
            com.springcard.pcsclike.BuildConfig.libraryVersionMinor)

        super.setAboutInfo(appInfo, libInfo)
    }


}