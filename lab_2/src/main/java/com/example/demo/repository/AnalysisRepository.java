package com.example.demo.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.demo.model.Analysis;

@Repository
public class AnalysisRepository extends InMemoryRepository<Analysis> {

    public List<Analysis> findBySampleId(Long sampleId) {
        return stream().filter(a -> sampleId.equals(a.getSampleId())).toList();
    }

    public List<Analysis> findByMethodId(Long methodId) {
        return stream().filter(a -> methodId.equals(a.getMethodId())).toList();
    }

    public boolean existsBySampleId(Long sampleId) {
        return stream().anyMatch(a -> sampleId.equals(a.getSampleId()));
    }

    public boolean existsByMethodId(Long methodId) {
        return stream().anyMatch(a -> methodId.equals(a.getMethodId()));
    }
}
