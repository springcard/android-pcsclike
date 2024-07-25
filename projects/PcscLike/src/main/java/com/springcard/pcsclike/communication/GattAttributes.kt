/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import java.util.*


open class GattAttributes {

    private val standardUuidPrefix = "0000"
    private val standardUuidSuffix = "-0000-1000-8000-00805F9B34FB"

    private fun getUuidFromShort(shortUuid: String) : UUID {
        return UUID.fromString("$standardUuidPrefix$shortUuid$standardUuidSuffix")
    }

    protected fun getUuid(longUuid: String) : UUID {
        return UUID.fromString(longUuid)
    }

    /* Generic Services */

    val UUID_GENERIC_ACCESS_SERVICE = getUuidFromShort("1800")
    val UUID_DEVICE_NAME_CHAR =  getUuidFromShort("2A00")
    val UUID_APPEARANCE_CHAR =  getUuidFromShort("2A01")

    val UUID_DEVICE_INFORMATION_SERVICE =  getUuidFromShort("180A")
    val UUID_MANUFACTURER_NAME_STRING_CHAR =  getUuidFromShort("2A29")
    val UUID_MODEL_NUMBER_STRING_CHAR =  getUuidFromShort("2A24")
    val UUID_SERIAL_NUMBER_STRING_CHAR =  getUuidFromShort("2A25")
    val UUID_FIRMWARE_REVISION_STRING_CHAR =  getUuidFromShort("2A26")
    val UUID_SOFTWARE_REVISION_STRING_CHAR =  getUuidFromShort("2A28")
    val UUID_HARDWARE_REVISION_STRING_CHAR =  getUuidFromShort("2A27")
    val UUID_PNP_ID_CHAR =  getUuidFromShort("2A50")

    val UUID_BATTERY_SERVICE =  getUuidFromShort("180F")
    val UUID_BATTERY_LEVEL_CHAR =  getUuidFromShort("2A19")
    val UUID_BATTERY_POWER_STATE_CHAR =  getUuidFromShort("2A1A")

    val UUID_GENERIC_ATTRIBUTE_SERVICE = getUuidFromShort("180F")
    val UUID_SERVICE_CHANGED_CHAR = getUuidFromShort("2A05")

}

object GattAttributesD600 : GattAttributes() {

    /* SpringCard Services */

    val UUID_SPRINGCARD_RFID_SCAN_PCSC_LIKE_SERVICE = getUuid("6CB501B7-96F6-4EEF-ACB1-D7535F153CF0") //
    val UUID_SPRINGCARD_SCAN_DATA_CHAR = getUuid("CE3E81B8-D871-4613-BA78-5FFC0B1520A6")
    val UUID_SPRINGCARD_SCAN_CONTROL_CHAR = getUuid("833A2364-BCA0-4647-8113-478E1FC449BA")
    val UUID_SPRINGCARD_CCID_STATUS_CHAR = getUuid("7C334BC2-1812-4C7E-A81D-591F92933C37")
    val UUID_SPRINGCARD_CCID_TO_RDR_CHAR = getUuid("91ACE9FD-EDD6-40B1-BA77-050A78CF9BC0")
    val UUID_SPRINGCARD_CCID_TO_PC_CHAR = getUuid("B4CA2D75-B855-4C1A-BF40-4A72AE46BD5A")

    val UUID_SPRINGCARD_DEVICE_CONFIG_SERVICE = getUuid("7A4385C9-F7C7-4E22-9AFD-16D68FC588CA")
    val UUID_SPRINGCARD_CONFIG_IO_CHAR = getUuid("1254FC72-336E-4BB2-A0A8-71C7D28D73CE")

    val UUID_SPRINGCARD_OTA_DFU_SERVICE = getUuid("D7B82F8E-7D95-4143-8D97-B57FC21B025B")
    val UUID_SPRINGCARD_DFU_TO_RDR_CHAR = getUuid("AC38BCCD-D1D6-4DF6-AE84-AE6A951F9971")
    val UUID_SPRINGCARD_DFU_TO_PC_CHAR = getUuid("9110BC7E-124B-4D93-B29B-BC7BF74AE964")
}


object GattAttributesSpringCore : GattAttributes() {

    /* SpringCard Services */

    val UUID_SPRINGCARD_CCID_PLAIN_SERVICE = getUuid("F91C914F-367C-4108-AC3E-3D30CFDD0A1A") //
    private val UUID_CCID_PC_TO_RDR_PLAIN_CHAR = getUuid("281EBED4-86C4-4253-84F1-57FB9AB2F72C")
    private val UUID_CCID_RDR_TO_PC_PLAIN_CHAR = getUuid("811DC7A6-A573-4E15-89CC-7EFACAE04E3C")
    private val UUID_CCID_STATUS_PLAIN_CHAR = getUuid("EAB75CAB-C7DC-4DB9-874C-4AD8EE0F180F")

    val UUID_SPRINGCARD_CCID_BONDED_SERVICE = getUuid("7F20CDC5-A9FC-4C70-9292-3ACF9DE71F73") //
    private val UUID_CCID_PC_TO_RDR_BONDED_CHAR = getUuid("CD5BCE75-65FC-4747-AB9A-FF82BFDFA7FB")
    private val UUID_CCID_RDR_TO_PC_BONDED_CHAR = getUuid("94EDE62E-0808-46F8-91EC-AC0272D67796")
    private val UUID_CCID_STATUS_BONDED_CHAR = getUuid("DC2AA4CA-76A9-43F9-9FE5-127652837EF5")

    val UUID_SPRINGCARD_S320_PLAIN_SERVICE = getUuid("F91C914F-367C-4108-AC3E-3D3030323353")
    val UUID_SPRINGCARD_S320_BONDED_SERVICE = getUuid("7F20CDC5-A9FC-4C70-9292-3ACF30323353")

    val UUID_SPRINGCARD_S370_PLAIN_SERVICE = getUuid("F91C914F-367C-4108-AC3E-3D3030373353")
    val UUID_SPRINGCARD_S370_BONDED_SERVICE = getUuid("7F20CDC5-A9FC-4C70-9292-3ACF30373353")

    /* Access to CCID characteristic transparently whereas it's bonded or not */

    var isCcidServiceBonded = false

    val UUID_SPRINGCARD_CCID_SERVICE : UUID
    get() {
        return if( !isCcidServiceBonded)
            UUID_SPRINGCARD_CCID_PLAIN_SERVICE
        else
            UUID_SPRINGCARD_CCID_BONDED_SERVICE
    }

    val UUID_CCID_PC_TO_RDR_CHAR: UUID
    get() {
        return if( !isCcidServiceBonded)
            UUID_CCID_PC_TO_RDR_PLAIN_CHAR
        else
            UUID_CCID_PC_TO_RDR_BONDED_CHAR
    }

    val UUID_CCID_RDR_TO_PC_CHAR: UUID
    get() {
        return if( !isCcidServiceBonded)
            UUID_CCID_RDR_TO_PC_PLAIN_CHAR
        else
            UUID_CCID_RDR_TO_PC_BONDED_CHAR
    }

    val UUID_CCID_STATUS_CHAR: UUID
    get() {
        return if( !isCcidServiceBonded)
            UUID_CCID_STATUS_PLAIN_CHAR
        else
            UUID_CCID_STATUS_BONDED_CHAR
    }

}




