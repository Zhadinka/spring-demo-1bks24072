package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.exception.NotFoundException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SampleService sampleService;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    public User create(User input) {
        String name = Checks.requireText(input.getName(), "name");
        User user = new User(null, name, normalizeEmail(input.getEmail()));
        return userRepository.save(user);
    }

    public User update(Long id, User patch) {
        User user = getById(id);
        String name = patch.getName() != null ? Checks.requireText(patch.getName(), "name") : user.getName();
        String email = patch.getEmail() != null ? normalizeEmail(patch.getEmail()) : user.getEmail();
        user.setName(name);
        user.setEmail(email);
        return userRepository.save(user);
    }

    public void delete(Long id) {
        getById(id);
        sampleService.deleteByCustomerId(id);
        userRepository.deleteById(id);
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }
}
