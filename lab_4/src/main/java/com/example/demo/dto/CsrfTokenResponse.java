package com.example.demo.dto;

public record CsrfTokenResponse(String headerName, String parameterName, String token) {
}
