package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.domain.ExamPaperType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PePayloadAdapterTest {
    private final ExamWebhookPayloadReader reader = new ExamWebhookPayloadReader(
            new ObjectMapper().registerModule(new JavaTimeModule()), new EosPayloadAdapter());
    private final ExamWebhookPayloadValidator validator = new ExamWebhookPayloadValidator(5_242_880, 200);
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC";
    private static final String ZIP = "UEsFBgAAAAAAAAAAAAAAAAAAAAAAAA==";
    private String pe() {
        return """
                {"testName":"PE_PRO192_SU26_3_190626","paperNo":1,"numberOfPage":1,
                 "paperImage":["%s"],"givenMaterials":[{"questionNo":2,"given":"%s"}],
                 "password":"do-not-persist","gui":"do-not-persist"}
                """.formatted(PNG, ZIP);
    }
    @Test void mapsPePagesAndInlineZipAndStripsClientFields() {
        var delivery = reader.readAll(pe(), 50).getFirst();
        var paper = validator.validate(delivery.request());
        assertEquals(ExamPaperType.PE, paper.paperType());
        assertEquals("PRO192", paper.subjectCode());
        assertEquals("SU26", paper.term());
        assertEquals("PE_PRO192_SU26_3_190626_PaperNo1", paper.examCode());
        assertEquals(1, paper.images().size());
        assertEquals("Câu 2", paper.resources().getFirst().folderLabel());
        assertEquals(22, paper.resources().getFirst().content().length);
        assertNull(paper.resources().getFirst().sourceUrl());
        assertFalse(delivery.payloadJson().contains("do-not-persist"));
        assertEquals(paper.examCode(), validator.validate(reader.read(delivery.payloadJson())).examCode());
    }
    @Test void preservesStableIdentityWhenClientFieldsChange() {
        assertEquals(reader.read(pe()).eventId(), reader.read(pe().replace("do-not-persist", "changed")).eventId());
    }
    @Test void acceptsMixedRawBatch() {
        String eos = """
                {"ExamCode":"SCM302_SU26_FE_1","GrammarQuestions":[{"QID":1,"ImageData":"%s"}]}
                """.formatted(PNG);
        var batch = reader.readAll("{\"eventId\":\"batch-1\",\"papers\":[" + pe() + "," + eos + "]}", 50);
        assertEquals(2, batch.size());
        assertEquals("batch-1#0", batch.getFirst().request().eventId());
        assertEquals(ExamPaperType.PE, validator.validate(batch.getFirst().request()).paperType());
        assertEquals(ExamPaperType.FE, validator.validate(batch.getLast().request()).paperType());
    }
    @Test void rejectsMissingPages() {
        assertThrows(BadRequestException.class, () -> reader.read(pe().replace("\"numberOfPage\":1", "\"numberOfPage\":9")));
    }
    @Test void rejectsCorruptJsonInsteadOfRepairingImageBytes() {
        assertThrows(BadRequestException.class, () -> reader.read(pe().replace(PNG, "broken\\2image")));
    }
    @Test void rejectsInvalidBase64Zip() {
        var error = assertThrows(BadRequestException.class, () -> validator.validate(reader.read(pe().replace(ZIP, "!invalid!"))));
        assertEquals("WEBHOOK_RESOURCE_INVALID_BASE64", error.code());
    }
    @Test void rejectsNonZipResource() {
        var error = assertThrows(BadRequestException.class, () -> validator.validate(reader.read(pe().replace(ZIP, PNG))));
        assertEquals("WEBHOOK_RESOURCE_INVALID_ZIP", error.code());
    }
    @Test void refusesUrlAndInlineResourceTogether() {
        var canonical = reader.readAll(pe(), 50).getFirst().payloadJson().replace("\"sourceUrl\":null", "\"sourceUrl\":\"https://example.com/a.zip\"");
        assertThrows(BadRequestException.class, () -> validator.validate(reader.read(canonical)));
    }
}
