package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.model.Analysis;

import jakarta.persistence.LockModeType;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    List<Analysis> findBySample_IdOrderById(Long sampleId);

    List<Analysis> findByMethod_IdOrderById(Long methodId);

    boolean existsBySample_Id(Long sampleId);

    boolean existsByMethod_Id(Long methodId);

    boolean existsBySample_IdAndMethod_Id(Long sampleId, Long methodId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Analysis a where a.id = :id")
    Optional<Analysis> findByIdForUpdate(@Param("id") Long id);

    /** Нужен, чтобы сначала заблокировать пробу, а потом исследование (единый порядок блокировок). */
    @Query("select a.sample.id from Analysis a where a.id = :id")
    Optional<Long> findSampleIdById(@Param("id") Long id);
}
