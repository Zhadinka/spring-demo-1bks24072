package com.example.demo.service;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.Analysis;
import com.example.demo.model.AnalysisStatus;
import com.example.demo.model.Sample;
import com.example.demo.model.SampleStatus;
import com.example.demo.repository.SampleRepository;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleRepository sampleRepository;
    private final UserRepository userRepository;
    private final AnalysisService analysisService;
    private final Clock clock;

    public List<Sample> findAll() {
        return sampleRepository.findAll();
    }

    public Sample getById(Long id) {
        return sampleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sample not found: " + id));
    }

    public Sample create(Sample input) {
        String material = Checks.requireText(input.getMaterial(), "material");
        Long customerId = Checks.requireNonNull(input.getCustomerId(), "customerId");
        requireCustomer(customerId);

        Sample sample = new Sample(null, material, customerId, clock.instant(), SampleStatus.RECEIVED);
        return sampleRepository.save(sample);
    }

    public Sample update(Long id, Sample patch) {
        Sample sample = getById(id);
        String material = sample.getMaterial();
        Long customerId = sample.getCustomerId();
        SampleStatus status = sample.getStatus();

        if (patch.getMaterial() != null) {
            material = Checks.requireText(patch.getMaterial(), "material");
            // исследования подобраны под материал пробы - менять его под ними нельзя
            if (!material.equalsIgnoreCase(sample.getMaterial()) && analysisService.existsBySampleId(id)) {
                throw new ConflictException("Material cannot be changed: the sample already has analyses");
            }
        }
        if (patch.getCustomerId() != null) {
            customerId = patch.getCustomerId();
            requireCustomer(customerId);
        }
        if (patch.getStatus() != null) {
            status = patch.getStatus();
            if (status == SampleStatus.COMPLETED && sample.getStatus() != SampleStatus.COMPLETED) {
                checkAllAnalysesCompleted(id);
            }
        }

        sample.setMaterial(material);
        sample.setCustomerId(customerId);
        sample.setStatus(status);
        return sampleRepository.save(sample);
    }

    public void delete(Long id) {
        getById(id);
        analysisService.deleteBySampleId(id);
        sampleRepository.deleteById(id);
    }

    public void deleteByCustomerId(Long customerId) {
        sampleRepository.findByCustomerId(customerId).forEach(s -> delete(s.getId()));
    }

    private void requireCustomer(Long customerId) {
        if (!userRepository.existsById(customerId)) {
            throw new BadRequestException("User (customer) not found: " + customerId);
        }
    }

    private void checkAllAnalysesCompleted(Long sampleId) {
        List<Analysis> analyses = analysisService.findBySampleId(sampleId);
        boolean allCompleted = analyses.stream().allMatch(a -> a.getStatus() == AnalysisStatus.COMPLETED);
        if (analyses.isEmpty() || !allCompleted) {
            throw new ConflictException("Sample cannot be COMPLETED: it needs at least one analysis "
                    + "and all its analyses must be COMPLETED");
        }
    }
}
