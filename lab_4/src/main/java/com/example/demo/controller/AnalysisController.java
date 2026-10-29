package com.example.demo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.AnalysisRequest;
import com.example.demo.dto.AnalysisResponse;
import com.example.demo.model.Analysis;
import com.example.demo.service.AnalysisService;
import com.example.demo.security.LabUserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService service;

    @GetMapping
    public List<AnalysisResponse> getAll(@AuthenticationPrincipal LabUserPrincipal principal) {
        var analyses = principal.isCustomer() ? service.findByCustomerId(principal.getCustomerId()) : service.findAll();
        return analyses.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public AnalysisResponse getById(@PathVariable("id") Long id,
                                    @AuthenticationPrincipal LabUserPrincipal principal) {
        return toResponse(principal.isCustomer()
                ? service.getByIdForCustomer(id, principal.getCustomerId()) : service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse create(@RequestBody AnalysisRequest request) {
        return toResponse(service.create(request.sampleId(), request.methodId()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    public AnalysisResponse update(@PathVariable("id") Long id, @RequestBody AnalysisRequest request) {
        return toResponse(service.update(id, request.sampleId(), request.methodId(), request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }


    // бизнес-операции

    /** Подтвердить все неподтверждённые измерения исследования (всё или ничего). */
    @PostMapping("/{id}/confirm-measurements")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    public AnalysisResponse confirmMeasurements(@PathVariable("id") Long id) {
        return toResponse(service.confirmMeasurements(id));
    }

    /** Завершить исследование; если завершены все исследования пробы - завершается и проба. */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    public AnalysisResponse complete(@PathVariable("id") Long id) {
        return toResponse(service.complete(id));
    }

    /** Вернуть завершённое исследование в работу. */
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    public AnalysisResponse reopen(@PathVariable("id") Long id) {
        return toResponse(service.reopen(id));
    }

    private AnalysisResponse toResponse(Analysis entity) {
        return new AnalysisResponse(entity.getId(), entity.getSample().getId(), entity.getMethod().getId(), entity.getStatus(), entity.getAverageValue());
    }
}
