/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscapp


import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*


abstract class ScanFragment : Fragment() {

    protected val TAG = this::class.java.simpleName

    protected lateinit var mainActivity: MainActivity


}
