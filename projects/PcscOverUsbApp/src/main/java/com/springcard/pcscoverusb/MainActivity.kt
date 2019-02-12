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
            /*com.springcard.pcscusblib.BuildConfig.DEBUG,
            com.springcard.pcscusblib.BuildConfig.APPLICATION_ID,
            com.springcard.pcscusblib.BuildConfig.BUILD_TYPE,
            com.springcard.pcscusblib.BuildConfig.FLAVOR,
            com.springcard.pcscusblib.BuildConfig.VERSION_CODE,
            com.springcard.pcscusblib.BuildConfig.VERSION_NAME,
            com.springcard.pcscusblib.BuildConfig.libraryDebug,
            com.springcard.pcscusblib.BuildConfig.libraryName,
            com.springcard.pcscusblib.BuildConfig.librarySpecial,
            com.springcard.pcscusblib.BuildConfig.libraryVersion,
            com.springcard.pcscusblib.BuildConfig.libraryVersionBuild,
            com.springcard.pcscusblib.BuildConfig.libraryVersionMajor,
            com.springcard.pcscusblib.BuildConfig.libraryVersionMinor*/
        true,"","","",55,"",true,"","","",0,0,0)

        super.setAboutInfo(appInfo, libInfo)
    }


}
