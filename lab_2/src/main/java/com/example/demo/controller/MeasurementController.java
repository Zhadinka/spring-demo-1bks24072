package com.example.demo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.MeasurementRequest;
import com.example.demo.dto.MeasurementResponse;
import com.example.demo.model.Measurement;
import com.example.demo.service.MeasurementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/measurements")
@RequiredArgsConstructor
public class MeasurementController {

    private final MeasurementService service;

    @GetMapping
    public List<MeasurementResponse> getAll() {
        return service.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public MeasurementResponse getById(@PathVariable("id") Long id) {
        return toResponse(service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse create(@RequestBody MeasurementRequest request) {
        return toResponse(service.create(toEntity(request)));
    }

    @PatchMapping("/{id}")
    public MeasurementResponse update(@PathVariable("id") Long id, @RequestBody MeasurementRequest request) {
        return toResponse(service.update(id, toEntity(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    private Measurement toEntity(MeasurementRequest request) {
        return new Measurement(null, request.analysisId(), request.value(), null, request.confirmed());
    }

    private MeasurementResponse toResponse(Measurement entity) {
        return new MeasurementResponse(entity.getId(), entity.getAnalysisId(), entity.getValue(), entity.getMeasuredAt(), Boolean.TRUE.equals(entity.getConfirmed()));
    }
}
