package com.jassi.expensetracker.repositories;

import com.jassi.expensetracker.entities.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserInfo, String>
{
    // <UserInfo, String> — the id type is String because userId is a UUID
    // assigned by the service, not a generated number.
    public UserInfo findByUsername(String username);
}
