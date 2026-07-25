package com.jassi.aop.controller;

import com.jassi.aop.library.PaymentGateway;
import com.jassi.aop.model.User;
import com.jassi.aop.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller to trigger the advised methods from a browser / curl.
 *
 * @Autowired asks Spring for the UserService SINGLETON — Spring injects the one
 * shared instance (actually the PROXY of it, which is what makes the aspects
 * fire). Same for the PaymentGateway "library" bean.
 */
@RestController
public class Api {

    private final UserService userService;
    private final PaymentGateway paymentGateway;

    // constructor injection — Spring autowires the singletons in
    @Autowired
    public Api(UserService userService, PaymentGateway paymentGateway) {
        this.userService = userService;
        this.paymentGateway = paymentGateway;
    }

    /** GET / → triggers @Before, @After, @AfterReturning, @Around. */
    @GetMapping("/login")
    public String login() {
        System.out.println("\n--- /login ---");
        userService.logIn();
        return "login called — check the console for the aspect logs";
    }

    /** GET /user → returns a value, so @AfterReturning can read it. */
    @GetMapping("/user")
    public User user() {
        System.out.println("\n--- /user ---");
        return userService.getUser();
    }

    /** GET /logout → throws, so @AfterThrowing fires (and @After too). */
    @GetMapping("/logout")
    public String logout() {
        System.out.println("\n--- /logout ---");
        try {
            userService.logOut();
        } catch (Exception e) {
            return "logout failed: " + e.getMessage() + " — check the console";
        }
        return "unreachable";
    }

    /** GET /pay → triggers the LIBRARY logging aspect on code we can't edit. */
    @GetMapping("/pay")
    public String pay() {
        System.out.println("\n--- /pay ---");
        String txn = paymentGateway.charge("acct-42", 99.50);
        paymentGateway.refund(txn);
        return "payment flow ran — check the console for [LIB ...] logs";
    }
}
