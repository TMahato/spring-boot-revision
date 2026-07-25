package Proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/*
 * The InvocationHandler = the code that runs for EVERY method called on the
 * proxy. This one implements @Cacheable behaviour, which is exactly what Spring
 * generates for you behind the scenes.
 *
 * Flow for each call:
 *   1. use REFLECTION to find the real method and check for @Cacheable
 *   2. if not annotated -> just delegate to the real method (no caching)
 *   3. if annotated     -> build a key from method name + args
 *        - key already in cache -> return cached value, SKIP the real method
 *        - key missing          -> run the real method, store result, return it
 */
public class CacheableHandler implements InvocationHandler {

    private final Object target;                              // the real object we wrap
    private final Map<String, Object> cache = new HashMap<>(); // our cache store

    public CacheableHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        // --- REFLECTION: at runtime, look up the real method on the target
        //     class so we can read the annotations it declares. ---
        Method targetMethod = target.getClass()
                .getMethod(method.getName(), method.getParameterTypes());

        // Method not marked @Cacheable -> no caching, just call through.
        if (!targetMethod.isAnnotationPresent(Cacheable.class)) {
            return method.invoke(target, args);
        }

        // Build a cache key from the method name + its arguments.
        String key = method.getName() + "::" + Arrays.toString(args);

        if (cache.containsKey(key)) {                 // CACHE HIT
            System.out.println("[cache HIT ] " + key + " -> real method skipped");
            return cache.get(key);
        }

        System.out.println("[cache MISS] " + key + " -> running real method");
        Object result = method.invoke(target, args); // run the REAL method
        cache.put(key, result);                        // store for next time
        return result;
    }
}
