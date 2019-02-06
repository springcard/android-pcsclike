/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcscblelib


private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

/**
 * Convert a ByteArray to a String in hexadecimal format
 * ex: ```[ 0x1F, 0xAB, 0xCD ] -> 1FABCD```.
 * See [String.hexStringToByteArray] to do the inverse
 *
 * @receiver ByteArray to convert
 * @return Hexadecimal string
 */
fun ByteArray.byteArrayToHexString(): String {
    val result = StringBuffer()

    forEach {
        val octet = it.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
        result.append(HEX_CHARS[firstIndex])
        result.append(HEX_CHARS[secondIndex])
    }

    return result.toString()
}

/**
 * Convert an hexadecimal String to a ByteArray
 * ex: ```1FABCD -> [ 0x1F, 0xAB, 0xCD ]```.
 * See [ByteArray.byteArrayToHexString] to do the inverse
 *
 * @receiver String to convert
 * @return ByteArray
 */
fun String.hexStringToByteArray(): ByteArray {

    val result = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        val firstIndex = HEX_CHARS.indexOf(this[i])
        val secondIndex = HEX_CHARS.indexOf(this[i + 1])

        val octet = firstIndex.shl(4).or(secondIndex)
        result[i.shr(1)] = octet.toByte()
    }

    return result
}

internal val String.Empty: String
    get() = ""


/**
 * Check is a string represent an hexadecimal value
 * (even length, digit and A-F chars)
 *
 * @receiver String to be checked
 * @return true if the string is an hexadecimal value
 */
fun String.isHex(): Boolean {

    if (this.isEmpty()) {
        return false
    }

    // length should be even number
    // otherwise its not a valid hex
    if (this.length % 2 == 0) {
        val var1 = "(?i)[0-9A-F]+"
        return this.matches(var1.toRegex())
    }

    return false
}

// LSB first 5555555 = 0xE3 0xB5 0x4F  0x03
internal fun Int.bytes(): ByteArray {
    var b = mutableListOf<Byte>(0, 0, 0, 0)

    for (i in 0 until 4) {
        b[i] = (this shr (i*8)).toByte()
    }

    return b.toByteArray()
}
