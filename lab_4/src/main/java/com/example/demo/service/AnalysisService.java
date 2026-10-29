package com.example.demo.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
import com.example.demo.model.Sample;
import com.example.demo.model.SampleStatus;
import com.example.demo.repository.AnalysisMethodRepository;
import com.example.demo.repository.AnalysisRepository;
import com.example.demo.repository.MeasurementRepository;
import com.example.demo.repository.SampleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Порядок блокировок во всех операциях одинаковый: сначала проба, затем исследование.
 * Операции над измерениями блокируют только исследование. Так параллельные запросы не взаимоблокируются
 * и не теряют обновления (например, два одновременных завершения исследований одной пробы).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final MeasurementRepository measurementRepository;
    private final SampleRepository sampleRepository;
    private final AnalysisMethodRepository methodRepository;
    private final SampleService sampleService;

    //  чтение

    public List<Analysis> findAll() {
        return analysisRepository.findAll(Sort.by("id"));
    }

    public List<Analysis> findByCustomerId(Long customerId) {
        return analysisRepository.findBySample_Customer_IdOrderById(customerId);
    }

    public Analysis getByIdForCustomer(Long id, Long customerId) {
        return analysisRepository.findByIdAndSample_Customer_Id(id, customerId)
                .orElseThrow(() -> new NotFoundException("Analysis not found: " + id));
    }

    public Analysis getById(Long id) {
        return analysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found: " + id));
    }

    /** Исследование с блокировкой строки; для операций над его измерениями. */
    @Transactional
    public Analysis getForUpdate(Long id) {
        return analysisRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found: " + id));
    }

    /** То же, но отсутствие исследования - ошибка в теле запроса (400), а не отсутствие ресурса (404). */
    @Transactional
    public Analysis getReferencedForUpdate(Long id) {
        return analysisRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BadRequestException("Analysis not found: " + id));
    }

    // CRUD

    @Transactional
    public Analysis create(Long sampleIdRaw, Long methodIdRaw) {
        Long sampleId = Checks.requireNonNull(sampleIdRaw, "sampleId");
        Long methodId = Checks.requireNonNull(methodIdRaw, "methodId");
        Sample sample = sampleRepository.findByIdForUpdate(sampleId)
                .orElseThrow(() -> new BadRequestException("Sample not found: " + sampleId));
        AnalysisMethod method = findMethod(methodId);
        LabRules.checkSampleAcceptsAnalyses(sample);
        LabRules.checkMaterial(sample, method);
        checkNotOrderedYet(sampleId, methodId);

        Analysis analysis = new Analysis();
        analysis.setSample(sample);
        analysis.setMethod(method);
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        analysisRepository.save(analysis);

        sample.setStatus(SampleStatus.IN_PROGRESS);
        return analysis;
    }

    @Transactional
    public Analysis update(Long id, Long sampleIdPatch, Long methodIdPatch, AnalysisStatus status) {
        Long oldSampleId = analysisRepository.findSampleIdById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found: " + id));
        Long newSampleId = sampleIdPatch != null ? sampleIdPatch : oldSampleId;

        // блокируем пробы по возрастанию id, затем исследование
        Map<Long, Sample> samples = new HashMap<>();
        for (Long sid : new TreeSet<>(List.of(oldSampleId, newSampleId))) {
            Sample sample = sampleRepository.findByIdForUpdate(sid).orElse(null);
            if (sample == null) {
                throw sid.equals(oldSampleId)
                        ? new NotFoundException("Analysis not found: " + id)
                        : new BadRequestException("Sample not found: " + sid);
            }
            samples.put(sid, sample);
        }
        Analysis analysis = getForUpdate(id);

        Long methodId = methodIdPatch != null ? methodIdPatch : analysis.getMethod().getId();
        boolean sampleChanged = !newSampleId.equals(oldSampleId);
        boolean methodChanged = !methodId.equals(analysis.getMethod().getId());
        if (sampleChanged || methodChanged) {
            requireInProgress(analysis, "Sample and method can be changed only while the analysis is IN_PROGRESS");
            if (measurementRepository.existsByAnalysis_Id(id)) {
                throw new ConflictException("Sample and method cannot be changed: the analysis already has measurements");
            }
            Sample sample = samples.get(newSampleId);
            AnalysisMethod method = findMethod(methodId);
            if (sampleChanged) {
                LabRules.checkSampleAcceptsAnalyses(sample);
            }
            LabRules.checkMaterial(sample, method);
            checkNotOrderedYet(newSampleId, methodId);
            analysis.setSample(sample);
            analysis.setMethod(method);
        }
        if (status != null && status != analysis.getStatus()) {
            switch (status) {
                case COMPLETED -> completeLocked(analysis);
                case IN_PROGRESS -> reopenLocked(analysis);
                case CANCELLED -> throw new BadRequestException(
                        "Status CANCELLED is set only by rejecting the sample: POST /api/samples/{id}/reject");
            }
        }
        analysisRepository.save(analysis);

        sampleService.recalculateStatus(oldSampleId);
        if (sampleChanged) {
            sampleService.recalculateStatus(newSampleId);
        }
        return analysis;
    }

    /** Измерения исследования удаляются каскадом (ON DELETE CASCADE в БД); статус пробы пересчитывается. */
    @Transactional
    public void delete(Long id) {
        Analysis analysis = lockWithSample(id);
        Long sampleId = analysis.getSample().getId();
        analysisRepository.delete(analysis);
        analysisRepository.flush();
        sampleService.recalculateStatus(sampleId);
    }

    //  бизнес-операции

    /**
     * Бизнес-операция 3 (Analysis + Measurement + AnalysisMethod): подтвердить сразу все неподтверждённые
     * измерения исследования. Всё или ничего: если после подтверждения разброс превысит допустимый для методики,
     * не подтверждается ни одно измерение. Допустима только для исследования IN_PROGRESS.
     */
    @Transactional
    public Analysis confirmMeasurements(Long id) {
        Analysis analysis = getForUpdate(id);
        requireInProgress(analysis, "Measurements can be confirmed only while the analysis is IN_PROGRESS");

        List<Measurement> all = measurementRepository.findByAnalysis_IdOrderById(id);
        List<Measurement> pending = all.stream().filter(m -> !Boolean.TRUE.equals(m.getConfirmed())).toList();
        if (pending.isEmpty()) {
            throw new ConflictException("Analysis " + id + " has no unconfirmed measurements");
        }
        List<Double> values = all.stream().map(Measurement::getValue).toList();
        double maxSpread = analysis.getMethod().getMaxSpread();
        double spread = Statistics.spread(values);
        if (spread > maxSpread + Statistics.EPSILON) {
            throw new ConflictException("Measurements were not confirmed: their spread would be "
                    + Statistics.round(spread) + ", which exceeds the maximum " + maxSpread);
        }

        pending.forEach(m -> m.setConfirmed(true));
        analysis.setAverageValue(Statistics.round(Statistics.average(values)));
        return analysis;
    }

    /**
     * Бизнес-операция 4 (Analysis + Measurement + Sample): завершить исследование. Нужно хотя бы одно
     * подтверждённое измерение, разброс подтверждённых значений не должен превышать допустимый для методики.
     * Если завершены все исследования пробы - проба тоже становится COMPLETED.
     */
    @Transactional
    public Analysis complete(Long id) {
        Analysis analysis = lockWithSample(id);
        completeLocked(analysis);
        analysisRepository.save(analysis);
        sampleService.recalculateStatus(analysis.getSample().getId());
        return analysis;
    }

    /**
     * Бизнес-операция 5 (Analysis + Sample): вернуть завершённое исследование в работу (например, чтобы
     * добавить измерение). Проба при этом снова становится IN_PROGRESS. Нельзя для отклонённой пробы.
     */
    @Transactional
    public Analysis reopen(Long id) {
        Analysis analysis = lockWithSample(id);
        reopenLocked(analysis);
        analysisRepository.save(analysis);
        sampleService.recalculateStatus(analysis.getSample().getId());
        return analysis;
    }

    // расчёты

    /** Итоговое среднее считается только по подтверждённым измерениям. */
    @Transactional
    public void recalculateAverage(Analysis analysis) {
        List<Double> values = confirmedValues(analysis.getId());
        analysis.setAverageValue(values.isEmpty() ? null : Statistics.round(Statistics.average(values)));
        analysisRepository.save(analysis);
    }

    //  правила

    private void completeLocked(Analysis analysis) {
        requireInProgress(analysis, "Only an IN_PROGRESS analysis can be completed");
        List<Double> values = confirmedValues(analysis.getId());
        if (values.isEmpty()) {
            throw new ConflictException("Analysis cannot be COMPLETED: it has no confirmed measurements");
        }
        double maxSpread = analysis.getMethod().getMaxSpread();
        if (Statistics.spread(values) > maxSpread + Statistics.EPSILON) {
            throw new ConflictException("Analysis cannot be COMPLETED: spread of confirmed measurements exceeds "
                    + maxSpread);
        }
        analysis.setAverageValue(Statistics.round(Statistics.average(values)));
        analysis.setStatus(AnalysisStatus.COMPLETED);
    }

    private void reopenLocked(Analysis analysis) {
        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            throw new ConflictException("Only a COMPLETED analysis can be reopened, but analysis "
                    + analysis.getId() + " is " + analysis.getStatus());
        }
        if (analysis.getSample().getStatus() == SampleStatus.REJECTED) {
            throw new ConflictException("Sample " + analysis.getSample().getId()
                    + " is REJECTED, its analyses cannot be reopened");
        }
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
    }

    private void requireInProgress(Analysis analysis, String message) {
        if (analysis.getStatus() != AnalysisStatus.IN_PROGRESS) {
            throw new ConflictException(message + " (analysis " + analysis.getId() + " is "
                    + analysis.getStatus() + ")");
        }
    }

    private void checkNotOrderedYet(Long sampleId, Long methodId) {
        if (analysisRepository.existsBySample_IdAndMethod_Id(sampleId, methodId)) {
            throw new ConflictException("Method " + methodId + " is already ordered for sample " + sampleId);
        }
    }

    private AnalysisMethod findMethod(Long id) {
        return methodRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Analysis method not found: " + id));
    }

    private Analysis lockWithSample(Long analysisId) {
        Long sampleId = analysisRepository.findSampleIdById(analysisId)
                .orElseThrow(() -> new NotFoundException("Analysis not found: " + analysisId));
        sampleRepository.findByIdForUpdate(sampleId);
        return getForUpdate(analysisId);
    }

    private List<Double> confirmedValues(Long analysisId) {
        return measurementRepository.findByAnalysis_IdAndConfirmedTrueOrderById(analysisId).stream()
                .map(Measurement::getValue)
                .toList();
    }
}
