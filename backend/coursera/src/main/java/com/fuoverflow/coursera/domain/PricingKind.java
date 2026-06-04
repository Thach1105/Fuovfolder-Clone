package com.fuoverflow.coursera.domain;

public enum PricingKind {
    SINGLE,
    COMBO;

    public String toDb() {
        return name().toLowerCase();
    }

    public static PricingKind fromDb(String value) {
        return PricingKind.valueOf(value.toUpperCase());
    }
}
