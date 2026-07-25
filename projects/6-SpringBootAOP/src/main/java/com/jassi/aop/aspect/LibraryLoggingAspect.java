package com.jassi.aop.aspect;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * THE KEY USE CASE: logging methods that live inside a library we cannot edit.
 *
 * We never touched PaymentGateway's source. This aspect's pointcut matches
 * every method in the library package, and @Around weaves our logging around
 * each call — invoking OUR external method inside the LIBRARY method calls.
 *
 * Pointcut: execution(* com.jassi.aop.library..*(..))
 *   the ".." after the package = that package AND all sub-packages.
 */
@Aspect
@Component
public class LibraryLoggingAspect {

    @Around("execution(* com.jassi.aop.library..*(..))")
    public Object logLibraryCall(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        System.out.println("[LIB @Before] calling " + method + " args=" + Arrays.toString(pjp.getArgs()));

        Object result = pjp.proceed();          // run the real library method

        System.out.println("[LIB @After]  " + method + " returned " + result);
        return result;
    }
}
