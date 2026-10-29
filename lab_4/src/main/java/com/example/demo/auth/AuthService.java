package com.example.demo.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.model.AccountRole;
import com.example.demo.model.AccountStatus;
import com.example.demo.model.User;
import com.example.demo.model.UserAccount;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserAccount register(String nameRaw, String emailRaw, String password) {
        String name = requireText(nameRaw, "name");
        String email = normalizeEmail(emailRaw);
        validatePassword(password);

        if (accountRepository.existsByEmailIgnoreCase(email) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("The email address is already in use");
        }

        // Отдельный бизнес-профиль нужен для проверки владения пробами. Роль всегда CUSTOMER.
        User customer = new User();
        customer.setName(name);
        customer.setEmail(email);
        customer = userRepository.save(customer);

        UserAccount account = new UserAccount();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(AccountRole.CUSTOMER); // клиент не может прислать роль или статус
        account.setStatus(AccountStatus.ACTIVE);
        account.setCustomerId(customer.getId());
        return accountRepository.save(account);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Field '" + field + "' must not be blank");
        }
        return value.trim();
    }

    private static String normalizeEmail(String raw) {
        String email = requireText(raw, "email").toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BadRequestException("A valid email address is required");
        }
        return email;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must contain 12-72 UTF-8 bytes");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean special = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!upper || !lower || !digit || !special || password.chars().anyMatch(Character::isWhitespace)) {
            throw new BadRequestException(
                    "Password must include uppercase and lowercase letters, a digit and a special character, without whitespace");
        }
    }
}
