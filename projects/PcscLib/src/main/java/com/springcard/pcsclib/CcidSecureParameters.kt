/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclib

/**
 * Class used to authenticate and ciphering CCID frames
 *
 * @property authMode Authentication version & mode (0x01 -> AES128)
 * @property keyIndex Index of the Key (0x00 -> User, 0x01 -> Admin)
 * @property keyValue The value of the key on 16 bytes for AES128
 * @property mode This field specify how the communication will be secured once the authentication is passed
 * @constructor
 */
class CcidSecureParameters (val authMode: AuthenticationMode, val keyIndex: AuthenticationKeyIndex, val keyValue: MutableList<Byte>, val mode: CommunicationMode) {
    enum class AuthenticationMode(val value: Byte) {
        None(-1),
        Aes128(0x01)
    }
    enum class AuthenticationKeyIndex(val value: Byte) {
        None(-1),
        User(0x00),
        Admin(0x01)
    }
    enum class CommunicationMode(val value: Byte) {
        Plain(0x00),
        Mac(0x01),
        MacAndCipher(0x03)
    }
}