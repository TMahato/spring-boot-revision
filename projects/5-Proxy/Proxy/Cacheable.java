package Proxy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
 * Our own tiny copy of Spring's @Cacheable.
 *
 * RUNTIME retention is essential: the proxy reads this annotation with
 * reflection WHILE the program runs, so it must survive into the .class file
 * and be visible at runtime (SOURCE/CLASS retention would be invisible then).
 */
@Retention(RetentionPolicy.RUNTIME)   // keep it available for reflection at runtime
@Target(ElementType.METHOD)           // can only be placed on methods
public @interface Cacheable {
}
