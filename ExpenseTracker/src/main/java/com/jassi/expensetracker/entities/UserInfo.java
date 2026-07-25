package com.jassi.expensetracker.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    @Id
    private String userId;   // assigned manually as a UUID string in the service

    @Indexed(unique = true)
    private String username;

    private String password;

    // In MongoDB there are no join tables — roles are embedded documents on the user.
    private Set<UserRole> userRoles = new HashSet<>();
}
