package com.example.demo.dto;

import java.time.Instant;

public record MeasurementResponse(Long id, Long analysisId, Double value, Instant measuredAt, boolean confirmed) {
}
