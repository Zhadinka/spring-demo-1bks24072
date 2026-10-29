package com.example.demo.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.demo.model.AccountRole;
import com.example.demo.model.AccountStatus;
import com.example.demo.model.UserAccount;

/** Безопасное представление учетной записи в контексте текущего запроса. */
public final class LabUserPrincipal implements UserDetails, CredentialsContainer {

    private final Long accountId;
    private final Long customerId;
    private final String email;
    private String passwordHash;
    private final AccountRole role;
    private final AccountStatus status;

    public LabUserPrincipal(UserAccount account) {
        this.accountId = account.getId();
        this.customerId = account.getCustomerId();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash();
        this.role = account.getRole();
        this.status = account.getStatus();
    }

    public Long getAccountId() { return accountId; }
    public Long getCustomerId() { return customerId; }
    public AccountRole getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public boolean isCustomer() { return role == AccountRole.CUSTOMER; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return status != AccountStatus.LOCKED; }
    @Override public boolean isEnabled() { return status == AccountStatus.ACTIVE; }

    /** После успешной аутентификации Spring Security очищает хеш из principal. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
