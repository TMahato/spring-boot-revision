package com.jassi.aop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Application entry point.
 *
 * @SpringBootApplication  → component-scans this package and below, so every
 *                           @Service / @RestController / @Aspect is picked up.
 * @EnableAspectJAutoProxy → turns on the proxy machinery that WEAVES aspects
 *                           into beans. (Spring Boot enables this automatically
 *                           when spring-boot-starter-aop is present; shown here
 *                           to make the mechanism explicit.)
 */
@SpringBootApplication
@EnableAspectJAutoProxy
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
