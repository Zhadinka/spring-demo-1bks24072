package com.example.demo.repository;

import org.springframework.stereotype.Repository;

import com.example.demo.model.User;

@Repository
public class UserRepository extends InMemoryRepository<User> {
}
