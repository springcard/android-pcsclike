/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike_sample


import android.content.pm.PackageManager
import androidx.fragment.app.Fragment


abstract class ScanFragment : Fragment() {

    protected val TAG = this::class.java.simpleName

    /* System support feature ? */
    protected fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

    protected lateinit var mainActivity: MainActivity
}
