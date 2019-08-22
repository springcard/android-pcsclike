/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */

package com.springcard.pcsclike.communication

import android.util.Log
import com.springcard.pcsclike.SCardReaderList


internal enum class State {
    Closed,
    Creating,
    Idle,
    Sleeping,
    WakingUp,
    WritingCmdAndWaitingResp,
    Closing
}


internal class DeviceMachineState(private val scardReaderList: SCardReaderList) {

    private val TAG = this::class.java.simpleName
    private var currentState: State = State.Closed
    private var isCreated = false

    fun getCurrentState(): State {
        return currentState
    }

    fun setNewState(newState: State) {

        val oldState = currentState
        currentState = newState
        var callback : (() -> Unit) ? = null

        Log.d(TAG, "State transition: $oldState -> $newState")

        when(newState) {
            State.Closed -> {
                when(oldState) {
                    State.Closing -> {
                        val uniqueId = SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice)
                        SCardReaderList.connectedScardReaderList.remove(uniqueId)

                        scardReaderList.exitExclusive()
                        /* If error arrive while creating */
                        /* Post with scardReaderList = null */
                        callback = if(isCreated) {
                            {scardReaderList.callbacks.onReaderListClosed(scardReaderList)}
                        } else {
                            {scardReaderList.callbacks.onReaderListClosed(null)}
                        }
                        isCreated = false
                    }
                    else -> {
                        Log.w(TAG, "Transition should not happen")
                        currentState = oldState
                    }
                }
            }
            State.Creating -> {
                when(oldState) {
                    State.Closed -> {
                        /* No callback */
                    }
                    else -> {
                        Log.w(TAG, "Transition should not happen")
                        currentState = oldState
                    }
                }
            }
            State.Idle -> {
                when(oldState) {
                    State.Idle -> {
                        /* card changed */
                        /* callback emitted from slot machine state */
                    }
                    State.Creating -> {
                        isCreated = true

                        val uniqueId = SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice)
                        SCardReaderList.knownSCardReaderList[uniqueId] = scardReaderList.constants
                        SCardReaderList.connectedScardReaderList.add(uniqueId)

                        scardReaderList.exitExclusive()
                        callback = {scardReaderList.callbacks.onReaderListCreated(scardReaderList)}
                    }
                    State.Sleeping -> {
                        callback = {scardReaderList.callbacks.onReaderListState(scardReaderList, scardReaderList.isSleeping)}
                    }
                    State.WakingUp -> {
                        scardReaderList.exitExclusive()
                        callback = {scardReaderList.callbacks.onReaderListState(scardReaderList, scardReaderList.isSleeping)}
                    }
                    State.WritingCmdAndWaitingResp -> {
                        scardReaderList.exitExclusive()
                        /* No callback here */
                        /* But callback emitted from slot machine state */
                    }
                    else -> {
                        Log.w(TAG, "Transition should not happen")
                        currentState = oldState
                    }
                }
                scardReaderList.mayConnectCard()
            }
            State.Sleeping -> {
                when(oldState) {
                    State.Idle -> {
                        callback = {scardReaderList.callbacks.onReaderListState(scardReaderList, scardReaderList.isSleeping)}
                    }
                    else -> {
                        Log.w(TAG, "Transition should not happen")
                        currentState = oldState
                    }
                }
            }
            State.WakingUp -> {
                when(oldState) {
                    State.Sleeping -> {
                        /* No callback */
                    }
                    else -> {
                        Log.w(TAG, "Transition should not happen")
                        currentState = oldState
                    }
                }
            }
            State.WritingCmdAndWaitingResp -> {
                when(oldState) {
                    State.Idle -> {

                        /* No callback */
                    }
                    else -> {
                        Log.w(TAG, "Transition should not happen")
                        currentState = oldState
                    }
                }
            }
            State.Closing -> {
                when(oldState) {
                    State.Creating, State.WritingCmdAndWaitingResp, State.WakingUp -> {
                        /* If error arrive while creating */
                        /* Post with scardReaderList = null */
                        callback = if(isCreated) {
                            {scardReaderList.callbacks.onReaderListError(scardReaderList, scardReaderList.lastError)}
                        } else {
                            {scardReaderList.callbacks.onReaderListError(null, scardReaderList.lastError)}
                        }
                    }
                    State.Sleeping -> {
                        callback = {scardReaderList.callbacks.onReaderListError(scardReaderList, scardReaderList.lastError)}
                    }
                    State.Idle -> {
                        /* No callback */
                    }
                    else -> {
                        Log.w(TAG, "Transition should not happen")
                        currentState = oldState
                    }
                }
            }
            else -> {
                Log.w(TAG, "Impossible new state: $newState")
                currentState = oldState
            }
        }


        if(callback != null) {
            scardReaderList.postCallback(callback)
        }

    }
}