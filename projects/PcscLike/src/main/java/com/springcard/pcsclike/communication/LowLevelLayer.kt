/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import android.content.Context

interface LowLevelLayer {

    fun connect(ctx: Context)

    fun disconnect()

    fun close()

    fun write(data: List<Byte>)
}