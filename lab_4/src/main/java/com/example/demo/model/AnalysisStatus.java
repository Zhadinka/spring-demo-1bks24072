package com.example.demo.model;

public enum AnalysisStatus {
    IN_PROGRESS,
    COMPLETED,
    /** Исследование отменено, потому что проба отклонена. Выставляется только операцией отклонения пробы. */
    CANCELLED
}
