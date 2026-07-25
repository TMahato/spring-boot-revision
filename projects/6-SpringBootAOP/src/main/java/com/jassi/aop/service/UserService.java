package com.jassi.aop.service;

import com.jassi.aop.model.User;
import org.springframework.stereotype.Service;

/**
 * The "business" class that the aspect will advise.
 *
 * @Service makes this a Spring-managed SINGLETON bean: Spring creates exactly
 * ONE instance and hands that same instance out everywhere it is @Autowired.
 * Because it is a bean, Spring can wrap it in a PROXY and weave advice around
 * its methods (that is why AOP only works on Spring-managed beans).
 *
 * None of the methods below contain any logging code — the logging is added
 * from OUTSIDE by LoggingAspect. That separation is the whole point of AOP.
 */
@Service
public class UserService {

    private final User user;

    public UserService() {
        // one demo user created when the singleton is built
        this.user = new User("Lovepreet Singh", 23, "Bangalore, India");
    }

    /** normal method → triggers @Before, @After, @Around, @AfterReturning. */
    public void logIn() {
        System.out.println("   [UserService] logging user in: " + user.getName());
    }

    /** returns a value → advice can read the returned object (@AfterReturning). */
    public User getUser() {
        System.out.println("   [UserService] fetching user");
        return user;
    }

    /** always throws → triggers @AfterThrowing (and @After, which runs anyway). */
    public void logOut() throws Exception {
        System.out.println("   [UserService] logging user out");
        throw new Exception("unable to log the user out");
    }
}
