package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.model.Analysis;
import com.example.demo.model.AnalysisMethod;
import com.example.demo.model.AnalysisStatus;
import com.example.demo.model.Measurement;
import com.example.demo.model.Sample;
import com.example.demo.model.SampleStatus;
import com.example.demo.model.User;
import com.example.demo.repository.AnalysisMethodRepository;
import com.example.demo.repository.AnalysisRepository;
import com.example.demo.repository.MeasurementRepository;
import com.example.demo.repository.SampleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AnalysisMethodService;
import com.example.demo.service.AnalysisService;
import com.example.demo.service.MeasurementService;
import com.example.demo.service.SampleService;
import com.example.demo.service.UserService;

class LabProcessingServiceTests {

    private static final Instant NOW = Instant.parse("2026-01-15T10:30:00Z");

    private UserRepository userRepository;
    private SampleRepository sampleRepository;
    private AnalysisMethodRepository methodRepository;
    private AnalysisRepository analysisRepository;
    private MeasurementRepository measurementRepository;

    private UserService userService;
    private SampleService sampleService;
    private AnalysisMethodService methodService;
    private AnalysisService analysisService;
    private MeasurementService measurementService;

    private User user;
    private Sample sample;
    private AnalysisMethod method;
    private Analysis analysis;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        userRepository = new UserRepository();
        sampleRepository = new SampleRepository();
        methodRepository = new AnalysisMethodRepository();
        analysisRepository = new AnalysisRepository();
        measurementRepository = new MeasurementRepository();

        analysisService = new AnalysisService(analysisRepository, measurementRepository, sampleRepository, methodRepository);
        sampleService = new SampleService(sampleRepository, userRepository, analysisService, clock);
        userService = new UserService(userRepository, sampleService);
        methodService = new AnalysisMethodService(methodRepository, analysisService);
        measurementService = new MeasurementService(measurementRepository, analysisService, clock);

        user = userService.create(new User(null, "Ivan", "ivan@example.com"));
        sample = sampleService.create(new Sample(null, "blood", user.getId(), null, null));
        method = methodService.create(new AnalysisMethod(null, "Glucose", "blood", 0.0, 100.0, 2.0));
        analysis = analysisService.create(new Analysis(null, sample.getId(), method.getId(), null, null));
    }

    private Measurement measure(double value, boolean confirmed) {
        return measurementService.create(new Measurement(null, analysis.getId(), value, null, confirmed));
    }

    private Measurement confirm(Measurement m) {
        return measurementService.update(m.getId(), new Measurement(null, null, null, null, true));
    }

    @Test
    void serverSetsTimeAndInitialStatuses() {
        assertEquals(NOW, sample.getReceivedAt());
        assertEquals(SampleStatus.IN_PROGRESS, sampleService.getById(sample.getId()).getStatus(),
                "после создания исследования проба переходит в IN_PROGRESS");
        assertEquals(AnalysisStatus.IN_PROGRESS, analysis.getStatus());
        assertNull(analysis.getAverageValue());

        Measurement m = measure(10, false);
        assertEquals(NOW, m.getMeasuredAt());
        assertEquals(false, m.getConfirmed());
    }

    @Test
    void sampleStatusFromClientIsIgnoredOnCreate() {
        Sample created = sampleService.create(new Sample(null, "urine", user.getId(), Instant.EPOCH, SampleStatus.COMPLETED));
        assertEquals(SampleStatus.RECEIVED, created.getStatus());
        assertEquals(NOW, created.getReceivedAt());
    }

    @Test
    void sampleRequiresExistingCustomerAndMaterial() {
        assertThrows(BadRequestException.class,
                () -> sampleService.create(new Sample(null, "blood", 999L, null, null)));
        assertThrows(BadRequestException.class,
                () -> sampleService.create(new Sample(null, " ", user.getId(), null, null)));
    }

    @Test
    void analysisRequiresMatchingMaterial() {
        Sample urine = sampleService.create(new Sample(null, "urine", user.getId(), null, null));
        assertThrows(BadRequestException.class,
                () -> analysisService.create(new Analysis(null, urine.getId(), method.getId(), null, null)));
        Sample blood = sampleService.create(new Sample(null, "BLOOD", user.getId(), null, null));
        analysisService.create(new Analysis(null, blood.getId(), method.getId(), null, null));
    }

    @Test
    void measurementMustBeInsideMethodRange() {
        assertThrows(BadRequestException.class, () -> measure(-0.1, false));
        assertThrows(BadRequestException.class, () -> measure(100.1, false));
        measure(0, false);
        measure(100, false);
    }

    @Test
    void averageIsCalculatedFromConfirmedMeasurementsOnly() {
        Measurement a = measure(10, false);
        Measurement b = measure(11, false);
        measure(20, false);
        assertNull(analysisService.getById(analysis.getId()).getAverageValue());

        confirm(a);
        assertEquals(10.0, analysisService.getById(analysis.getId()).getAverageValue());
        confirm(b);
        assertEquals(10.5, analysisService.getById(analysis.getId()).getAverageValue());
    }

    @Test
    void confirmationIsRejectedWhenSpreadIsTooLarge() {
        Measurement a = measure(10, true);
        Measurement outlier = measure(13, false);

        assertThrows(ConflictException.class, () -> confirm(outlier));
        assertEquals(false, measurementService.getById(outlier.getId()).getConfirmed());
        assertThrows(ConflictException.class, () -> measure(13, true));

        Measurement b = measure(10.2, true);
        assertEquals(10.1, analysisService.getById(analysis.getId()).getAverageValue());

        assertThrows(ConflictException.class,
                () -> measurementService.update(b.getId(), new Measurement(null, null, 12.5, null, null)));
        assertEquals(10.2, measurementService.getById(b.getId()).getValue());
        assertEquals(10.0, measurementService.getById(a.getId()).getValue());
    }

    @Test
    void spreadExactlyAtLimitIsAllowedDespiteFloatingPointError() {
        measure(2.4, true);
        measure(4.4, true);
        assertEquals(3.4, analysisService.getById(analysis.getId()).getAverageValue());
    }

    @Test
    void analysisCanBeCompletedOnlyWithConfirmedMeasurements() {
        measure(10, false);
        assertThrows(ConflictException.class,
                () -> analysisService.update(analysis.getId(), new Analysis(null, null, null, AnalysisStatus.COMPLETED, null)));
        assertEquals(AnalysisStatus.IN_PROGRESS, analysisService.getById(analysis.getId()).getStatus());
    }

    @Test
    void completingAnalysisCompletesSampleAndLocksMeasurements() {
        Measurement m = confirm(measure(10, false));
        analysisService.update(analysis.getId(), new Analysis(null, null, null, AnalysisStatus.COMPLETED, null));

        assertEquals(SampleStatus.COMPLETED, sampleService.getById(sample.getId()).getStatus());
        assertThrows(ConflictException.class, () -> measure(11, false));
        assertThrows(ConflictException.class, () -> measurementService.delete(m.getId()));
        assertThrows(ConflictException.class,
                () -> analysisService.create(new Analysis(null, sample.getId(), method.getId(), null, null)));

        analysisService.update(analysis.getId(), new Analysis(null, null, null, AnalysisStatus.IN_PROGRESS, null));
        assertEquals(SampleStatus.IN_PROGRESS, sampleService.getById(sample.getId()).getStatus());
        measure(11, false);
    }

    @Test
    void sampleCannotBeCompletedManuallyWhileAnalysesAreOpen() {
        assertThrows(ConflictException.class,
                () -> sampleService.update(sample.getId(), new Sample(null, null, null, null, SampleStatus.COMPLETED)));
        Sample rejected = sampleService.update(sample.getId(), new Sample(null, null, null, null, SampleStatus.REJECTED));
        assertEquals(SampleStatus.REJECTED, rejected.getStatus());
        assertThrows(ConflictException.class,
                () -> analysisService.create(new Analysis(null, sample.getId(), method.getId(), null, null)));
    }

    @Test
    void deletingMeasurementRecalculatesAverage() {
        Measurement a = confirm(measure(10, false));
        confirm(measure(11, false));
        assertEquals(10.5, analysisService.getById(analysis.getId()).getAverageValue());

        measurementService.delete(a.getId());
        assertEquals(11.0, analysisService.getById(analysis.getId()).getAverageValue());
    }

    @Test
    void methodRulesCannotBeChangedWhileInUse() {
        assertThrows(ConflictException.class,
                () -> methodService.update(method.getId(), new AnalysisMethod(null, null, null, 0.0, 50.0, null)));
        assertEquals("Glucose v2",
                methodService.update(method.getId(), new AnalysisMethod(null, "Glucose v2", null, null, null, null)).getName());
        assertThrows(BadRequestException.class,
                () -> methodService.create(new AnalysisMethod(null, "Bad", "blood", 10.0, 5.0, 1.0)));
    }

    @Test
    void partialUpdateKeepsOtherFields() {
        User updated = userService.update(user.getId(), new User(null, "Petr", null));
        assertEquals("Petr", updated.getName());
        assertEquals("ivan@example.com", updated.getEmail());
    }

    @Test
    void deletingMeasurementAndAnalysisLeavesNoDanglingReferences() {
        measure(10, true);
        analysisService.delete(analysis.getId());

        assertTrue(measurementRepository.findAll().isEmpty());
        assertTrue(analysisRepository.findAll().isEmpty());
        assertEquals(SampleStatus.RECEIVED, sampleService.getById(sample.getId()).getStatus());
    }

    @Test
    void deletingSampleRemovesItsAnalysesAndMeasurements() {
        measure(10, true);
        sampleService.delete(sample.getId());

        assertTrue(sampleRepository.findAll().isEmpty());
        assertTrue(analysisRepository.findAll().isEmpty());
        assertTrue(measurementRepository.findAll().isEmpty());
        assertEquals(1, methodRepository.findAll().size());
    }

    @Test
    void deletingMethodRemovesAnalysesButKeepsSample() {
        measure(10, true);
        methodService.delete(method.getId());

        assertTrue(analysisRepository.findAll().isEmpty());
        assertTrue(measurementRepository.findAll().isEmpty());
        assertEquals(SampleStatus.RECEIVED, sampleService.getById(sample.getId()).getStatus());
    }

    @Test
    void deletingUserCascadesDownToMeasurements() {
        measure(10, true);
        User other = userService.create(new User(null, "Other", null));
        Sample otherSample = sampleService.create(new Sample(null, "blood", other.getId(), null, null));

        userService.delete(user.getId());

        assertThrows(NotFoundException.class, () -> userService.getById(user.getId()));
        assertEquals(1, sampleRepository.findAll().size());
        assertEquals(otherSample.getId(), sampleRepository.findAll().get(0).getId());
        assertTrue(analysisRepository.findAll().isEmpty());
        assertTrue(measurementRepository.findAll().isEmpty());
    }

    @Test
    void notFoundForUnknownIds() {
        assertThrows(NotFoundException.class, () -> sampleService.getById(999L));
        assertThrows(NotFoundException.class, () -> measurementService.delete(999L));
        assertThrows(BadRequestException.class,
                () -> measurementService.create(new Measurement(null, 999L, 1.0, null, null)));
    }
}
