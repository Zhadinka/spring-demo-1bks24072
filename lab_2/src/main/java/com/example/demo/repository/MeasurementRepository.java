package com.example.demo.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.demo.model.Measurement;

@Repository
public class MeasurementRepository extends InMemoryRepository<Measurement> {

    public List<Measurement> findByAnalysisId(Long analysisId) {
        return stream().filter(m -> analysisId.equals(m.getAnalysisId())).toList();
    }

    public List<Measurement> findConfirmedByAnalysisId(Long analysisId) {
        return stream()
                .filter(m -> analysisId.equals(m.getAnalysisId()) && Boolean.TRUE.equals(m.getConfirmed()))
                .toList();
    }

    public void deleteByAnalysisId(Long analysisId) {
        findByAnalysisId(analysisId).forEach(m -> deleteById(m.getId()));
    }
}
