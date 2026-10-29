package com.example.demo.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public List<User> findAll() {
        return userRepository.findAll(Sort.by("id"));
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    @Transactional
    public User create(String nameRaw, String emailRaw) {
        String name = Checks.requireText(nameRaw, "name");
        String email = normalizeEmail(emailRaw);
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("User with email '" + email + "' already exists");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        return userRepository.save(user);
    }

    @Transactional
    public User update(Long id, String nameRaw, String emailRaw) {
        User user = getById(id);
        if (nameRaw != null) {
            user.setName(Checks.requireText(nameRaw, "name"));
        }
        if (emailRaw != null) {
            String email = normalizeEmail(emailRaw);
            if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
                throw new ConflictException("User with email '" + email + "' already exists");
            }
            user.setEmail(email);
        }
        return userRepository.save(user);
    }

    /** Пробы, исследования и измерения пользователя удаляются каскадом (ON DELETE CASCADE в БД). */
    @Transactional
    public void delete(Long id) {
        userRepository.delete(getById(id));
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }
}
