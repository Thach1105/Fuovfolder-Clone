package com.fuoverflow.grading.domain;

/**
 * Provenance of a question's answer key.
 *
 * <p>Only {@code MANUAL} and {@code IMPORTED} count as answered when publishing a paper.
 * {@code SUGGESTED} comes from a source that does not share our identifier space, so it
 * must be confirmed by a human before it can gate publication.
 */
public enum AnswerSource { MANUAL, IMPORTED, SUGGESTED }
