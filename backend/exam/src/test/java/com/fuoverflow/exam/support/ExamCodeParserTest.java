package com.fuoverflow.exam.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExamCodeParserTest {

    @Test
    void parsesTheClassicUnderscoreShapeWithATrailingNumericId() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("SCM302_SU26_FE_553972");

        assertEquals("SCM302", p.subjectCode());
        assertEquals("SU26", p.term());
        assertEquals("FE", p.paperType());
        assertEquals("553972", p.externalPaperId());
        assertNull(p.campus());
    }

    @Test
    void parsesTheNewDashShapeWithATrailingCampusCode() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("SDN302-PE-SU26-HCM");

        assertEquals("SDN302", p.subjectCode());
        assertEquals("SU26", p.term());
        assertEquals("PE", p.paperType());
        assertEquals("HCM", p.campus());
        assertNull(p.externalPaperId());
    }

    @Test
    void isCaseInsensitiveOnTypeAndTermAndUppercasesTheResult() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("sdn302-pe-su26-hcm");

        assertEquals("SU26", p.term());
        assertEquals("PE", p.paperType());
        assertEquals("HCM", p.campus());
    }

    @Test
    void doesNotCareAboutTheOrderOfTypeAndTerm() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("SDN302_SU26_PE_HCM");

        assertEquals("SDN302", p.subjectCode());
        assertEquals("SU26", p.term());
        assertEquals("PE", p.paperType());
        assertEquals("HCM", p.campus());
    }

    @Test
    void recognizesOtherSeasonCodes() {
        assertEquals("FA26", ExamCodeParser.parse("SDN302-PE-FA26-HN").term());
        assertEquals("SP26", ExamCodeParser.parse("SDN302-PE-SP26-DN").term());
    }

    @Test
    void treatsProgressTestAndMidtermCodesAsRecognizedTypesToo() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("MAE101_SU26_PT_42");

        assertEquals("PT", p.paperType());
        assertEquals("SU26", p.term());
        assertEquals("42", p.externalPaperId());
    }

    @Test
    void leavesCampusAndExternalIdNullWhenMoreThanOneSegmentIsLeftover() {
        // Same shape the current adapter already treats as non-conforming.
        ExamCodeParser.Parsed p = ExamCodeParser.parse("TEST_EOS_Client_278333");

        assertEquals("TEST", p.subjectCode());
        assertNull(p.term());
        assertNull(p.paperType());
        assertNull(p.campus());
        assertNull(p.externalPaperId());
    }

    @Test
    void aCodeWithNoRecognizableSegmentsStillYieldsTheFirstSegmentAsSubject() {
        ExamCodeParser.Parsed p = ExamCodeParser.parse("JUSTASUBJECTCODE");

        assertEquals("JUSTASUBJECTCODE", p.subjectCode());
        assertNull(p.term());
        assertNull(p.paperType());
    }
}
