package com.example.demo.service;

import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ConflictException;
import com.example.demo.model.AnalysisMethod;
import com.example.demo.model.Sample;
import com.example.demo.model.SampleStatus;

/** Правила предметной области, которые используются сразу несколькими сервисами. */
final class LabRules {

    private LabRules() {
    }

    /** Методика применима только к материалу, для которого она создана. */
    static void checkMaterial(Sample sample, AnalysisMethod method) {
        if (!method.getAllowedMaterial().equalsIgnoreCase(sample.getMaterial())) {
            throw new BadRequestException("Method '" + method.getName() + "' is intended for material '"
                    + method.getAllowedMaterial() + "', but the sample material is '" + sample.getMaterial() + "'");
        }
    }

    /** Новые исследования нельзя добавлять к завершённой или отклонённой пробе. */
    static void checkSampleAcceptsAnalyses(Sample sample) {
        if (sample.getStatus() == SampleStatus.COMPLETED || sample.getStatus() == SampleStatus.REJECTED) {
            throw new ConflictException("Sample " + sample.getId() + " is " + sample.getStatus()
                    + " and cannot get new analyses");
        }
    }
}
