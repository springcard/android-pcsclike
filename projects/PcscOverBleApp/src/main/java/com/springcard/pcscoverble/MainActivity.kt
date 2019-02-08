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
            com.springcard.pcscblelib.BuildConfig.DEBUG,
            com.springcard.pcscblelib.BuildConfig.APPLICATION_ID,
            com.springcard.pcscblelib.BuildConfig.BUILD_TYPE,
            com.springcard.pcscblelib.BuildConfig.FLAVOR,
            com.springcard.pcscblelib.BuildConfig.VERSION_CODE,
            com.springcard.pcscblelib.BuildConfig.VERSION_NAME,
            com.springcard.pcscblelib.BuildConfig.libraryDebug,
            com.springcard.pcscblelib.BuildConfig.libraryName,
            com.springcard.pcscblelib.BuildConfig.librarySpecial,
            com.springcard.pcscblelib.BuildConfig.libraryVersion,
            com.springcard.pcscblelib.BuildConfig.libraryVersionBuild,
            com.springcard.pcscblelib.BuildConfig.libraryVersionMajor,
            com.springcard.pcscblelib.BuildConfig.libraryVersionMinor)

        super.setAboutInfo(appInfo, libInfo)
    }


}