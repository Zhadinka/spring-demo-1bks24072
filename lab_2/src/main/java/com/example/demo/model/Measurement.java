package com.example.demo.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Measurement implements Identifiable {

    private Long id;
    private Long analysisId;
    private Double value;
    private Instant measuredAt;
    private Boolean confirmed;
}
