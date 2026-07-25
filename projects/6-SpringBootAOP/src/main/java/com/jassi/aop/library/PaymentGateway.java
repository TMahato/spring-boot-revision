package com.jassi.aop.library;

import org.springframework.stereotype.Component;

/**
 * Pretend this is a THIRD-PARTY LIBRARY class.
 *
 * The scenario: you use a library and want to log some of its methods, but you
 * do NOT have access to its source, so you cannot add log statements inside
 * charge() / refund(). AOP solves this: you write an aspect whose POINTCUT
 * matches these methods, and Spring WEAVES your external logging code around
 * them — your log runs "inside" the library call without touching its source.
 *
 * (In a real app this would come from a Maven/Gradle dependency. For the demo
 * we mark it @Component so it becomes a Spring bean that can be proxied. NOTE:
 * plain Spring AOP can only advise Spring-managed beans — if a library creates
 * its objects with `new` internally, you'd need full AspectJ load-time weaving.)
 */
@Component
public class PaymentGateway {

    public String charge(String account, double amount) {
        System.out.println("   [LIBRARY] charging " + amount + " to " + account);
        return "TXN-" + System.currentTimeMillis();
    }

    public void refund(String txnId) {
        System.out.println("   [LIBRARY] refunding " + txnId);
    }
}
