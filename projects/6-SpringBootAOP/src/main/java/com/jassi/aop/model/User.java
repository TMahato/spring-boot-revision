package com.jassi.aop.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A plain data class built with Lombok — no hand-written boilerplate.
 *
 * @AllArgsConstructor → generates User(String name, int age, String address)
 * @Getter / @Setter   → generates getName()/setName(), getAge()/setAge(), ...
 * @ToString           → generates a readable toString() (nice for logging)
 *
 * Lombok generates all of this at COMPILE time via annotation processing, so
 * the .class file really has the constructor/getters — reflection & Spring see
 * them normally.
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
public class User {
    private String name;
    private int age;
    private String address;
}
