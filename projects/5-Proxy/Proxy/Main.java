package Proxy;

import java.lang.reflect.Proxy;

public class Main {
    public static void main(String[] args) {

        // 1) the real object
        UserService real = new UserServiceImpl();

        // 2) wrap it in a JDK dynamic proxy backed by our CacheableHandler.
        //    Proxy.newProxyInstance needs: a classloader, the interfaces the
        //    proxy should implement, and the handler with the extra logic.
        UserService service = (UserService) Proxy.newProxyInstance(
                real.getClass().getClassLoader(),
                real.getClass().getInterfaces(),   // { UserService.class }
                new CacheableHandler(real));

        // 3) the caller uses `service` as if it were a plain UserService,
        //    unaware it's actually a proxy.
        System.out.println("--- @Cacheable method (getUser) ---");
        System.out.println("result: " + service.getUser(5L));  // MISS -> DB hit
        System.out.println("result: " + service.getUser(5L));  // HIT  -> no DB hit
        System.out.println("result: " + service.getUser(9L));  // MISS -> different arg

        System.out.println();
        System.out.println("--- non-cached method (getTime) runs every time ---");
        System.out.println(service.getTime());
        System.out.println(service.getTime());  // different value -> proved not cached
    }
}
