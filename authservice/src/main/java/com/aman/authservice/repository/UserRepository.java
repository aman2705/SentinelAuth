package com.aman.authservice.repository;


import com.aman.authservice.entities.UserInfo;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends CrudRepository<UserInfo, String> {
    Optional<UserInfo>  findByUsername(String username);
    Optional<UserInfo> findByEmail(String email);
}