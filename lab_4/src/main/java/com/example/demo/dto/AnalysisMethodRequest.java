package com.example.demo.dto;

public record AnalysisMethodRequest(String name, String allowedMaterial,
                                    Double minValue, Double maxValue, Double maxSpread) {
}
