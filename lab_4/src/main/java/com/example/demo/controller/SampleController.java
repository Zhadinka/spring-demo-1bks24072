package com.example.demo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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

import com.example.demo.dto.AnalysisResponse;
import com.example.demo.dto.SampleRequest;
import com.example.demo.dto.SampleResponse;
import com.example.demo.model.Analysis;
import com.example.demo.model.Sample;
import com.example.demo.service.SampleService;
import com.example.demo.security.LabUserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService service;

    @GetMapping
    public List<SampleResponse> getAll(@AuthenticationPrincipal LabUserPrincipal principal) {
        var samples = principal.isCustomer() ? service.findByCustomerId(principal.getCustomerId()) : service.findAll();
        return samples.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public SampleResponse getById(@PathVariable("id") Long id,
                                  @AuthenticationPrincipal LabUserPrincipal principal) {
        return toResponse(principal.isCustomer()
                ? service.getByIdForCustomer(id, principal.getCustomerId()) : service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SampleResponse create(@RequestBody SampleRequest request,
                                 @AuthenticationPrincipal LabUserPrincipal principal) {
        Long customerId = request.customerId();
        if (principal.isCustomer()) {
            if (customerId != null && !customerId.equals(principal.getCustomerId())) {
                throw new AccessDeniedException("A customer can create samples only for their own account");
            }
            customerId = principal.getCustomerId();
        }
        return toResponse(service.create(request.material(), customerId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    public SampleResponse update(@PathVariable("id") Long id, @RequestBody SampleRequest request) {
        return toResponse(service.update(id, request.material(), request.customerId(), request.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        service.delete(id);
    }


    //бизнес-операции

    /** Назначить пробе все подходящие по материалу методики, которые ещё не назначены. */
    @PostMapping("/{id}/order-analyses")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AnalysisResponse> orderAnalyses(@PathVariable("id") Long id,
                                                @AuthenticationPrincipal LabUserPrincipal principal) {
        var analyses = principal.isCustomer()
                ? service.orderAnalysesForCustomer(id, principal.getCustomerId())
                : service.orderAnalyses(id);
        return analyses.stream().map(this::toAnalysisResponse).toList();
    }

    /** Отклонить пробу; её незавершённые исследования отменяются. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'LAB_TECHNICIAN')")
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
