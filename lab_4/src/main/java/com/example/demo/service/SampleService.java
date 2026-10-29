package com.example.demo.service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.Analysis;
import com.example.demo.model.AnalysisMethod;
import com.example.demo.model.AnalysisStatus;
import com.example.demo.model.Sample;
import com.example.demo.model.SampleStatus;
import com.example.demo.model.User;
import com.example.demo.repository.AnalysisMethodRepository;
import com.example.demo.repository.AnalysisRepository;
import com.example.demo.repository.SampleRepository;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SampleService {

    private final SampleRepository sampleRepository;
    private final UserRepository userRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisMethodRepository methodRepository;
    private final Clock clock;

    //  CRUD

    public List<Sample> findAll() {
        return sampleRepository.findAll(Sort.by("id"));
    }

    public List<Sample> findByCustomerId(Long customerId) {
        return sampleRepository.findByCustomer_IdOrderById(customerId);
    }

    public Sample getByIdForCustomer(Long id, Long customerId) {
        return sampleRepository.findByIdAndCustomer_Id(id, customerId)
                .orElseThrow(() -> new NotFoundException("Sample not found: " + id));
    }

    public Sample getById(Long id) {
        return sampleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sample not found: " + id));
    }

    @Transactional
    public Sample create(String materialRaw, Long customerId) {
        String material = Checks.requireText(materialRaw, "material");
        User customer = findCustomer(Checks.requireNonNull(customerId, "customerId"));

        Sample sample = new Sample();
        sample.setMaterial(material);
        sample.setCustomer(customer);
        sample.setReceivedAt(clock.instant());      // время задаёт сервер
        sample.setStatus(SampleStatus.RECEIVED);    // статус клиентом не задаётся
        return sampleRepository.save(sample);
    }

    @Transactional
    public Sample update(Long id, String materialRaw, Long customerId, SampleStatus status) {
        Sample sample = lock(id);

        if (materialRaw != null) {
            String material = Checks.requireText(materialRaw, "material");
            // исследования подобраны под материал пробы - менять его под ними нельзя
            if (!material.equalsIgnoreCase(sample.getMaterial()) && analysisRepository.existsBySample_Id(id)) {
                throw new ConflictException("Material cannot be changed: the sample already has analyses");
            }
            sample.setMaterial(material);
        }
        if (customerId != null) {
            sample.setCustomer(findCustomer(customerId));
        }
        if (status != null && status != sample.getStatus()) {
            if (status == SampleStatus.REJECTED) {
                rejectLocked(sample);
            } else {
                throw new ConflictException("Sample status is derived from its analyses and cannot be set to "
                        + status + " manually. To reject a sample use POST /api/samples/" + id + "/reject");
            }
        }
        return sampleRepository.save(sample);
    }

    /** Исследования и измерения пробы удаляются каскадом (ON DELETE CASCADE в БД). */
    @Transactional
    public void delete(Long id) {
        sampleRepository.delete(getById(id));
    }

    // бизнес-операции

    /**
     * Бизнес-операция 1 (Sample + AnalysisMethod + Analysis): назначить пробе все подходящие по материалу
     * методики, которые ей ещё не назначены. Результат зависит от текущего состояния: пробы (статус),
     * справочника методик и уже созданных исследований.
     */
    @Transactional
    public List<Analysis> orderAnalyses(Long sampleId) {
        return orderAnalysesLocked(lock(sampleId));
    }

    /** Проверка владельца и бизнес-операция выполняются под одной блокировкой пробы. */
    @Transactional
    public List<Analysis> orderAnalysesForCustomer(Long sampleId, Long customerId) {
        Sample sample = lock(sampleId);
        if (!sample.getCustomer().getId().equals(customerId)) {
            throw new NotFoundException("Sample not found: " + sampleId);
        }
        return orderAnalysesLocked(sample);
    }

    private List<Analysis> orderAnalysesLocked(Sample sample) {
        Long sampleId = sample.getId();
        LabRules.checkSampleAcceptsAnalyses(sample);

        Set<Long> alreadyOrdered = analysisRepository.findBySample_IdOrderById(sampleId).stream()
                .map(a -> a.getMethod().getId())
                .collect(Collectors.toSet());
        List<AnalysisMethod> suitable = methodRepository.findByAllowedMaterialIgnoreCaseOrderById(sample.getMaterial());
        if (suitable.isEmpty()) {
            throw new ConflictException("There are no analysis methods for material '" + sample.getMaterial() + "'");
        }

        List<Analysis> created = suitable.stream()
                .filter(method -> !alreadyOrdered.contains(method.getId()))
                .map(method -> {
                    Analysis analysis = new Analysis();
                    analysis.setSample(sample);
                    analysis.setMethod(method);
                    analysis.setStatus(AnalysisStatus.IN_PROGRESS);
                    return analysis;
                })
                .toList();
        if (created.isEmpty()) {
            throw new ConflictException("All suitable methods are already ordered for sample " + sampleId);
        }

        List<Analysis> saved = analysisRepository.saveAll(created);
        sample.setStatus(SampleStatus.IN_PROGRESS);
        return saved;
    }

    /**
     * Бизнес-операция 2 (Sample + Analysis): отклонить пробу. Допустима, пока проба не завершена и не отклонена.
     * Незавершённые исследования пробы отменяются (CANCELLED); завершённые и все измерения сохраняются.
     */
    @Transactional
    public Sample reject(Long sampleId) {
        return rejectLocked(lock(sampleId));
    }

    private Sample rejectLocked(Sample sample) {
        if (sample.getStatus() == SampleStatus.REJECTED) {
            throw new ConflictException("Sample " + sample.getId() + " is already REJECTED");
        }
        if (sample.getStatus() == SampleStatus.COMPLETED) {
            throw new ConflictException("Sample " + sample.getId() + " is COMPLETED and cannot be rejected");
        }
        analysisRepository.findBySample_IdOrderById(sample.getId()).stream()
                .filter(a -> a.getStatus() == AnalysisStatus.IN_PROGRESS)
                .forEach(a -> a.setStatus(AnalysisStatus.CANCELLED));
        sample.setStatus(SampleStatus.REJECTED);
        return sampleRepository.save(sample);
    }

    //  согласованность статуса

    /**
     * Статус пробы вычисляется по её исследованиям: нет исследований - RECEIVED, все завершены - COMPLETED,
     * иначе IN_PROGRESS. Отклонённая проба не меняется. Вызывается после любого изменения исследований.
     */
    @Transactional
    public void recalculateStatus(Long sampleId) {
        sampleRepository.findByIdForUpdate(sampleId).ifPresent(sample -> {
            if (sample.getStatus() == SampleStatus.REJECTED) {
                return;
            }
            List<Analysis> analyses = analysisRepository.findBySample_IdOrderById(sampleId);
            SampleStatus target;
            if (analyses.isEmpty()) {
                target = SampleStatus.RECEIVED;
            } else if (analyses.stream().allMatch(a -> a.getStatus() == AnalysisStatus.COMPLETED)) {
                target = SampleStatus.COMPLETED;
            } else {
                target = SampleStatus.IN_PROGRESS;
            }
            sample.setStatus(target);
        });
    }

    //  вспомогательное

    private Sample lock(Long id) {
        return sampleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Sample not found: " + id));
    }

    private User findCustomer(Long customerId) {
        return userRepository.findById(customerId)
                .orElseThrow(() -> new BadRequestException("User (customer) not found: " + customerId));
    }
}
