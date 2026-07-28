package com.jassi.expensetracker.repositories;

import com.jassi.expensetracker.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long>
{
    // Id type is Long: RefreshToken.id is an AUTO_INCREMENT column.
    Optional<RefreshToken> findByToken(String token);

}
