package com.example.demo.dto;

public record MeasurementRequest(Long analysisId, Double value, Boolean confirmed) {
}
