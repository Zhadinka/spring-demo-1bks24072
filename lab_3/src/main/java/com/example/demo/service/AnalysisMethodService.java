package com.example.demo.service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.AnalysisMethod;
import com.example.demo.repository.AnalysisMethodRepository;
import com.example.demo.repository.AnalysisRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisMethodService {

    private final AnalysisMethodRepository methodRepository;
    private final AnalysisRepository analysisRepository;
    private final SampleService sampleService;

    public List<AnalysisMethod> findAll() {
        return methodRepository.findAll(Sort.by("id"));
    }

    public AnalysisMethod getById(Long id) {
        return methodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis method not found: " + id));
    }

    @Transactional
    public AnalysisMethod create(String nameRaw, String materialRaw, Double minRaw, Double maxRaw, Double spreadRaw) {
        String name = Checks.requireText(nameRaw, "name");
        String material = Checks.requireText(materialRaw, "allowedMaterial");
        double min = Checks.requireNumber(minRaw, "minValue");
        double max = Checks.requireNumber(maxRaw, "maxValue");
        double spread = Checks.requireNumber(spreadRaw, "maxSpread");
        validateLimits(min, max, spread);
        if (methodRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Analysis method '" + name + "' already exists");
        }

        AnalysisMethod method = new AnalysisMethod();
        method.setName(name);
        method.setAllowedMaterial(material);
        method.setMinValue(min);
        method.setMaxValue(max);
        method.setMaxSpread(spread);
        return methodRepository.save(method);
    }

    @Transactional
    public AnalysisMethod update(Long id, String nameRaw, String materialRaw, Double minRaw, Double maxRaw,
                                 Double spreadRaw) {
        AnalysisMethod method = getById(id);
        String name = nameRaw != null ? Checks.requireText(nameRaw, "name") : method.getName();
        String material = materialRaw != null ? Checks.requireText(materialRaw, "allowedMaterial")
                : method.getAllowedMaterial();
        double min = minRaw != null ? Checks.requireNumber(minRaw, "minValue") : method.getMinValue();
        double max = maxRaw != null ? Checks.requireNumber(maxRaw, "maxValue") : method.getMaxValue();
        double spread = spreadRaw != null ? Checks.requireNumber(spreadRaw, "maxSpread") : method.getMaxSpread();
        validateLimits(min, max, spread);
        if (methodRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Analysis method '" + name + "' already exists");
        }

        boolean rulesChanged = !material.equalsIgnoreCase(method.getAllowedMaterial())
                || min != method.getMinValue()
                || max != method.getMaxValue()
                || spread != method.getMaxSpread();
        if (rulesChanged && analysisRepository.existsByMethod_Id(id)) {
            throw new ConflictException("Material, range and max spread cannot be changed: "
                    + "the method is already used by analyses");
        }

        method.setName(name);
        method.setAllowedMaterial(material);
        method.setMinValue(min);
        method.setMaxValue(max);
        method.setMaxSpread(spread);
        return methodRepository.save(method);
    }

    /**
     * Удаление методики удаляет её исследования и их измерения (ON DELETE CASCADE в БД),
     * после чего статусы затронутых проб пересчитываются.
     */
    @Transactional
    public void delete(Long id) {
        AnalysisMethod method = getById(id);
        // TreeSet - стабильный порядок блокировок проб, чтобы параллельные операции не взаимоблокировались
        Set<Long> affectedSamples = analysisRepository.findByMethod_IdOrderById(id).stream()
                .map(a -> a.getSample().getId())
                .collect(Collectors.toCollection(TreeSet::new));

        methodRepository.delete(method);
        methodRepository.flush();
        affectedSamples.forEach(sampleService::recalculateStatus);
    }

    private static void validateLimits(double min, double max, double spread) {
        if (min > max) {
            throw new BadRequestException("minValue must not be greater than maxValue");
        }
        if (spread < 0) {
            throw new BadRequestException("maxSpread must not be negative");
        }
    }
}
