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

import com.example.demo.dto.AnalysisMethodRequest;
import com.example.demo.dto.AnalysisMethodResponse;
import com.example.demo.model.AnalysisMethod;
import com.example.demo.service.AnalysisMethodService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/analysis-methods")
@RequiredArgsConstructor
public class AnalysisMethodController {

    private final AnalysisMethodService service;

    @GetMapping
    public List<AnalysisMethodResponse> getAll() {
        return service.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public AnalysisMethodResponse getById(@PathVariable("id") Long id) {
        return toResponse(service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisMethodResponse create(@RequestBody AnalysisMethodRequest request) {
        return toResponse(service.create(toEntity(request)));
    }

    @PatchMapping("/{id}")
    public AnalysisMethodResponse update(@PathVariable("id") Long id, @RequestBody AnalysisMethodRequest request) {
        return toResponse(service.update(id, toEntity(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }

    private AnalysisMethod toEntity(AnalysisMethodRequest request) {
        return new AnalysisMethod(null, request.name(), request.allowedMaterial(), request.minValue(), request.maxValue(), request.maxSpread());
    }

    private AnalysisMethodResponse toResponse(AnalysisMethod entity) {
        return new AnalysisMethodResponse(entity.getId(), entity.getName(), entity.getAllowedMaterial(), entity.getMinValue(), entity.getMaxValue(), entity.getMaxSpread());
    }
}
