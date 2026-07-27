package com.jassi.expensetracker.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // WRITE_ONLY: Jackson still reads it from the signup request body, but never
    // writes it out — so the BCrypt hash can't leak into a Kafka record or a REST
    // response. Spring Data Mongo uses its own converter, so persistence is unaffected.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    // In MongoDB there are no join tables — roles are embedded documents on the user.
    private Set<UserRole> userRoles = new HashSet<>();
}
