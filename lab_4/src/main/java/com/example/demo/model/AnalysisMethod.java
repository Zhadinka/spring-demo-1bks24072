package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Методика анализа: для какого материала, в каком диапазоне значений и с каким максимальным разбросом. */
@Entity
@Table(name = "analysis_methods")
@Getter
@Setter
@NoArgsConstructor
public class AnalysisMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "allowed_material", nullable = false)
    private String allowedMaterial;

    @Column(name = "min_value", nullable = false)
    private Double minValue;

    @Column(name = "max_value", nullable = false)
    private Double maxValue;

    @Column(name = "max_spread", nullable = false)
    private Double maxSpread;
}
