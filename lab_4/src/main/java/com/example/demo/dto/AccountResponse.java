package com.example.demo.dto;

import com.example.demo.model.AccountRole;
import com.example.demo.model.AccountStatus;

public record AccountResponse(Long id, String email, AccountRole role, AccountStatus status, Long customerId) {
}
