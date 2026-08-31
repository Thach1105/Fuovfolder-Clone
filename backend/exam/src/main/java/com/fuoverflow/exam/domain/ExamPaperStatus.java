package com.fuoverflow.exam.domain;

/**
 * Publication state of a paper. Unlike {@link ExamPaperType} the database stores these lowercase,
 * so always go through {@link #dbValue()} rather than {@code name()}.
 */
public enum ExamPaperStatus {
    DRAFT("draft"),
    PUBLISHED("published");

    private final String dbValue;

    ExamPaperStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static ExamPaperStatus fromDbValue(String value) {
        return PUBLISHED.dbValue.equals(value) ? PUBLISHED : DRAFT;
    }
}
