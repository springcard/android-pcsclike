/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverble

import android.os.Bundle
import com.springcard.pcscapp.DeviceFragment
import com.springcard.pcscapp.MainActivity

class MainActivity : MainActivity() {

    override val deviceFragment: DeviceFragment = com.springcard.pcscoverble.DeviceFragment()
    override val scanFragment: ScanFragment = com.springcard.pcscoverble.ScanFragment()
    override var supportCrypto = true // BLE support AES authentication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setAboutInfo()
    }

    private fun setAboutInfo() {

        val appInfo = ApplicationInfo(
            com.springcard.pcscoverble.BuildConfig.DEBUG,
            com.springcard.pcscoverble.BuildConfig.APPLICATION_ID,
            com.springcard.pcscoverble.BuildConfig.BUILD_TYPE,
            com.springcard.pcscoverble.BuildConfig.FLAVOR,
            com.springcard.pcscoverble.BuildConfig.VERSION_CODE,
            com.springcard.pcscoverble.BuildConfig.VERSION_NAME,
            com.springcard.pcscoverble.BuildConfig.appDebug)

        val libInfo = LibraryInfo(
            com.springcard.pcsclike.BuildConfig.DEBUG,
            com.springcard.pcsclike.BuildConfig.APPLICATION_ID,
            com.springcard.pcsclike.BuildConfig.BUILD_TYPE,
            com.springcard.pcsclike.BuildConfig.FLAVOR,
            com.springcard.pcsclike.BuildConfig.VERSION_CODE,
            com.springcard.pcsclike.BuildConfig.VERSION_NAME,
            com.springcard.pcsclike.BuildConfig.libraryDebug,
            com.springcard.pcsclike.BuildConfig.libraryName,
            com.springcard.pcsclike.BuildConfig.librarySpecial,
            com.springcard.pcsclike.BuildConfig.libraryVersion,
            com.springcard.pcsclike.BuildConfig.libraryVersionBuild,
            com.springcard.pcsclike.BuildConfig.libraryVersionMajor,
            com.springcard.pcsclike.BuildConfig.libraryVersionMinor)

        super.setAboutInfo(appInfo, libInfo)
    }


}