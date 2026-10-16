package com.example.demo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.demo.model.Sample;

import jakarta.persistence.LockModeType;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    /** Загрузка пробы с блокировкой строки (SELECT ... FOR UPDATE) - для бизнес-операций, меняющих её состояние. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sample s where s.id = :id")
    Optional<Sample> findByIdForUpdate(@Param("id") Long id);
}
