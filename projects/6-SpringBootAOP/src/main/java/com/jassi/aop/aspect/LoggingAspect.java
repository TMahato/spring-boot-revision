package com.jassi.aop.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * The ASPECT — the module that bundles the "logging" cross-cutting concern.
 *
 *   @Aspect    → marks this class as an aspect (holds advice + pointcuts)
 *   @Component → makes it a Spring bean so Spring can WEAVE it into targets
 *
 * VOCABULARY, mapped to this file:
 *   Aspect      = this whole class
 *   Advice      = each advised method below (@Before / @After / @Around ...)
 *   Join point  = an actual method execution on UserService (a JoinPoint object)
 *   Pointcut    = the expression selecting WHICH methods (userServiceMethods())
 *   Weaving     = Spring wrapping UserService in a proxy so this advice fires
 */
@Aspect
@Component
public class LoggingAspect {

    /* ─────────────────────────── POINTCUTS ───────────────────────────
     * A named, reusable pointcut. The empty method body is just a hook the
     * expression hangs on; you reference it by name in the advice below.
     *
     * execution(  * com.jassi.aop.service.UserService.*(..)  )
     *   *   → any return type
     *   .*  → any method name in that class
     *   (..)→ any number/type of arguments
     */
    @Pointcut("execution(* com.jassi.aop.service.UserService.*(..))")
    public void userServiceMethods() {}

    /* A second pointcut selecting exactly one method, to show targeting. */
    @Pointcut("execution(void com.jassi.aop.service.UserService.logIn())")
    public void loginMethod() {}


    /* ─────────────────────────── @Before ─────────────────────────────
     * Runs BEFORE every matched method. JoinPoint gives metadata about the
     * method being intercepted (name, args, target).
     */
    @Before("userServiceMethods()")
    public void logBefore(JoinPoint jp) {
        System.out.println("[@Before]         → about to run: " + jp.getSignature().toShortString());
    }


    /* ─────────────────────────── @After ──────────────────────────────
     * Runs AFTER the method, no matter what — normal return OR exception
     * (like a finally block). Good for cleanup / "method finished" logs.
     */
    @After("userServiceMethods()")
    public void logAfter(JoinPoint jp) {
        System.out.println("[@After]          → finished (ok or error): " + jp.getSignature().getName());
    }


    /* ────────────────────── @AfterReturning ──────────────────────────
     * Runs ONLY if the method returned normally. `returning` binds the
     * returned value so the advice can read/log it.
     */
    @AfterReturning(pointcut = "userServiceMethods()", returning = "result")
    public void logAfterReturning(JoinPoint jp, Object result) {
        System.out.println("[@AfterReturning] → " + jp.getSignature().getName() + " returned: " + result);
    }


    /* ────────────────────── @AfterThrowing ───────────────────────────
     * Runs ONLY if the method throws. `throwing` binds the exception so you
     * can log/alert on failures (here: UserService.logOut()).
     */
    @AfterThrowing(pointcut = "userServiceMethods()", throwing = "ex")
    public void logAfterThrowing(JoinPoint jp, Throwable ex) {
        System.out.println("[@AfterThrowing]  → " + jp.getSignature().getName() + " threw: " + ex.getMessage());
    }


    /* ─────────────────────────── @Around ─────────────────────────────
     * The most powerful advice: it WRAPS the method. You control what runs
     * before and after, and you must call proceed() to actually run the real
     * method. (Skip proceed() to short-circuit it — that's how @Cacheable
     * returns a cached value without running the method.)
     *
     * Here we time only logIn() using the loginMethod() pointcut.
     */
    @Around("loginMethod()")
    public Object timeAround(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("[@Around]         → START timing " + pjp.getSignature().getName());
        long start = System.nanoTime();

        Object result = pjp.proceed();          // ← run the real logIn()

        long micros = (System.nanoTime() - start) / 1_000;
        System.out.println("[@Around]         → END   took " + micros + " micros");
        return result;                          // may modify/replace the result
    }
}
