package com.reader.Novel.Reader.repository;

import com.reader.Novel.Reader.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByName(String name);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByNameIgnoreCase(String name);
    Optional<User> findFirstByNameIgnoreCase(String name);
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameIgnoreCase(String username);
}
