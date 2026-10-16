package com.example.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

final class Statistics {

    static final double EPSILON = 1e-9;

    private Statistics() {
    }

    static double average(Collection<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    static double spread(Collection<Double> values) {
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        return max - min;
    }

    static double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }
}
