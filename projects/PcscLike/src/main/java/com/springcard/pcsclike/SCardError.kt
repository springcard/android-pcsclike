/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike

/**
 * Class used by the errors callbacks when an error occurred
 * It contains some information about how the error happened
 *
 * @property code The error code
 * @property detail Detailed information about the error
 * @property isFatal If the error was critical, the device was lost and disconnected
 * @property message Message corresponding to the ErrorCodes
 *
 */
class SCardError(val code: ErrorCodes, val detail : String, val isFatal: Boolean = false) {

    val message : String
    get() {
        return code.name
    }

    /**
     * List of error codes
     *
     * @property value Error code value (2 bytes)
     */
    enum class ErrorCodes(val value: Int){

        /**
         * Invalid parameter
         */
        INVALID_PARAMETER(0x1000),
        /**
         * Other error
         */
        OTHER_ERROR(0xFFFF),
        /**
         * Device is Busy
         */
        BUSY(0x1001),

        /**
         * The library has been called to instantiate a device (SCardReaderList object),
         * but the provided input parameter does not refer to a connected BLE device.
         * Or the underlying BLE device has been disconnected
         */
        DEVICE_NOT_CONNECTED(0x1100),
        /**
         * The device's primary service is not supported by the library
         */
        UNSUPPORTED_PRIMARY_SERVICE(0x1200),
        /**
         * One of the mandatory services is missing from the device's GATT
         */
        MISSING_SERVICE(0x1300),
        /**
         * One of the mandatory characteristics is missing from the device's GATT
         */
        MISSING_CHARACTERISTIC(0x1400),
        /**
         * One of the mandatory services has not the expected settings
         * (for instance, bonding flags not consistent with the implementation in the library)
         */
        INVALID_SERVICE_SETTINGS(0x1500),
        /**
         * One of the mandatory characteristics has not the expected settings
         * (for instance, read/write/notify flags not consistent with the implementation in the library)
         */
        INVALID_CHARACTERISTIC_SETTINGS(0x1600),
        /**
         * The library has failed to enable the notifications or indications on a given characteristic
         */
        ENABLE_CHARACTERISTIC_EVENTS_FAILED(0x1700),
        /**
         * The library has failed to read a given characteristic
         */
        READ_CHARACTERISTIC_FAILED(0x1800),
        /**
         * The library has failed to write a given characteristic
         */
        WRITE_CHARACTERISTIC_FAILED(0x1900),
        /**
         * The underlying BLE device is still connected, but a CCID-level timeout has occurred
         * (no RDR_To_PC after a PC_To_RDR)
         */
        COMMUNICATION_TIMEOUT(0x1A00),
        /**
         * The format of the RDR_To_PC or CCID_Status characteristic is invalid
         * (unsupported opcode, bad length etc)
         */
        PROTOCOL_ERROR(0x1B00),
        /**
         * The device does not answer as expected
         * (ex: empty response to a SCardControl that is looking for the slot name)
         */
        DIALOG_ERROR(0x1C00),
        /**
         * The number of slots in CCID_Status is 0
         */
        DUMMY_DEVICE(0x1D00),

        /**
         * The authentication between the library and the device has failed
         */
        AUTHENTICATION_ERROR(0x2100),
        /**
         * Wrong CMAC or frame decipher failed
         */
        SECURE_COMMUNICATION_ERROR(0x2200),
        /**
         * The device has reported a secure communication on its side and is closing the communication
         */
        SECURE_COMMUNICATION_ABORTED(0x2300),

        /**
         * getReader invoked with an invalid parameter
         */
        NO_SUCH_SLOT(0x3100),
        /**
         * Connect invoked, but there's no card in the slot
         */
        CARD_ABSENT(0x3200),
        /**
         * A card is present, but it is unresponsive
         */
        CARD_MUTE(0x3300),
        /**
         * Transmit or Disconnect invoked, but the library is aware that the card has already been removed
         */
        CARD_REMOVED(0x3400),
        /**
         * Transmit invoked, and no response from the card (the card is likely to have been removed during the exchange)
         */
        CARD_COMMUNICATION_ERROR(0x3400),
        /**
         * Transmit invoked, but Disconnect has been called earlier
         */
        CARD_POWERED_DOWN(0x3500)
    }
}