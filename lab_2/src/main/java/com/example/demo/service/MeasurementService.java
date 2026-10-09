package com.example.demo.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

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
public class MeasurementService {

    private final MeasurementRepository measurementRepository;
    private final AnalysisService analysisService;
    private final Clock clock;

    public List<Measurement> findAll() {
        return measurementRepository.findAll();
    }

    public Measurement getById(Long id) {
        return measurementRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Measurement not found: " + id));
    }

    public Measurement create(Measurement input) {
        Long analysisId = Checks.requireNonNull(input.getAnalysisId(), "analysisId");
        double value = Checks.requireNumber(input.getValue(), "value");
        Analysis analysis = analysisService.getReferenced(analysisId);
        checkEditable(analysis);
        AnalysisMethod method = analysisService.getMethodOf(analysis);
        checkRange(method, value);

        boolean confirmed = Boolean.TRUE.equals(input.getConfirmed());
        if (confirmed) {
            checkSpread(analysisId, method, null, value);
        }

        Measurement measurement = measurementRepository.save(
                new Measurement(null, analysisId, value, clock.instant(), confirmed));
        analysisService.recalculateAverage(analysisId);
        return measurement;
    }

    public Measurement update(Long id, Measurement patch) {
        Measurement measurement = getById(id);
        Analysis analysis = analysisService.getById(measurement.getAnalysisId());
        checkEditable(analysis);
        AnalysisMethod method = analysisService.getMethodOf(analysis);

        double value = measurement.getValue();
        boolean confirmed = Boolean.TRUE.equals(measurement.getConfirmed());
        if (patch.getValue() != null) {
            value = Checks.requireNumber(patch.getValue(), "value");
            checkRange(method, value);
        }
        if (patch.getConfirmed() != null) {
            confirmed = patch.getConfirmed();
        }
        if (confirmed) {
            checkSpread(analysis.getId(), method, id, value);
        }

        measurement.setValue(value);
        measurement.setConfirmed(confirmed);
        measurementRepository.save(measurement);
        analysisService.recalculateAverage(analysis.getId());
        return measurement;
    }

    public void delete(Long id) {
        Measurement measurement = getById(id);
        Analysis analysis = analysisService.getById(measurement.getAnalysisId());
        checkEditable(analysis);

        measurementRepository.deleteById(id);
        analysisService.recalculateAverage(analysis.getId());
    }

    // правила

    private void checkEditable(Analysis analysis) {
        if (analysis.getStatus() == AnalysisStatus.COMPLETED) {
            throw new ConflictException("Analysis " + analysis.getId()
                    + " is COMPLETED; reopen it (status IN_PROGRESS) to change its measurements");
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
        for (Measurement m : measurementRepository.findConfirmedByAnalysisId(analysisId)) {
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
