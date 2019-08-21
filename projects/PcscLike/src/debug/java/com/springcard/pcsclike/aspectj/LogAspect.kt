/**
 * Copyright (c) 2018-2019 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package  com.springcard.pcsclike.aspectj

import android.util.Log
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation .Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.CodeSignature




@Aspect
class LogAscpect {

    @Pointcut("execution(public * com.springcard.pcsclike..*(..)) ")
    fun methodAnnotatedWithDebugTrace() {}

    /* Do not use AOP on internal calls and utils.* */
    @Pointcut("execution(public * com.springcard.pcsclike.utils.*.*(..)) " +
            "|| cflow(call(* com.springcard.pcsclike..*(..)))")
    fun excludedMethodAnnotatedWithDebugTrace() {}

    @Before("methodAnnotatedWithDebugTrace() && !excludedMethodAnnotatedWithDebugTrace()")
    fun externalCall(joinPoint: JoinPoint) {
        val methodSignature = joinPoint.signature as MethodSignature
        val className = methodSignature.declaringType.simpleName
        val methodName = methodSignature.method.name
        //if(methodName != "run")
            Log.d(className, "--> $methodName()")
    }

    @Before("methodAnnotatedWithDebugTrace() && excludedMethodAnnotatedWithDebugTrace()")
    fun internalCall(joinPoint: JoinPoint) {
        val methodSignature = joinPoint.signature as MethodSignature
        val className = methodSignature.declaringType.simpleName
        val methodName = methodSignature.method.name
        Log.d(className, "$methodName()")
    }
}