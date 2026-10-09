package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.AnalysisMethod;
import com.example.demo.repository.AnalysisMethodRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalysisMethodService {

    private final AnalysisMethodRepository methodRepository;
    private final AnalysisService analysisService;

    public List<AnalysisMethod> findAll() {
        return methodRepository.findAll();
    }

    public AnalysisMethod getById(Long id) {
        return methodRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis method not found: " + id));
    }

    public AnalysisMethod create(AnalysisMethod input) {
        String name = Checks.requireText(input.getName(), "name");
        String material = Checks.requireText(input.getAllowedMaterial(), "allowedMaterial");
        double min = Checks.requireNumber(input.getMinValue(), "minValue");
        double max = Checks.requireNumber(input.getMaxValue(), "maxValue");
        double spread = Checks.requireNumber(input.getMaxSpread(), "maxSpread");
        validateLimits(min, max, spread);

        return methodRepository.save(new AnalysisMethod(null, name, material, min, max, spread));
    }

    public AnalysisMethod update(Long id, AnalysisMethod patch) {
        AnalysisMethod method = getById(id);
        String name = patch.getName() != null ? Checks.requireText(patch.getName(), "name") : method.getName();
        String material = patch.getAllowedMaterial() != null
                ? Checks.requireText(patch.getAllowedMaterial(), "allowedMaterial") : method.getAllowedMaterial();
        double min = patch.getMinValue() != null ? Checks.requireNumber(patch.getMinValue(), "minValue") : method.getMinValue();
        double max = patch.getMaxValue() != null ? Checks.requireNumber(patch.getMaxValue(), "maxValue") : method.getMaxValue();
        double spread = patch.getMaxSpread() != null
                ? Checks.requireNumber(patch.getMaxSpread(), "maxSpread") : method.getMaxSpread();
        validateLimits(min, max, spread);

        boolean rulesChanged = !material.equalsIgnoreCase(method.getAllowedMaterial())
                || min != method.getMinValue()
                || max != method.getMaxValue()
                || spread != method.getMaxSpread();
        if (rulesChanged && analysisService.existsByMethodId(id)) {
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

    public void delete(Long id) {
        getById(id);
        analysisService.deleteByMethodId(id);
        methodRepository.deleteById(id);
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
