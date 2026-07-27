package com.jassi.expensetracker.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    private String id;   // Mongo ObjectId (assigned by the DB)

    @Indexed(unique = true)
    private String token;

    private Instant expiryDate;

    // One user -> many refresh tokens. @DBRef stores a reference to the user document.
    @DBRef
    private UserInfo userInfo;
}
