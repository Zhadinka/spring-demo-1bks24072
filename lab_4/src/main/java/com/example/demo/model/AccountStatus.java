package com.example.demo.model;

/** Состояние учетной записи, учитываемое Spring Security при каждом входе. */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    DISABLED
}
