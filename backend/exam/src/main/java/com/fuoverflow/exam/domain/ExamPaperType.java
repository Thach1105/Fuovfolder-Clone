package com.fuoverflow.exam.domain;

import com.fuoverflow.common.exception.BadRequestException;

/**
 * Kind of exam paper. The wire form and the {@code exam_papers.paper_type} column both use the
 * uppercase name, which is what the table's check constraint accepts.
 */
public enum ExamPaperType {
    FE, PE;

    public String dbValue() {
        return name();
    }

    public static ExamPaperType fromWire(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Thiếu paperType.");
        }
        return switch (value.trim().toUpperCase()) {
            case "FE" -> FE;
            case "PE" -> PE;
            default -> throw new BadRequestException(
                    "WEBHOOK_PAYLOAD_INVALID", "paperType không hỗ trợ: " + value);
        };
    }
}
