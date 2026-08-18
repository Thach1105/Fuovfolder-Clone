package com.fuoverflow.grading.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.grading.api.dto.AnswerItem;
import com.fuoverflow.grading.api.dto.ApplyAnswersOutcome;
import com.fuoverflow.grading.api.dto.ApplyAnswersRequest;
import com.fuoverflow.grading.api.dto.ApplyAnswersResponse;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperStatus;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import com.fuoverflow.grading.support.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Applies an answer key that arrived separately from the paper.
 *
 * <p>The two JSON dialects in play share no identifier: the exam payload keys options by QAID and
 * carries no answers, while the answer dump keys them by letter and has no QAID at all. Bridging
 * them requires positional matching, and option order is known to be shuffled before delivery — so a
 * letter-keyed answer is recorded as a suggestion that a human must confirm, never as truth.
 */
@Service
public class PaperAnswerImportService {

    private static final String KEEP_EXISTING = "KEEP_EXISTING";
    private static final String OVERWRITE = "OVERWRITE";
    private static final String FAIL = "FAIL";

    private final GradingPaperRepository paperRepository;
    private final GradingPaperQuestionRepository questionRepository;
    private final GradingPaperAnswerRepository answerRepository;

    public PaperAnswerImportService(GradingPaperRepository paperRepository,
                                    GradingPaperQuestionRepository questionRepository,
                                    GradingPaperAnswerRepository answerRepository) {
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    @Transactional
    public ApplyAnswersResponse apply(UUID paperId, ApplyAnswersRequest request) {
        GradingPaperEntity paper = requireDraft(paperId);

        if (request.examCode() != null && !request.examCode().isBlank()
                && !request.examCode().equals(paper.getExamCode())) {
            throw new BadRequestException("EXAM_CODE_MISMATCH",
                    "Bộ đáp án thuộc mã đề khác: " + request.examCode());
        }

        String policy = policyOf(request.conflictPolicy());
        List<Plan> plans = new ArrayList<>();
        List<ApplyAnswersOutcome> outcomes = new ArrayList<>();

        for (AnswerItem item : request.items()) {
            plan(paper, item, policy, request.sourceRef(), plans, outcomes);
        }

        if (!request.dryRun()) {
            plans.forEach(this::persist);
        }

        int applied = (int) outcomes.stream().filter(o -> "APPLIED".equals(o.result())).count();
        int suggested = (int) outcomes.stream().filter(o -> "SUGGESTED".equals(o.result())).count();
        int skipped = (int) outcomes.stream().filter(o -> "SKIPPED".equals(o.result())).count();

        List<Long> stillUnanswered = questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())
                .stream()
                .filter(question -> !question.isAnswered())
                .map(GradingPaperQuestionEntity::getQid)
                .toList();

        return new ApplyAnswersResponse(applied, suggested, skipped, stillUnanswered,
                List.copyOf(outcomes));
    }

    @Transactional
    public void confirmSuggestion(UUID paperId, long qid) {
        GradingPaperEntity paper = requireDraft(paperId);
        GradingPaperQuestionEntity question = questionRepository.findByPaperIdAndQid(paper.getId(), qid)
                .orElseThrow(() -> new NotFoundException("QUESTION_NOT_FOUND",
                        "Không tìm thấy câu " + qid + " trong đề."));

        if (question.getAnswerSource() != AnswerSource.SUGGESTED) {
            throw new BadRequestException("NO_SUGGESTION_TO_CONFIRM",
                    "Câu " + qid + " không có gợi ý cần xác nhận.");
        }

        // is_correct is already set by the suggestion; confirming only raises the trust level.
        question.applyAnswer(AnswerSource.MANUAL, question.getAnswerSourceRef());
        questionRepository.save(question);
    }

    // ---------------------------------------------------------------- planning

    /** What would change for one question. Kept separate from persistence so dryRun writes nothing. */
    private record Plan(GradingPaperQuestionEntity question,
                        List<GradingPaperAnswerEntity> options,
                        Set<Long> correctQaids,
                        boolean trusted,
                        String sourceRef) {
    }

    private void plan(GradingPaperEntity paper, AnswerItem item, String policy, String sourceRef,
                      List<Plan> plans, List<ApplyAnswersOutcome> outcomes) {
        var found = questionRepository.findByPaperIdAndQid(paper.getId(), item.qid());
        if (found.isEmpty()) {
            outcomes.add(skip(item.qid(), "QID_NOT_IN_PAPER"));
            return;
        }
        GradingPaperQuestionEntity question = found.get();

        if (question.isAnswered() && !OVERWRITE.equals(policy)) {
            if (FAIL.equals(policy)) {
                throw new ConflictException("ANSWER_CONFLICT",
                        "Câu " + item.qid() + " đã có đáp án.");
            }
            outcomes.add(skip(item.qid(), "ALREADY_ANSWERED_MANUALLY"));
            return;
        }

        int keys = 0;
        if (notEmpty(item.qaids())) keys++;
        if (notEmpty(item.optionTexts())) keys++;
        if (notEmpty(item.letters())) keys++;
        if (keys != 1) {
            outcomes.add(skip(item.qid(), "AMBIGUOUS_KEY"));
            return;
        }

        boolean byLetter = notEmpty(item.letters());
        int provided = byLetter ? distinct(item.letters()).size()
                : notEmpty(item.qaids()) ? distinct(item.qaids()).size()
                : distinct(item.optionTexts()).size();
        Integer expected = question.getExpectedAnswerCount();
        if (expected != null && provided != expected) {
            outcomes.add(skip(item.qid(), "ANSWER_COUNT_MISMATCH"));
            return;
        }

        if (byLetter && !isImageOnly(question)) {
            outcomes.add(skip(item.qid(), "LETTER_NOT_ALLOWED_FOR_TEXT_QUESTION"));
            return;
        }

        List<GradingPaperAnswerEntity> options =
                answerRepository.findByPaperIdAndQid(paper.getId(), item.qid());
        Set<Long> correct = new LinkedHashSet<>();

        if (notEmpty(item.qaids())) {
            Set<Long> known = new LinkedHashSet<>();
            options.forEach(option -> known.add(option.getQaid()));
            for (Long qaid : distinct(item.qaids())) {
                if (!known.contains(qaid)) {
                    outcomes.add(skip(item.qid(), "QAID_NOT_IN_QUESTION"));
                    return;
                }
                correct.add(qaid);
            }
        } else if (notEmpty(item.optionTexts())) {
            for (String text : distinct(item.optionTexts())) {
                String hash = Sha256.hexUtf8(text);
                var match = options.stream()
                        .filter(option -> hash.equals(option.getOptionSha256()))
                        .findFirst();
                if (match.isEmpty()) {
                    outcomes.add(skip(item.qid(), "OPTION_TEXT_NOT_FOUND"));
                    return;
                }
                correct.add(match.get().getQaid());
            }
        } else {
            for (String letter : distinct(item.letters())) {
                int index = letterIndex(letter);
                if (index < 0 || index >= options.size()) {
                    outcomes.add(skip(item.qid(), "LETTER_OUT_OF_RANGE"));
                    return;
                }
                correct.add(options.get(index).getQaid());
            }
        }

        plans.add(new Plan(question, options, correct, !byLetter, sourceRef));
        outcomes.add(new ApplyAnswersOutcome(item.qid(), byLetter ? "SUGGESTED" : "APPLIED", null));
    }

    private void persist(Plan plan) {
        plan.options().forEach(option -> option.setCorrect(plan.correctQaids().contains(option.getQaid())));
        answerRepository.saveAll(plan.options());
        if (plan.trusted()) {
            plan.question().applyAnswer(AnswerSource.IMPORTED, plan.sourceRef());
        } else {
            plan.question().suggestAnswer(plan.sourceRef());
        }
        questionRepository.save(plan.question());
    }

    // ---------------------------------------------------------------- helpers

    /** Letters only mean anything when the options are drawn inside the question image. */
    private boolean isImageOnly(GradingPaperQuestionEntity question) {
        return question.getQuestionText() == null && question.getImageSha256() != null;
    }

    private int letterIndex(String letter) {
        if (letter == null || letter.isBlank()) {
            return -1;
        }
        char c = Character.toUpperCase(letter.trim().charAt(0));
        return c < 'A' || c > 'Z' ? -1 : c - 'A';
    }

    private ApplyAnswersOutcome skip(long qid, String reason) {
        return new ApplyAnswersOutcome(qid, "SKIPPED", reason);
    }

    private <T> boolean notEmpty(List<T> list) {
        return list != null && !list.isEmpty();
    }

    private <T> Set<T> distinct(List<T> list) {
        return new LinkedHashSet<>(list);
    }

    private String policyOf(String value) {
        if (value == null || value.isBlank()) {
            return KEEP_EXISTING;
        }
        String policy = value.trim().toUpperCase();
        if (!KEEP_EXISTING.equals(policy) && !OVERWRITE.equals(policy) && !FAIL.equals(policy)) {
            throw new BadRequestException("INVALID_CONFLICT_POLICY",
                    "conflictPolicy không hợp lệ: " + value);
        }
        return policy;
    }

    private GradingPaperEntity requireDraft(UUID paperId) {
        GradingPaperEntity paper = paperRepository.findByIdAndDeletedAtIsNull(paperId)
                .orElseThrow(() -> new NotFoundException("PAPER_NOT_FOUND", "Không tìm thấy đề."));
        if (paper.getStatus() == PaperStatus.READY) {
            throw new ConflictException("PAPER_ALREADY_PUBLISHED",
                    "Đề đã phát hành, không nhập thêm đáp án.");
        }
        return paper;
    }
}
