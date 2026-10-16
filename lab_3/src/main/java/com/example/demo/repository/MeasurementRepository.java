package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.Measurement;

public interface MeasurementRepository extends JpaRepository<Measurement, Long> {

    List<Measurement> findByAnalysis_IdOrderById(Long analysisId);

    List<Measurement> findByAnalysis_IdAndConfirmedTrueOrderById(Long analysisId);

    boolean existsByAnalysis_Id(Long analysisId);
}
