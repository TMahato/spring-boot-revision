package Proxy;

/*
 * The REAL object (the "target"). Notice it contains ZERO caching code — the
 * caching is added from the outside by the proxy. This is the whole point of
 * AOP: keep the cross-cutting concern (caching) out of the business class.
 */
public class UserServiceImpl implements UserService {

    @Cacheable                       // our marker: "the proxy should cache this"
    @Override
    public String getUser(Long id) {
        // Pretend this is a slow DB call. It should run only on a cache MISS.
        System.out.println("   >> DB hit for user " + id + " (slow work ran)");
        return "User-" + id;
    }

    @Override
    public String getTime() {        // no @Cacheable -> proxy will NOT cache it
        return "time=" + System.nanoTime();
    }
}
