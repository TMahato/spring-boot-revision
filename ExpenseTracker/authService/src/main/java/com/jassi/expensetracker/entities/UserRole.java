package com.jassi.expensetracker.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedded inside a {@link UserInfo} document (no separate collection / join table).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    private String roleId;
    private String name;
}
