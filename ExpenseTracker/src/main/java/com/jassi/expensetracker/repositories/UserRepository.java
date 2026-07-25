package com.jassi.expensetracker.repositories;

import com.jassi.expensetracker.entities.UserInfo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends MongoRepository<UserInfo, String>
{
    public UserInfo findByUsername(String username);
}
