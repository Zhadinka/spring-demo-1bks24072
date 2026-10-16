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

import com.example.demo.dto.AnalysisResponse;
import com.example.demo.dto.SampleRequest;
import com.example.demo.dto.SampleResponse;
import com.example.demo.model.Analysis;
import com.example.demo.model.Sample;
import com.example.demo.service.SampleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService service;

    @GetMapping
    public List<SampleResponse> getAll() {
        return service.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public SampleResponse getById(@PathVariable("id") Long id) {
        return toResponse(service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SampleResponse create(@RequestBody SampleRequest request) {
        return toResponse(service.create(request.material(), request.customerId()));
    }

    @PatchMapping("/{id}")
    public SampleResponse update(@PathVariable("id") Long id, @RequestBody SampleRequest request) {
        return toResponse(service.update(id, request.material(), request.customerId(), request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }


    //бизнес-операции

    /** Назначить пробе все подходящие по материалу методики, которые ещё не назначены. */
    @PostMapping("/{id}/order-analyses")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AnalysisResponse> orderAnalyses(@PathVariable("id") Long id) {
        return service.orderAnalyses(id).stream().map(this::toAnalysisResponse).toList();
    }

    /** Отклонить пробу; её незавершённые исследования отменяются. */
    @PostMapping("/{id}/reject")
    public SampleResponse reject(@PathVariable("id") Long id) {
        return toResponse(service.reject(id));
    }

    private AnalysisResponse toAnalysisResponse(Analysis entity) {
        return new AnalysisResponse(entity.getId(), entity.getSample().getId(), entity.getMethod().getId(),
                entity.getStatus(), entity.getAverageValue());
    }

    private SampleResponse toResponse(Sample entity) {
        return new SampleResponse(entity.getId(), entity.getMaterial(), entity.getCustomer().getId(), entity.getReceivedAt(), entity.getStatus());
    }
}
