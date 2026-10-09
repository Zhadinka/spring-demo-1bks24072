package com.example.demo.service;

import com.example.demo.exception.BadRequestException;

final class Checks {

    private Checks() {
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Field '" + field + "' is required and must not be blank");
        }
        return value.trim();
    }

    static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new BadRequestException("Field '" + field + "' is required");
        }
        return value;
    }

    static double requireNumber(Double value, String field) {
        double number = requireNonNull(value, field);
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            throw new BadRequestException("Field '" + field + "' must be a finite number");
        }
        return number;
    }
}
