package com.jassi.expensetracker.repositories;

import com.jassi.expensetracker.entities.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String>
{
    Optional<RefreshToken> findByToken(String token);

}
