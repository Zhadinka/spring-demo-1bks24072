package com.example.demo.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.Analysis;
import com.example.demo.model.AnalysisMethod;
import com.example.demo.model.AnalysisStatus;
import com.example.demo.model.Measurement;
import com.example.demo.repository.MeasurementRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeasurementService {

    private final MeasurementRepository measurementRepository;
    private final AnalysisService analysisService;
    private final Clock clock;

    public List<Measurement> findAll() {
        return measurementRepository.findAll(Sort.by("id"));
    }

    public List<Measurement> findByCustomerId(Long customerId) {
        return measurementRepository.findByAnalysis_Sample_Customer_IdOrderById(customerId);
    }

    public Measurement getByIdForCustomer(Long id, Long customerId) {
        return measurementRepository.findByIdAndAnalysis_Sample_Customer_Id(id, customerId)
                .orElseThrow(() -> new NotFoundException("Measurement not found: " + id));
    }

    public Measurement getById(Long id) {
        return measurementRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Measurement not found: " + id));
    }

    @Transactional
    public Measurement create(Long analysisIdRaw, Double valueRaw, Boolean confirmedRaw) {
        Long analysisId = Checks.requireNonNull(analysisIdRaw, "analysisId");
        double value = Checks.requireNumber(valueRaw, "value");
        Analysis analysis = analysisService.getReferencedForUpdate(analysisId);
        checkEditable(analysis);
        AnalysisMethod method = analysis.getMethod();
        checkRange(method, value);

        boolean confirmed = Boolean.TRUE.equals(confirmedRaw);
        if (confirmed) {
            checkSpread(analysisId, method, null, value);
        }

        Measurement measurement = new Measurement();
        measurement.setAnalysis(analysis);
        measurement.setValue(value);
        measurement.setMeasuredAt(clock.instant());     // время задаёт сервер
        measurement.setConfirmed(confirmed);
        measurementRepository.save(measurement);

        analysisService.recalculateAverage(analysis);
        return measurement;
    }

    @Transactional
    public Measurement update(Long id, Double valuePatch, Boolean confirmedPatch) {
        Measurement measurement = getById(id);
        Analysis analysis = analysisService.getForUpdate(measurement.getAnalysis().getId());
        checkEditable(analysis);
        AnalysisMethod method = analysis.getMethod();

        double value = measurement.getValue();
        boolean confirmed = Boolean.TRUE.equals(measurement.getConfirmed());
        if (valuePatch != null) {
            value = Checks.requireNumber(valuePatch, "value");
            checkRange(method, value);
        }
        if (confirmedPatch != null) {
            confirmed = confirmedPatch;
        }
        if (confirmed) {
            checkSpread(analysis.getId(), method, id, value);
        }

        measurement.setValue(value);
        measurement.setConfirmed(confirmed);
        measurementRepository.save(measurement);
        analysisService.recalculateAverage(analysis);
        return measurement;
    }

    @Transactional
    public void delete(Long id) {
        Measurement measurement = getById(id);
        Analysis analysis = analysisService.getForUpdate(measurement.getAnalysis().getId());
        checkEditable(analysis);

        measurementRepository.delete(measurement);
        measurementRepository.flush();
        analysisService.recalculateAverage(analysis);
    }

    //  правила

    private void checkEditable(Analysis analysis) {
        if (analysis.getStatus() != AnalysisStatus.IN_PROGRESS) {
            throw new ConflictException("Analysis " + analysis.getId() + " is " + analysis.getStatus()
                    + ": its measurements cannot be changed"
                    + (analysis.getStatus() == AnalysisStatus.COMPLETED
                    ? " (reopen it first: POST /api/analyses/" + analysis.getId() + "/reopen)" : ""));
        }
    }

    private void checkRange(AnalysisMethod method, double value) {
        if (value < method.getMinValue() || value > method.getMaxValue()) {
            throw new BadRequestException("Value " + value + " is outside the allowed range ["
                    + method.getMinValue() + "; " + method.getMaxValue() + "] of method '" + method.getName() + "'");
        }
    }

    private void checkSpread(Long analysisId, AnalysisMethod method, Long excludedId, double newValue) {
        List<Double> values = new ArrayList<>();
        for (Measurement m : measurementRepository.findByAnalysis_IdAndConfirmedTrueOrderById(analysisId)) {
            if (!m.getId().equals(excludedId)) {
                values.add(m.getValue());
            }
        }
        values.add(newValue);
        double spread = Statistics.spread(values);
        if (spread > method.getMaxSpread() + Statistics.EPSILON) {
            throw new ConflictException("Spread of confirmed measurements would be " + Statistics.round(spread)
                    + ", which exceeds the maximum " + method.getMaxSpread() + " of method '" + method.getName() + "'");
        }
    }
}
