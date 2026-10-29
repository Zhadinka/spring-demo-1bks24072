package com.example.demo.dto;

import com.example.demo.model.SampleStatus;

public record SampleRequest(String material, Long customerId, SampleStatus status) {
}
