package com.example.demo.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.example.demo.model.Sample;

@Repository
public class SampleRepository extends InMemoryRepository<Sample> {

    public List<Sample> findByCustomerId(Long customerId) {
        return stream().filter(s -> customerId.equals(s.getCustomerId())).toList();
    }
}
