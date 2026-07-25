package Proxy;

/*
 * The interface. A JDK dynamic proxy can ONLY proxy something that has an
 * interface — the generated proxy class implements this same interface, so the
 * caller can hold a UserService reference without knowing it's a proxy.
 */
public interface UserService {
    String getUser(Long id);   // marked @Cacheable in the impl -> will be cached
    String getTime();          // NOT cached -> runs every time
}
