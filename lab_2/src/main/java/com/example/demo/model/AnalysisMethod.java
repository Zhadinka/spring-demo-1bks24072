package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisMethod implements Identifiable {

    private Long id;
    private String name;
    private String allowedMaterial;
    private Double minValue;
    private Double maxValue;
    private Double maxSpread;
}
