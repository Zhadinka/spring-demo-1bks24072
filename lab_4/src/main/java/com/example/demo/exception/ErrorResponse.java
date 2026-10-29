package com.example.demo.exception;

public record ErrorResponse(int status, String error, String message) {
}
