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
            com.springcard.pcsclib.BuildConfig.DEBUG,
            com.springcard.pcsclib.BuildConfig.APPLICATION_ID,
            com.springcard.pcsclib.BuildConfig.BUILD_TYPE,
            com.springcard.pcsclib.BuildConfig.FLAVOR,
            com.springcard.pcsclib.BuildConfig.VERSION_CODE,
            com.springcard.pcsclib.BuildConfig.VERSION_NAME,
            com.springcard.pcsclib.BuildConfig.libraryDebug,
            com.springcard.pcsclib.BuildConfig.libraryName,
            com.springcard.pcsclib.BuildConfig.librarySpecial,
            com.springcard.pcsclib.BuildConfig.libraryVersion,
            com.springcard.pcsclib.BuildConfig.libraryVersionBuild,
            com.springcard.pcsclib.BuildConfig.libraryVersionMajor,
            com.springcard.pcsclib.BuildConfig.libraryVersionMinor)

        super.setAboutInfo(appInfo, libInfo)
    }


}