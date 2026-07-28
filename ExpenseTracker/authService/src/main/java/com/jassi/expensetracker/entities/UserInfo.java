package com.jassi.expensetracker.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    // Assigned manually as a UUID string in the service — NOT @GeneratedValue.
    // Note this means Spring Data sees a non-null id on a brand new entity and
    // routes save() through merge() (a SELECT then an INSERT) rather than
    // persist(). Correct, just one extra query per signup.
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false, unique = true)
    private String username;

    // WRITE_ONLY: Jackson still reads it from the signup request body, but never
    // writes it out — so the BCrypt hash can't leak into a Kafka record or a REST
    // response. JPA uses its own mapping, so persistence is unaffected.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    // Roles live in a `user_roles` collection table keyed by user_id, rather
    // than a full @ManyToMany against a separate roles table. @ElementCollection
    // fits because a UserRole has no identity or lifecycle of its own — it only
    // exists as part of a user.
    //
    // EAGER on purpose: CustomUserDetails iterates these during authentication,
    // outside any transaction. LAZY (the @ElementCollection default) would throw
    // LazyInitializationException on every login.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id")
    )
    private Set<UserRole> userRoles = new HashSet<>();
}
