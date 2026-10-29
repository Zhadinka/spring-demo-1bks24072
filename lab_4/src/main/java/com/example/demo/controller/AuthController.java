package com.example.demo.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.example.demo.auth.AuthService;
import com.example.demo.dto.AccountResponse;
import com.example.demo.dto.CsrfTokenResponse;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.model.UserAccount;
import com.example.demo.security.LabUserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        // getToken() материализует отложенный токен и сохраняет его в HTTP-сессии.
        CsrfTokenResponse body = new CsrfTokenResponse(
                csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@RequestBody RegisterRequest request) {
        return toResponse(authService.register(request.name(), request.email(), request.password()));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public AccountResponse me(@AuthenticationPrincipal LabUserPrincipal principal) {
        return new AccountResponse(principal.getAccountId(), principal.getUsername(), principal.getRole(),
                principal.getStatus(), principal.getCustomerId());
    }

    private AccountResponse toResponse(UserAccount account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getRole(),
                account.getStatus(), account.getCustomerId());
    }
}
