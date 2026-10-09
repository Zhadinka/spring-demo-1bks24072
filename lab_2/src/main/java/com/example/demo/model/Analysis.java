package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Analysis implements Identifiable {

    private Long id;
    private Long sampleId;
    private Long methodId;
    private AnalysisStatus status;
    private Double averageValue;
}
