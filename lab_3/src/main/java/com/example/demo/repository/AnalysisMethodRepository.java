package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.AnalysisMethod;

public interface AnalysisMethodRepository extends JpaRepository<AnalysisMethod, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<AnalysisMethod> findByAllowedMaterialIgnoreCaseOrderById(String allowedMaterial);
}
