package com.example.demo.model;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Результат измерения в рамках исследования. */
@Entity
@Table(name = "measurements")
@Getter
@Setter
@NoArgsConstructor
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Analysis analysis;

    // столбец назван measured_value, т.к. VALUE - ключевое слово во многих СУБД
    @Column(name = "measured_value", nullable = false)
    private Double value;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(nullable = false)
    private Boolean confirmed;
}
