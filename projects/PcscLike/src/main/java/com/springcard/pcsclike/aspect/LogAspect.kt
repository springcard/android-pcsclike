/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.pcsclike.aspect

import android.util.Log
import com.springcard.pcsclike.BuildConfig
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation .Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.aspectj.lang.annotation.Before


@Aspect
internal class LogAscpect {

    @Pointcut("execution(public * com.springcard.pcsclike..*(..)) ")
    fun methodAnnotatedWithDebugTrace() {}

    /* Do not use AOP on internal calls (and utils.*, but it does not work..) */
    @Pointcut("call(public * com.springcard.pcsclike.utils.*(..)) " +
            "|| cflow(call(* com.springcard.pcsclike..*(..)))")
    fun excludedMethodAnnotatedWithDebugTrace() {}


    @Before("methodAnnotatedWithDebugTrace() && !excludedMethodAnnotatedWithDebugTrace()")
    fun weaveJoinPoint(joinPoint: JoinPoint) {
        if(BuildConfig.libraryDebug) {
            val methodSignature = joinPoint.signature as MethodSignature
            val className = methodSignature.declaringType.simpleName
            val methodName = methodSignature.method.name
            Log.d(className, "--> $methodName")
        }
    }
}