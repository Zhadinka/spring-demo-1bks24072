package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.model.AccountRole;
import com.example.demo.model.AccountStatus;
import com.example.demo.model.UserAccount;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthService;
import com.example.demo.security.LabUserPrincipal;

@SpringBootTest
class AuthServiceTests {

    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserAccountRepository accountRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationCreatesCustomerAndStoresOnlyBcryptHash() {
        UserAccount account = authService.register("Test Customer", "Test.User@example.com", "StrongPassword123!");

        assertEquals("test.user@example.com", account.getEmail());
        assertEquals(AccountRole.CUSTOMER, account.getRole());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertNotNull(account.getCustomerId());
        assertNotEquals("StrongPassword123!", account.getPasswordHash());
        assertTrue(passwordEncoder.matches("StrongPassword123!", account.getPasswordHash()));
    }

    @Test
    void registrationRejectsWeakPassword() {
        assertThrows(BadRequestException.class,
                () -> authService.register("Test Customer", "weak@example.com", "weak"));
        assertTrue(accountRepository.findAll().isEmpty());
    }

    @Test
    void registrationRejectsDuplicateEmailIgnoringCase() {
        authService.register("Test Customer", "same@example.com", "StrongPassword123!");
        assertThrows(ConflictException.class,
                () -> authService.register("Another Customer", "SAME@example.com", "AnotherStrong123!"));
    }

    @Test
    void accountStatusControlsAuthenticationEligibility() {
        UserAccount account = authService.register("Test Customer", "status@example.com", "StrongPassword123!");
        assertTrue(new LabUserPrincipal(account).isEnabled());

        account.setStatus(AccountStatus.LOCKED);
        assertFalse(new LabUserPrincipal(account).isAccountNonLocked());

        account.setStatus(AccountStatus.DISABLED);
        assertFalse(new LabUserPrincipal(account).isEnabled());
    }
}
