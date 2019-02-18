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
    override var supportCrypto = false // USB does not support authentication

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
