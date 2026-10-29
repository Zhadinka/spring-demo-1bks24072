package com.example.demo.dto;

import java.time.Instant;

import com.example.demo.model.SampleStatus;

public record SampleResponse(Long id, String material, Long customerId, Instant receivedAt, SampleStatus status) {
}
