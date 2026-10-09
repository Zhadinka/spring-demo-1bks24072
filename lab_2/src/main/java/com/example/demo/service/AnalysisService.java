package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.Analysis;
import com.example.demo.model.AnalysisMethod;
import com.example.demo.model.AnalysisStatus;
import com.example.demo.model.Measurement;
import com.example.demo.model.Sample;
import com.example.demo.model.SampleStatus;
import com.example.demo.repository.AnalysisMethodRepository;
import com.example.demo.repository.AnalysisRepository;
import com.example.demo.repository.MeasurementRepository;
import com.example.demo.repository.SampleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final MeasurementRepository measurementRepository;
    private final SampleRepository sampleRepository;
    private final AnalysisMethodRepository methodRepository;

    //чтение

    public List<Analysis> findAll() {
        return analysisRepository.findAll();
    }

    public Analysis getById(Long id) {
        return analysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found: " + id));
    }

    public Analysis getReferenced(Long id) {
        return analysisRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Analysis not found: " + id));
    }

    public List<Analysis> findBySampleId(Long sampleId) {
        return analysisRepository.findBySampleId(sampleId);
    }

    public boolean existsBySampleId(Long sampleId) {
        return analysisRepository.existsBySampleId(sampleId);
    }

    public boolean existsByMethodId(Long methodId) {
        return analysisRepository.existsByMethodId(methodId);
    }

    public AnalysisMethod getMethodOf(Analysis analysis) {
        return methodRepository.findById(analysis.getMethodId())
                .orElseThrow(() -> new IllegalStateException("Method of analysis " + analysis.getId() + " is missing"));
    }

    // создание/изменение

    public Analysis create(Analysis input) {
        Long sampleId = Checks.requireNonNull(input.getSampleId(), "sampleId");
        Long methodId = Checks.requireNonNull(input.getMethodId(), "methodId");
        Sample sample = findSample(sampleId);
        AnalysisMethod method = findMethod(methodId);
        checkSampleAcceptsAnalyses(sample);
        checkMaterial(sample, method);

        Analysis analysis = analysisRepository.save(
                new Analysis(null, sampleId, methodId, AnalysisStatus.IN_PROGRESS, null));
        syncSampleStatus(sampleId);
        return analysis;
    }

    public Analysis update(Long id, Analysis patch) {
        Analysis analysis = getById(id);
        Long oldSampleId = analysis.getSampleId();
        Long sampleId = patch.getSampleId() != null ? patch.getSampleId() : oldSampleId;
        Long methodId = patch.getMethodId() != null ? patch.getMethodId() : analysis.getMethodId();
        AnalysisStatus status = patch.getStatus() != null ? patch.getStatus() : analysis.getStatus();

        boolean sampleChanged = !sampleId.equals(oldSampleId);
        boolean methodChanged = !methodId.equals(analysis.getMethodId());
        if (sampleChanged || methodChanged) {
            if (!measurementRepository.findByAnalysisId(id).isEmpty()) {
                throw new ConflictException("Sample and method cannot be changed: the analysis already has measurements");
            }
            Sample sample = findSample(sampleId);
            AnalysisMethod method = findMethod(methodId);
            if (sampleChanged) {
                checkSampleAcceptsAnalyses(sample);
            }
            checkMaterial(sample, method);
        }
        if (status == AnalysisStatus.COMPLETED && analysis.getStatus() != AnalysisStatus.COMPLETED) {
            checkCanComplete(analysis);
        }

        analysis.setSampleId(sampleId);
        analysis.setMethodId(methodId);
        analysis.setStatus(status);
        analysisRepository.save(analysis);

        syncSampleStatus(oldSampleId);
        if (sampleChanged) {
            syncSampleStatus(sampleId);
        }
        return analysis;
    }

    public void recalculateAverage(Long analysisId) {
        Analysis analysis = getById(analysisId);
        List<Double> values = confirmedValues(analysisId);
        analysis.setAverageValue(values.isEmpty() ? null : Statistics.round(Statistics.average(values)));
        analysisRepository.save(analysis);
    }

    // удаление

    public void delete(Long id) {
        Analysis analysis = getById(id);
        removeWithMeasurements(analysis);
        syncSampleStatus(analysis.getSampleId());
    }

    public void deleteBySampleId(Long sampleId) {
        analysisRepository.findBySampleId(sampleId).forEach(this::removeWithMeasurements);
    }

    public void deleteByMethodId(Long methodId) {
        analysisRepository.findByMethodId(methodId).forEach(analysis -> {
            removeWithMeasurements(analysis);
            syncSampleStatus(analysis.getSampleId());
        });
    }

    private void removeWithMeasurements(Analysis analysis) {
        measurementRepository.deleteByAnalysisId(analysis.getId());
        analysisRepository.deleteById(analysis.getId());
    }

    // правила

    private Sample findSample(Long id) {
        return sampleRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Sample not found: " + id));
    }

    private AnalysisMethod findMethod(Long id) {
        return methodRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Analysis method not found: " + id));
    }

    private void checkMaterial(Sample sample, AnalysisMethod method) {
        if (!method.getAllowedMaterial().equalsIgnoreCase(sample.getMaterial())) {
            throw new BadRequestException("Method '" + method.getName() + "' is intended for material '"
                    + method.getAllowedMaterial() + "', but the sample material is '" + sample.getMaterial() + "'");
        }
    }

    private void checkSampleAcceptsAnalyses(Sample sample) {
        if (sample.getStatus() == SampleStatus.COMPLETED || sample.getStatus() == SampleStatus.REJECTED) {
            throw new ConflictException("Sample " + sample.getId() + " is " + sample.getStatus()
                    + " and cannot get new analyses");
        }
    }

    private void checkCanComplete(Analysis analysis) {
        List<Double> values = confirmedValues(analysis.getId());
        if (values.isEmpty()) {
            throw new ConflictException("Analysis cannot be COMPLETED: it has no confirmed measurements");
        }
        double maxSpread = getMethodOf(analysis).getMaxSpread();
        if (Statistics.spread(values) > maxSpread + Statistics.EPSILON) {
            throw new ConflictException("Analysis cannot be COMPLETED: spread of confirmed measurements exceeds "
                    + maxSpread);
        }
    }

    private List<Double> confirmedValues(Long analysisId) {
        return measurementRepository.findConfirmedByAnalysisId(analysisId).stream()
                .map(Measurement::getValue)
                .toList();
    }

    private void syncSampleStatus(Long sampleId) {
        sampleRepository.findById(sampleId).ifPresent(sample -> {
            if (sample.getStatus() == SampleStatus.REJECTED) {
                return;
            }
            List<Analysis> analyses = analysisRepository.findBySampleId(sampleId);
            SampleStatus target;
            if (analyses.isEmpty()) {
                target = SampleStatus.RECEIVED;
            } else if (analyses.stream().allMatch(a -> a.getStatus() == AnalysisStatus.COMPLETED)) {
                target = SampleStatus.COMPLETED;
            } else {
                target = SampleStatus.IN_PROGRESS;
            }
            if (target != sample.getStatus()) {
                sample.setStatus(target);
                sampleRepository.save(sample);
            }
        });
    }
}
