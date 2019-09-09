/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.ccid

import android.util.Log
import com.springcard.pcsclike.utils.*
import java.lang.Exception
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor
import kotlin.random.Random


internal class CcidSecure(private val secureConnectionParameters: CcidSecureParameters) {

    private val TAG = this::class.java.simpleName

    private val  protocolCode: Byte = 0x00
    private val useRandom = true

    private lateinit var sessionEncKey: MutableList<Byte>
    private lateinit var sessionMacKey: MutableList<Byte>
    private lateinit var sessionSendIV: MutableList<Byte>
    private lateinit var sessionRecvIV: MutableList<Byte>


    enum class ProtocolOpcode(val value: Byte) {
        Success(0x00),
        Authenticate(0x0A),
        Following(0xFF.toByte())
    }


    fun decryptCcidBuffer(frame: CcidResponse): CcidResponse {

        /* Extract the CMAC */
        val receivedCmac = frame.raw.takeLast(8)
        frame.raw = frame.raw.dropLast(8).toMutableList()

        /* Extract the data */
        var data = frame.raw.drop(10).toMutableList()

        Log.d(TAG, "   >     (crypted data) ${data.toHexString()}")

        /* Decipher the data */
        data = aesCbcDecrypt(sessionEncKey, sessionRecvIV, data)

        Log.d(TAG, "   >      (padded data) ${data.toHexString()}")

        var dataLen = data.size
        while (dataLen > 0 && data[dataLen - 1] == 0x00.toByte())
            dataLen--

        if (dataLen == 0 || data[dataLen - 1] != 0x80.toByte()) {
            val msg = "Padding is invalid (decryption failed/wrong session key?)"
            Log.e(TAG, msg)
            throw Exception(msg)
        }
        dataLen -= 1
        data = data.take(dataLen).toMutableList()

        Log.d(TAG, "   >       (plain data) ${data.toHexString()}")

        /* Extract the header and re-create a valid buffer */
        frame.raw = frame.raw.take(10).toMutableList()
        frame.length = data.size
        frame.raw.addAll(data)

        /* Compute the CMAC */
        val computedCmac = computeCmac(sessionMacKey, sessionRecvIV, frame.raw)

        Log.d(TAG, "   >${frame.raw.toHexString()} -> CMAC=${computedCmac.take(8).toHexString()}")

        if (receivedCmac != computedCmac.take(8)) {
            val msg = "CMAC is invalid (wrong session key?)"
            Log.e(TAG, msg)
            throw Exception(msg)
        }

        sessionRecvIV = computedCmac

        return frame
    }


    fun encryptCcidBuffer(frame: CcidCommand): CcidCommand {

        /* Compute the CMAC of the plain buffer */
        val cmac = computeCmac(sessionMacKey, sessionSendIV, frame.raw)

        Log.d(TAG, "   <${frame.raw.toHexString()} -> CMAC=${cmac.take(8).toMutableList().toHexString()}")

        /* Extract the data */
        var data = frame.raw.drop(10).toMutableList()

        Log.d(TAG, "   <       (plain data) ${data.toHexString()}")

        /* Cipher the data */
        data.add(0x80.toByte())
        while (data.size % 16 != 0) {
            data.add(0x00)
        }

        Log.d(TAG, "   <      (padded data) ${data.toHexString()}")

        data = aesCbcEncrypt(sessionEncKey, sessionSendIV, data)

        Log.d(TAG, "   <     (crypted data) ${data.toHexString()}")

        /* Update the length */
        frame.length = data.size + 8
        frame.ciphered = true

        /* Re-create the buffer */
        frame.raw = frame.raw.take(10).toMutableList()
        frame.raw.addAll(data)
        frame.raw.addAll(cmac.take(8))

        sessionSendIV = cmac

        return frame
    }

    private fun getRandom(length: Int): MutableList<Byte>
    {
        var result = mutableListOf<Byte>()
        if (!useRandom) {
            for (i in 0 until length) {
                result.add((0xA0 or (i and 0x0F)).toByte())
            }
        }
        else {
            result = Random.nextBytes(length).toMutableList()
        }
        return result
    }

    private fun computeCmac(key: MutableList<Byte>, iv: MutableList<Byte>, buffer: MutableList<Byte>): MutableList<Byte> {
        var cmac: MutableList<Byte>
        var actualLength: Int = buffer.size + 1

        cmac = iv

        Log.d(TAG, "Compute CMAC")

        while ((actualLength % 16) != 0) actualLength++

        for (i in 0 until actualLength step 16) {
            val block = mutableListOf<Byte>()

            for (j in 0 until 16) {
                when {
                    (i + j) < buffer.size -> block.add(buffer[i + j])
                    (i + j) == buffer.size -> block.add(0x80.toByte())
                    else -> block.add(0x00)
                }
            }

            Log.d(TAG, "\tBlock=${block.toHexString()}, IV=${cmac.toHexString()}, key=${key.toHexString()}")

            for (j in 0 until 16)
                block[j] = block[j].xor(cmac[j])

            Log.d(TAG, "\tBlock XOR=${block.toHexString()}")

            cmac = aesEcbEncrypt(key, block)

            Log.d(TAG, "\t\t-> ${cmac.toHexString()}")
        }

        return cmac

    }

    private fun  aesCbcEncrypt(key: MutableList<Byte>, iv: MutableList<Byte>, buffer: MutableList<Byte>): MutableList<Byte> {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keyCipher = SecretKeySpec(key.toByteArray(), 0, key.size, "AES")
        val initVector = IvParameterSpec(iv.toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, keyCipher, initVector)
        return cipher.doFinal(buffer.toByteArray()).toMutableList()
    }

    private fun aesCbcDecrypt(key: MutableList<Byte>, iv: MutableList<Byte>, buffer: MutableList<Byte>): MutableList<Byte> {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val keyCipher = SecretKeySpec(key.toByteArray(), 0, key.size, "AES")
        val initVector = IvParameterSpec(iv.toByteArray())
        cipher.init(Cipher.DECRYPT_MODE, keyCipher, initVector)
        return cipher.doFinal(buffer.toByteArray()).toMutableList()
    }

    private fun aesEcbEncrypt(key: MutableList<Byte>, buffer: MutableList<Byte>): MutableList<Byte> {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val keyCipher = SecretKeySpec(key.toByteArray(), 0, key.size, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keyCipher)
        return cipher.doFinal(buffer.toByteArray()).toMutableList()
    }

    private fun aesEcbDecrypt(key: MutableList<Byte>, buffer: MutableList<Byte>): MutableList<Byte> {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        val keyCipher = SecretKeySpec(key.toByteArray(), 0, key.size, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keyCipher)
        return cipher.doFinal(buffer.toByteArray()).toMutableList()
    }

    private fun cleanupAuthentication() {
        sessionEncKey = mutableListOf()
        sessionMacKey = mutableListOf()
        sessionSendIV = mutableListOf()
        sessionRecvIV = mutableListOf()
    }

    private var rndA = mutableListOf<Byte>()
    private var rndB = mutableListOf<Byte>()

    fun hostAuthCmd(): ByteArray {
        cleanupAuthentication()

        val keySelect = secureConnectionParameters.keyIndex.value
        val keyValue = secureConnectionParameters.keyValue

        Log.d(TAG, "Running AES mutual authentication using key 0x${String.format("%02X", keySelect)}")

        /* Generate host nonce */
        rndA = getRandom(16)

        Log.d(TAG, "key=${keyValue.toHexString()}")
        Log.d(TAG, "rndA=${rndA.toHexString()}")

        /* Host->Device AUTHENTICATE command */
        /* --------------------------------- */

        val cmdAuthenticate = mutableListOf<Byte>()

        cmdAuthenticate.add(protocolCode)
        cmdAuthenticate.add(ProtocolOpcode.Authenticate.value)
        cmdAuthenticate.add(0x01) /* Version & mode = AES128 */
        cmdAuthenticate.add(keySelect)

        Log.d(TAG, "   <                    ${cmdAuthenticate.toHexString()}")

        return cmdAuthenticate.toByteArray()
    }

    fun deviceRespStep1(response: ByteArray): Boolean {

        val rspStep1 = response.toMutableList()

        Log.d(TAG, "   >                    ${rspStep1.toHexString()}")

        /* Device->Host Authentication Step 1 */
        /* ---------------------------------- */

        if (rspStep1.size < 1) {
            Log.d(TAG, "Authentication failed at step 1 (response is too short)")
            return false
        }

        if (rspStep1[0] != ProtocolOpcode.Following.value) {
            Log.d(TAG, "Authentication failed at step 1 (the device has reported an error: 0x${String.format("%02X", rspStep1[0])})")
            return false
        }

        if (rspStep1.size != 17) {
            Log.d(TAG, "Authentication failed at step 1 (response does not have the expected format)")
            return false
        }

        return true
    }

    fun hostCmdStep2(rspStep1: MutableList<Byte>): ByteArray {
        val t = rspStep1.slice(1..16).toMutableList()
        rndB = aesEcbDecrypt(secureConnectionParameters.keyValue, t)

        Log.d(TAG, "rndB=${rndB.toHexString()}")

        /* Host->Device Authentication Step 2 */
        /* ---------------------------------- */

        val cmdStep2 = mutableListOf<Byte>()

        cmdStep2.add(protocolCode)
        cmdStep2.add(ProtocolOpcode.Following.value)
        cmdStep2.addAll(aesEcbEncrypt(secureConnectionParameters.keyValue, rndA))
        cmdStep2.addAll(aesEcbEncrypt(secureConnectionParameters.keyValue, rndB.toByteArray().RotateLeftOneByte().toMutableList()))

        Log.d(TAG, "   <                    ${cmdStep2.toHexString()}")

        return cmdStep2.toByteArray()
    }

    fun deviceRespStep3(response: ByteArray): Boolean {

        val rspStep3 = response.toMutableList()

        Log.d(TAG, "   >                    ${rspStep3.toHexString()}")

        /* Device->Host Authentication Step 3 */
        /* ---------------------------------- */

        if (rspStep3.size < 1) {
            Log.d(TAG, "Authentication failed at step 3")
            return false
        }

        if (rspStep3[0] != ProtocolOpcode.Success.value) {
            Log.d(TAG, "Authentication failed at step 3 (the device has reported an error: 0x${String.format("%02X", rspStep3[0])})")
            return false
        }

        if (rspStep3.size != 17) {
            Log.d(TAG, "Authentication failed at step 3 (response does not have the expected format)")
            return false
        }

        var t = rspStep3.slice(1..16).toMutableList()
        t = aesEcbDecrypt(secureConnectionParameters.keyValue, t)
        t = t.toByteArray().RotateRightOneByte().toMutableList()

        if (t !=  rndA) {
            Log.d(TAG, "${t.toHexString()}!=${rndA.toHexString()}")
            Log.d(TAG, "Authentication failed at step 3 (device's cryptogram is invalid)")
            return false
        }

        /* Session keys and first init vector */
        /* ---------------------------------- */

        val sv1 = mutableListOf<Byte>()
        sv1.addAll(0, rndA.slice(0..3))
        sv1.addAll(4, rndB.slice(0..3))
        sv1.addAll(8, rndA.slice(8..11))
        sv1.addAll(12, rndB.slice(8..11))

        Log.d(TAG, "SV1=${sv1.toHexString()}")

        val sv2 = mutableListOf<Byte>()
        sv2.addAll(0, rndA.slice(4..7))
        sv2.addAll(4, rndB.slice(4..7))
        sv2.addAll(8, rndA.slice(12..15))
        sv2.addAll(12, rndB.slice(12..15))

        Log.d(TAG, "SV2=${sv2.toHexString()}")

        sessionEncKey = aesEcbEncrypt(secureConnectionParameters.keyValue, sv1)

        Log.d(TAG, "Kenc=${sessionEncKey.toHexString()}")

        sessionMacKey = aesEcbEncrypt(secureConnectionParameters.keyValue, sv2)

        Log.d(TAG, "Kmac=${sessionMacKey.toHexString()}")

        t = XOR(rndA, rndB)
        t = aesEcbEncrypt(sessionMacKey, t)

        Log.d(TAG, "IV0=${t.toHexString()}")

        sessionSendIV = t
        sessionRecvIV = t

        return true
    }
}

