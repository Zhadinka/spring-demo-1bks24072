package com.example.demo.dto;

import com.example.demo.model.AnalysisStatus;

public record AnalysisResponse(Long id, Long sampleId, Long methodId, AnalysisStatus status, Double averageValue) {
}
