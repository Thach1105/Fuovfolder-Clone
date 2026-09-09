package com.fuoverflow.exam.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Extracts subject code, academic term, paper type, campus, and external paper id from an exam
 * code, independent of delimiter or field order.
 *
 * <p>Two shapes are seen in production: {@code SUBJECT_TERM_TYPE_ID} (underscore, trailing
 * numeric id) and {@code SUBJECT-TYPE-TERM-CAMPUS} (dash, trailing training-facility code). Both
 * — and any other ordering of the same four concepts — parse correctly here because every segment
 * after the first is classified by what it looks like, never by its position.
 */
public final class ExamCodeParser {

    private static final Pattern SEPARATOR = Pattern.compile("[-_]+");
    private static final Pattern TERM = Pattern.compile("^[A-Za-z]{2}\\d{2}$");
    private static final Pattern TYPE = Pattern.compile("^(FE|PE|PT|MID)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern LETTERS_ONLY = Pattern.compile("^[A-Za-z]+$");

    private ExamCodeParser() {
    }

    public record Parsed(
            String subjectCode, String term, String paperType, String campus, String externalPaperId) {
    }

    /** The first segment is always the subject; every other segment is classified by content. */
    public static Parsed parse(String examCode) {
        String[] segments = SEPARATOR.split(examCode.trim());
        if (segments.length == 0) {
            return new Parsed(examCode, null, null, null, null);
        }
        String subjectCode = segments[0];

        String term = null;
        String type = null;
        List<String> leftover = new ArrayList<>();
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            if (type == null && TYPE.matcher(segment).matches()) {
                type = segment.toUpperCase();
            } else if (term == null && TERM.matcher(segment).matches()) {
                term = segment.toUpperCase();
            } else {
                leftover.add(segment);
            }
        }

        String campus = null;
        String externalPaperId = null;
        if (leftover.size() == 1) {
            String only = leftover.get(0);
            if (DIGITS_ONLY.matcher(only).matches()) {
                externalPaperId = only;
            } else if (LETTERS_ONLY.matcher(only).matches()) {
                campus = only.toUpperCase();
            }
        }

        return new Parsed(subjectCode, term, type, campus, externalPaperId);
    }
}
