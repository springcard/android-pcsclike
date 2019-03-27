package com.springcard.pcsclike_sample

import android.content.Context
import com.google.gson.Gson

class AppPreferences(private val ctx: Context) {

    private val options = "optionsPcscApp"
    private val enableLogName = "enableLog"
    private val stopOnErrorName = "stopOnError"
    private val enableTimeMeasurementName = "enableTimeMeasurement"
    private val useAuthentificationName = "useAuthentication"
    private val authenticationKeyName = "authenticationKey"
    private val authenticationKeyIndexName = "authenticationKeyIndex"
    private val apduModelsName = "apduModels"
    private val defaultApdusName = "defaultApdus"

    var enableLog: Boolean
        get() {
            
            val sp = ctx.getSharedPreferences(options, 0)
            return sp.getBoolean(enableLogName, true)
        }
        set(value) {
            val editor =  ctx.getSharedPreferences(options, 0).edit()
            editor.putBoolean(enableLogName, value)
            editor.apply()
        }

    var stopOnError: Boolean
        get() {
            val sp = ctx.getSharedPreferences(options, 0)
            return sp.getBoolean(stopOnErrorName, false)
        }
        set(value) {
            val editor =  ctx.getSharedPreferences(options, 0).edit()
            editor.putBoolean(stopOnErrorName, value)
            editor.apply()
        }

    var enableTimeMeasurement: Boolean
        get() {
            val sp = ctx.getSharedPreferences(options, 0)
            return sp.getBoolean(enableTimeMeasurementName, false)
        }
        set(value) {
            val editor =  ctx.getSharedPreferences(options, 0).edit()
            editor.putBoolean(enableTimeMeasurementName, value)
            editor.apply()
        }



    var useAuthentication: Boolean
        get() {
            val sp = ctx.getSharedPreferences(options, 0)
            return sp.getBoolean(useAuthentificationName, false)
        }
        set(value) {
            val editor =  ctx.getSharedPreferences(options, 0).edit()
            editor.putBoolean(useAuthentificationName, value)
            editor.apply()
        }

    var authenticationKey: String
        get() {
            val sp = ctx.getSharedPreferences(options, 0)
            return sp.getString(authenticationKeyName, "00000000000000000000000000000000")!!
        }
        set(value) {
            val editor =  ctx.getSharedPreferences(options, 0).edit()
            editor.putString(authenticationKeyName, value)
            editor.apply()
        }

    var authenticationKeyIndex: Int
        get() {
            val sp = ctx.getSharedPreferences(options, 0)
            return sp.getInt(authenticationKeyIndexName, 0)
        }
        set(value) {
            val editor =  ctx.getSharedPreferences(options, 0).edit()
            editor.putInt(authenticationKeyIndexName, value)
            editor.apply()
        }

    var modelsApdusJson: String
        get() {
            val sp = ctx.getSharedPreferences(options, 0)
            return sp.getString(apduModelsName, "[{\"id\":0,\"title\":\"Card\\u0027s ATR\",\"mode\":0,\"apdu\":\"ff:ca:fa:00\",\"created\":\"2017-03-28T09:31:40+00:00\",\"modified\":\"2017-03-28T09:31:40+00:00\",\"group_id\":null,\"group\":\"\"}]")!!
        }
        set(value) {
            val editor =  ctx.getSharedPreferences(options, 0).edit()
            editor.putString(apduModelsName, value)
            editor.apply()
        }

    var defaultApdus: ApduModel?
        get() {
            val sp = ctx.getSharedPreferences(options, 0)
            val json = sp.getString(defaultApdusName, "")
            return Gson().fromJson<ApduModel>(json, ApduModel::class.java)
        }
        set(value) {
            val editor =  ctx.getSharedPreferences(options, 0).edit()
            editor.putString(defaultApdusName, Gson().toJson(value))
            editor.apply()
        }
}