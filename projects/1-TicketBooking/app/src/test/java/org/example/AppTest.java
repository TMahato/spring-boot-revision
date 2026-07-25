package org.example;

import org.example.util.UserServiceUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class AppTest {

    @Test
    public void hashedPasswordVerifies() {
        String hashed = UserServiceUtil.hashPassword("secret123");
        assertTrue("correct password should verify", UserServiceUtil.checkPassword("secret123", hashed));
        assertFalse("wrong password should not verify", UserServiceUtil.checkPassword("wrong", hashed));
    }
}
