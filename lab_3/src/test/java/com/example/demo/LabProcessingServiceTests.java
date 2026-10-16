package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

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

/** Тесты бизнес-логики на реальных сервисах и репозиториях (встроенная H2 вместо PostgreSQL). */
@SpringBootTest
class LabProcessingServiceTests {

    private static final Instant NOW = Instant.parse("2026-01-15T10:30:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired UserRepository userRepository;
    @Autowired SampleRepository sampleRepository;
    @Autowired AnalysisMethodRepository methodRepository;
    @Autowired AnalysisRepository analysisRepository;
    @Autowired MeasurementRepository measurementRepository;

    @Autowired UserService userService;
    @Autowired SampleService sampleService;
    @Autowired AnalysisMethodService methodService;
    @Autowired AnalysisService analysisService;
    @Autowired MeasurementService measurementService;

    private User user;
    private Sample sample;
    private AnalysisMethod method;
    private Analysis analysis;

    @BeforeEach
    void setUp() {
        // внешние ключи ON DELETE CASCADE очищают все зависимые таблицы
        userRepository.deleteAll();
        methodRepository.deleteAll();

        user = userService.create("Ivan", "ivan@example.com");
        sample = sampleService.create("blood", user.getId());
        method = methodService.create("Glucose", "blood", 0.0, 100.0, 2.0);
        analysis = analysisService.create(sample.getId(), method.getId());
    }

    private Measurement measure(double value, boolean confirmed) {
        return measurementService.create(analysis.getId(), value, confirmed);
    }

    private Measurement confirm(Measurement m) {
        return measurementService.update(m.getId(), null, true);
    }

    private Analysis reload() {
        return analysisService.getById(analysis.getId());
    }

    private SampleStatus sampleStatus(Long id) {
        return sampleService.getById(id).getStatus();
    }

    // ---------- CRUD и базовые правила ----------

    @Test
    void serverSetsTimeAndInitialStatuses() {
        assertEquals(NOW, sample.getReceivedAt());
        assertEquals(SampleStatus.IN_PROGRESS, sampleStatus(sample.getId()),
                "после создания исследования проба переходит в IN_PROGRESS");
        assertEquals(AnalysisStatus.IN_PROGRESS, analysis.getStatus());
        assertNull(analysis.getAverageValue());

        Measurement m = measure(10, false);
        assertEquals(NOW, m.getMeasuredAt());
        assertEquals(false, m.getConfirmed());
    }

    @Test
    void sampleRequiresExistingCustomerAndMaterial() {
        assertThrows(BadRequestException.class, () -> sampleService.create("blood", 999L));
        assertThrows(BadRequestException.class, () -> sampleService.create(" ", user.getId()));
        assertEquals(SampleStatus.RECEIVED, sampleService.create("urine", user.getId()).getStatus());
    }

    @Test
    void analysisRequiresMatchingMaterialAndIsUniquePerSampleAndMethod() {
        Sample urine = sampleService.create("urine", user.getId());
        assertThrows(BadRequestException.class, () -> analysisService.create(urine.getId(), method.getId()));
        Sample blood = sampleService.create("BLOOD", user.getId());
        analysisService.create(blood.getId(), method.getId());
        assertThrows(ConflictException.class, () -> analysisService.create(blood.getId(), method.getId()));
    }

    @Test
    void userEmailAndMethodNameMustBeUnique() {
        assertThrows(ConflictException.class, () -> userService.create("Other", "IVAN@example.com"));
        assertThrows(ConflictException.class, () -> methodService.create("glucose", "blood", 0.0, 1.0, 1.0));
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
        assertNull(reload().getAverageValue());

        confirm(a);
        assertEquals(10.0, reload().getAverageValue());
        confirm(b);
        assertEquals(10.5, reload().getAverageValue());
    }

    @Test
    void confirmationIsRejectedWhenSpreadIsTooLarge() {
        Measurement a = measure(10, true);
        Measurement outlier = measure(13, false);

        assertThrows(ConflictException.class, () -> confirm(outlier));
        assertEquals(false, measurementService.getById(outlier.getId()).getConfirmed());
        assertThrows(ConflictException.class, () -> measure(13, true));

        Measurement b = measure(10.2, true);
        assertEquals(10.1, reload().getAverageValue());

        assertThrows(ConflictException.class, () -> measurementService.update(b.getId(), 12.5, null));
        assertEquals(10.2, measurementService.getById(b.getId()).getValue());
        assertEquals(10.0, measurementService.getById(a.getId()).getValue());
    }

    @Test
    void spreadExactlyAtLimitIsAllowedDespiteFloatingPointError() {
        measure(2.4, true);
        measure(4.4, true);
        assertEquals(3.4, reload().getAverageValue());
    }

    @Test
    void deletingMeasurementRecalculatesAverage() {
        Measurement a = confirm(measure(10, false));
        confirm(measure(11, false));
        assertEquals(10.5, reload().getAverageValue());

        measurementService.delete(a.getId());
        assertEquals(11.0, reload().getAverageValue());
    }

    @Test
    void methodRulesCannotBeChangedWhileInUse() {
        assertThrows(ConflictException.class,
                () -> methodService.update(method.getId(), null, null, 0.0, 50.0, null));
        assertEquals("Glucose v2",
                methodService.update(method.getId(), "Glucose v2", null, null, null, null).getName());
        assertThrows(BadRequestException.class, () -> methodService.create("Bad", "blood", 10.0, 5.0, 1.0));
    }

    @Test
    void partialUpdateKeepsOtherFields() {
        User updated = userService.update(user.getId(), "Petr", null);
        assertEquals("Petr", updated.getName());
        assertEquals("ivan@example.com", updated.getEmail());
    }

    @Test
    void notFoundForUnknownIds() {
        assertThrows(NotFoundException.class, () -> sampleService.getById(999L));
        assertThrows(NotFoundException.class, () -> measurementService.delete(999L));
        assertThrows(NotFoundException.class, () -> analysisService.complete(999L));
        assertThrows(BadRequestException.class, () -> measurementService.create(999L, 1.0, null));
    }

    // ---------- удаление: нет висячих ссылок ----------

    @Test
    void deletingAnalysisRemovesMeasurementsAndRecalculatesSample() {
        measure(10, true);
        analysisService.delete(analysis.getId());

        assertEquals(0, measurementRepository.count());
        assertEquals(0, analysisRepository.count());
        assertEquals(SampleStatus.RECEIVED, sampleStatus(sample.getId()));
    }

    @Test
    void deletingSampleRemovesItsAnalysesAndMeasurements() {
        measure(10, true);
        sampleService.delete(sample.getId());

        assertEquals(0, sampleRepository.count());
        assertEquals(0, analysisRepository.count());
        assertEquals(0, measurementRepository.count());
        assertEquals(1, methodRepository.count());
    }

    @Test
    void deletingMethodRemovesAnalysesButKeepsSample() {
        measure(10, true);
        methodService.delete(method.getId());

        assertEquals(0, analysisRepository.count());
        assertEquals(0, measurementRepository.count());
        assertEquals(SampleStatus.RECEIVED, sampleStatus(sample.getId()));
    }

    @Test
    void deletingUserCascadesDownToMeasurements() {
        measure(10, true);
        User other = userService.create("Other", null);
        Sample otherSample = sampleService.create("blood", other.getId());

        userService.delete(user.getId());

        assertThrows(NotFoundException.class, () -> userService.getById(user.getId()));
        List<Sample> left = sampleRepository.findAll();
        assertEquals(1, left.size());
        assertEquals(otherSample.getId(), left.get(0).getId());
        assertEquals(0, analysisRepository.count());
        assertEquals(0, measurementRepository.count());
    }

    // ---------- бизнес-операция 1: назначить подходящие методики ----------

    @Test
    void orderAnalysesCreatesOnlyMissingMethodsForTheMaterial() {
        AnalysisMethod hemoglobin = methodService.create("Hemoglobin", "blood", 50.0, 200.0, 5.0);
        methodService.create("Protein", "urine", 0.0, 20.0, 1.0);

        List<Analysis> created = sampleService.orderAnalyses(sample.getId());   // Glucose уже назначена

        assertEquals(1, created.size());
        assertEquals(hemoglobin.getId(), created.get(0).getMethod().getId());
        assertEquals(2, analysisRepository.findBySample_IdOrderById(sample.getId()).size());
        // повторный вызов: назначать больше нечего
        assertThrows(ConflictException.class, () -> sampleService.orderAnalyses(sample.getId()));
    }

    @Test
    void orderAnalysesDependsOnSampleStateAndMethodCatalog() {
        Sample urine = sampleService.create("urine", user.getId());
        assertThrows(ConflictException.class, () -> sampleService.orderAnalyses(urine.getId()),
                "для мочи нет ни одной методики");

        methodService.create("Protein", "urine", 0.0, 20.0, 1.0);
        assertEquals(1, sampleService.orderAnalyses(urine.getId()).size());
        assertEquals(SampleStatus.IN_PROGRESS, sampleStatus(urine.getId()));

        sampleService.reject(urine.getId());
        assertThrows(ConflictException.class, () -> sampleService.orderAnalyses(urine.getId()),
                "отклонённой пробе исследования не назначаются");
    }

    // ---------- бизнес-операция 2: отклонить пробу ----------

    @Test
    void rejectingSampleCancelsOpenAnalysesAndKeepsMeasurements() {
        Measurement m = measure(10, true);

        Sample rejected = sampleService.reject(sample.getId());

        assertEquals(SampleStatus.REJECTED, rejected.getStatus());
        assertEquals(AnalysisStatus.CANCELLED, reload().getStatus());
        assertEquals(1, measurementRepository.count());
        assertThrows(ConflictException.class, () -> measurementService.delete(m.getId()),
                "измерения отменённого исследования менять нельзя");
        assertThrows(ConflictException.class, () -> sampleService.reject(sample.getId()));
        assertEquals(SampleStatus.REJECTED, sampleStatus(sample.getId()),
                "статус отклонённой пробы не пересчитывается");
    }

    @Test
    void completedSampleCannotBeRejected() {
        confirm(measure(10, false));
        analysisService.complete(analysis.getId());
        assertEquals(SampleStatus.COMPLETED, sampleStatus(sample.getId()));

        assertThrows(ConflictException.class, () -> sampleService.reject(sample.getId()));
    }

    @Test
    void sampleStatusCannotBeSetManuallyExceptRejected() {
        assertThrows(ConflictException.class,
                () -> sampleService.update(sample.getId(), null, null, SampleStatus.COMPLETED));
        assertThrows(ConflictException.class,
                () -> sampleService.update(sample.getId(), null, null, SampleStatus.RECEIVED));
        Sample rejected = sampleService.update(sample.getId(), null, null, SampleStatus.REJECTED);
        assertEquals(SampleStatus.REJECTED, rejected.getStatus());
        assertEquals(AnalysisStatus.CANCELLED, reload().getStatus());
    }

    // ---------- бизнес-операция 3: подтвердить все измерения ----------

    @Test
    void confirmMeasurementsConfirmsEverythingAndRecalculatesAverage() {
        measure(10, false);
        measure(11, false);

        Analysis result = analysisService.confirmMeasurements(analysis.getId());

        assertEquals(10.5, result.getAverageValue());
        assertTrue(measurementRepository.findAll().stream().allMatch(Measurement::getConfirmed));
        assertThrows(ConflictException.class, () -> analysisService.confirmMeasurements(analysis.getId()),
                "подтверждать больше нечего");
    }

    @Test
    void confirmMeasurementsIsAllOrNothing() {
        Measurement a = measure(10, false);
        Measurement b = measure(13, false);          // разброс 3 > 2

        assertThrows(ConflictException.class, () -> analysisService.confirmMeasurements(analysis.getId()));

        assertFalse(measurementService.getById(a.getId()).getConfirmed());
        assertFalse(measurementService.getById(b.getId()).getConfirmed());
        assertNull(reload().getAverageValue());

        measurementService.delete(b.getId());         // убрали выброс - операция проходит
        assertEquals(10.0, analysisService.confirmMeasurements(analysis.getId()).getAverageValue());
    }

    // ---------- бизнес-операции 4 и 5: завершить / вернуть в работу ----------

    @Test
    void analysisCanBeCompletedOnlyWithConfirmedMeasurements() {
        measure(10, false);
        assertThrows(ConflictException.class, () -> analysisService.complete(analysis.getId()));
        assertEquals(AnalysisStatus.IN_PROGRESS, reload().getStatus());
    }

    @Test
    void completingAnalysisCompletesSampleOnlyWhenAllAnalysesAreCompleted() {
        methodService.create("Hemoglobin", "blood", 50.0, 200.0, 5.0);
        Analysis second = sampleService.orderAnalyses(sample.getId()).get(0);
        confirm(measure(10, false));

        analysisService.complete(analysis.getId());
        assertEquals(SampleStatus.IN_PROGRESS, sampleStatus(sample.getId()), "второе исследование ещё идёт");

        measurementService.create(second.getId(), 100.0, true);
        analysisService.complete(second.getId());
        assertEquals(SampleStatus.COMPLETED, sampleStatus(sample.getId()));
    }

    @Test
    void completedAnalysisLocksMeasurementsUntilReopened() {
        Measurement m = confirm(measure(10, false));
        Analysis completed = analysisService.complete(analysis.getId());
        assertEquals(AnalysisStatus.COMPLETED, completed.getStatus());
        assertEquals(10.0, completed.getAverageValue());

        assertThrows(ConflictException.class, () -> measure(11, false));
        assertThrows(ConflictException.class, () -> measurementService.delete(m.getId()));
        assertThrows(ConflictException.class, () -> analysisService.complete(analysis.getId()),
                "повторное завершение недопустимо");
        assertThrows(ConflictException.class,
                () -> analysisService.create(sample.getId(), method.getId()));

        Analysis reopened = analysisService.reopen(analysis.getId());
        assertEquals(AnalysisStatus.IN_PROGRESS, reopened.getStatus());
        assertEquals(SampleStatus.IN_PROGRESS, sampleStatus(sample.getId()));
        measure(11, false);
    }

    @Test
    void reopenIsPossibleOnlyForCompletedAnalysisOfNotRejectedSample() {
        assertThrows(ConflictException.class, () -> analysisService.reopen(analysis.getId()),
                "исследование ещё не завершено");

        methodService.create("Hemoglobin", "blood", 50.0, 200.0, 5.0);
        sampleService.orderAnalyses(sample.getId());
        confirm(measure(10, false));
        analysisService.complete(analysis.getId());
        sampleService.reject(sample.getId());              // у пробы остаётся завершённое исследование

        assertEquals(AnalysisStatus.COMPLETED, reload().getStatus());
        assertThrows(ConflictException.class, () -> analysisService.reopen(analysis.getId()));
    }

    @Test
    void statusCancelledCannotBeSetThroughPatch() {
        assertThrows(BadRequestException.class,
                () -> analysisService.update(analysis.getId(), null, null, AnalysisStatus.CANCELLED));
    }

    @Test
    void patchStatusUsesTheSameRulesAsBusinessOperations() {
        assertThrows(ConflictException.class,
                () -> analysisService.update(analysis.getId(), null, null, AnalysisStatus.COMPLETED));
        confirm(measure(10, false));
        assertEquals(AnalysisStatus.COMPLETED,
                analysisService.update(analysis.getId(), null, null, AnalysisStatus.COMPLETED).getStatus());
        assertEquals(SampleStatus.COMPLETED, sampleStatus(sample.getId()));
    }
}
