package com.jassi.expensetracker.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(name = "tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    // IDENTITY (a MySQL AUTO_INCREMENT column) rather than a manually assigned
    // string: unlike userId, this id is never carried across a service boundary,
    // so the database is free to generate it.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    // One user -> many refresh tokens. A real foreign key on tokens.user_id.
    //
    // EAGER on purpose: TokenController.refreshToken() maps straight from the
    // token to getUserInfo().getUsername() outside any transaction, so a LAZY
    // proxy would throw LazyInitializationException there.
    //
    // Excluded from toString/equals/hashCode because @Data would otherwise walk
    // into UserInfo on every log line and comparison.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private UserInfo userInfo;
}
