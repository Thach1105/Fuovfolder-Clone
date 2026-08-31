package com.fuoverflow.grading.support;

import com.fuoverflow.grading.domain.NormalizedOption;
import com.fuoverflow.grading.domain.NormalizedPaper;
import com.fuoverflow.grading.domain.NormalizedQuestion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Identifies a paper by the exact set of questions and options it contains.
 *
 * <p>The canonical form is order independent because option order is shuffled before delivery and a
 * submission records questions in the student's own shuffled order. A fingerprint match therefore
 * guarantees that an answer key stored against QAIDs applies to the submission, which is the whole
 * point: grading must never rely on option position.
 */
public final class PaperFingerprint {

    private PaperFingerprint() {
    }

    public static String of(NormalizedPaper paper) {
        Map<Long, Collection<Long>> qidToQaids = new TreeMap<>();
        for (NormalizedQuestion question : paper.questions()) {
            List<Long> qaids = new ArrayList<>(question.options().size());
            for (NormalizedOption option : question.options()) {
                qaids.add(option.qaid());
            }
            qidToQaids.put(question.qid(), qaids);
        }
        return hash(paper.examCode(), qidToQaids);
    }

    /** Same canonical form, built from what a decoded {@code .dat} submission carries. */
    public static String ofSubmission(String examCode, Map<Long, ? extends Collection<Long>> qidToQaids) {
        return hash(examCode, qidToQaids);
    }

    private static String hash(String examCode, Map<Long, ? extends Collection<Long>> qidToQaids) {
        StringJoiner questions = new StringJoiner(";");
        for (Long qid : new TreeSet<>(qidToQaids.keySet())) {
            StringJoiner qaids = new StringJoiner(",");
            for (Long qaid : new TreeSet<>(qidToQaids.get(qid))) {
                qaids.add(Long.toString(qaid));
            }
            questions.add(qid + ":" + qaids);
        }
        return Sha256.hexUtf8(examCode + "|" + questions);
    }
}
