package com.example.demo.dto;

public record AnalysisMethodResponse(Long id, String name, String allowedMaterial,
                                     Double minValue, Double maxValue, Double maxSpread) {
}
