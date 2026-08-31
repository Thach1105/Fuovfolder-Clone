package com.fuoverflow.exam.support;

import com.fuoverflow.exam.domain.IngestAsset;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.domain.IngestQuestion;
import com.fuoverflow.exam.domain.IngestResource;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.TreeSet;

/**
 * Identifies a paper by the content it carries, independent of delivery order.
 *
 * <p>A third party may resend the same paper with questions or options shuffled; a fingerprint
 * match must still collapse the two deliveries onto one row, otherwise every retry leaves members
 * with a duplicate paper to wade through.
 */
public final class ExamPaperFingerprint {

    private ExamPaperFingerprint() {
    }

    public static String of(IngestPaper paper) {
        List<String> units = new ArrayList<>();
        for (IngestQuestion question : paper.questions()) {
            units.add(question.externalId() + ":" + unitBody(question));
        }
        for (IngestAsset image : paper.images()) {
            units.add(image.sha256());
        }
        for (IngestResource resource : paper.resources()) {
            units.add(resource.sha256());
        }

        StringJoiner joined = new StringJoiner(";");
        new TreeSet<>(units).forEach(joined::add);
        return Sha256.hexUtf8(paper.examCode() + "|" + paper.paperType().dbValue() + "|" + joined);
    }

    private static String unitBody(IngestQuestion question) {
        StringJoiner parts = new StringJoiner(",");
        if (question.answerOptionIds() != null && !question.answerOptionIds().isEmpty()) {
            new TreeSet<>(question.answerOptionIds()).forEach(id -> parts.add(Long.toString(id)));
            return parts.toString();
        }
        TreeSet<String> hashes = new TreeSet<>();
        for (IngestAsset image : question.images()) {
            hashes.add(image.sha256());
        }
        hashes.forEach(parts::add);
        return parts.toString();
    }
}
