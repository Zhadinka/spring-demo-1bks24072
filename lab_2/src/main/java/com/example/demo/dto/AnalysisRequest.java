package com.example.demo.dto;

import com.example.demo.model.AnalysisStatus;

public record AnalysisRequest(Long sampleId, Long methodId, AnalysisStatus status) {
}
