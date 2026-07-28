package com.jassi.expensetracker.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A role held by a {@link UserInfo}.
 *
 * {@code @Embeddable}, not {@code @Entity}: a UserRole has no identity or
 * lifecycle of its own — it exists only as part of a user, and is stored in the
 * `user_roles` collection table declared by UserInfo's {@code @ElementCollection}.
 *
 * {@code @Data} matters here: an @ElementCollection held in a Set relies on
 * equals/hashCode to deduplicate, so the generated implementations are
 * load-bearing rather than boilerplate.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @Column(name = "role_id")
    private String roleId;

    @Column(name = "name")
    private String name;
}
