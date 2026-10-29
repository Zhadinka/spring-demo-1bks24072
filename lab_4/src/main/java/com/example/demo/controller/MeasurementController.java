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

import com.example.demo.dto.MeasurementRequest;
import com.example.demo.dto.MeasurementResponse;
import com.example.demo.model.Measurement;
import com.example.demo.service.MeasurementService;
import com.example.demo.security.LabUserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/measurements")
@RequiredArgsConstructor
public class MeasurementController {

    private final MeasurementService service;

    @GetMapping
    public List<MeasurementResponse> getAll(@AuthenticationPrincipal LabUserPrincipal principal) {
        var measurements = principal.isCustomer()
                ? service.findByCustomerId(principal.getCustomerId()) : service.findAll();
        return measurements.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public MeasurementResponse getById(@PathVariable("id") Long id,
                                       @AuthenticationPrincipal LabUserPrincipal principal) {
        return toResponse(principal.isCustomer()
                ? service.getByIdForCustomer(id, principal.getCustomerId()) : service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse create(@RequestBody MeasurementRequest request) {
        return toResponse(service.create(request.analysisId(), request.value(), request.confirmed()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    public MeasurementResponse update(@PathVariable("id") Long id, @RequestBody MeasurementRequest request) {
        return toResponse(service.update(id, request.value(), request.confirmed()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }


    private MeasurementResponse toResponse(Measurement entity) {
        return new MeasurementResponse(entity.getId(), entity.getAnalysis().getId(), entity.getValue(), entity.getMeasuredAt(), Boolean.TRUE.equals(entity.getConfirmed()));
    }
}
