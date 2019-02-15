/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscoverusb


import android.os.Bundle
import com.springcard.pcscapp.MainActivity


class MainActivity : MainActivity() {


    override val deviceFragment = DeviceFragment()
    override val scanFragment= ScanFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setAboutInfo()
    }

    private fun setAboutInfo() {

        val appInfo = ApplicationInfo(
            com.springcard.pcscoverusb.BuildConfig.DEBUG,
            com.springcard.pcscoverusb.BuildConfig.APPLICATION_ID,
            com.springcard.pcscoverusb.BuildConfig.BUILD_TYPE,
            com.springcard.pcscoverusb.BuildConfig.FLAVOR,
            com.springcard.pcscoverusb.BuildConfig.VERSION_CODE,
            com.springcard.pcscoverusb.BuildConfig.VERSION_NAME,
            com.springcard.pcscoverusb.BuildConfig.appDebug)

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
