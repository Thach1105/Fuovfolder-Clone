package com.fuoverflow.grading.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.grading.api.dto.PaperSummaryResponse;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperStatus;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Lets an admin record the answer key and gates publication on it being complete.
 *
 * <p>Only a READY paper is ever used for grading, so the completeness check lives at the point of
 * publication rather than the point of saving: a half-entered key can be saved as a draft but can
 * never produce a score.
 */
@Service
public class PaperAnswerService {

    private final GradingPaperRepository paperRepository;
    private final GradingPaperQuestionRepository questionRepository;
    private final GradingPaperAnswerRepository answerRepository;

    public PaperAnswerService(GradingPaperRepository paperRepository,
                              GradingPaperQuestionRepository questionRepository,
                              GradingPaperAnswerRepository answerRepository) {
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
    }

    @Transactional
    public void setAnswer(UUID paperId, long qid, List<Long> qaids) {
        GradingPaperEntity paper = requireDraft(paperId);
        GradingPaperQuestionEntity question = questionRepository.findByPaperIdAndQid(paper.getId(), qid)
                .orElseThrow(() -> new NotFoundException("QUESTION_NOT_FOUND",
                        "Không tìm thấy câu " + qid + " trong đề."));

        if (qaids == null || qaids.isEmpty()) {
            throw new BadRequestException("ANSWER_REQUIRED", "Chưa chọn đáp án.");
        }
        Set<Long> chosen = new LinkedHashSet<>(qaids);

        List<GradingPaperAnswerEntity> options = answerRepository.findByPaperIdAndQid(paper.getId(), qid);
        Set<Long> known = new LinkedHashSet<>();
        options.forEach(option -> known.add(option.getQaid()));
        for (Long qaid : chosen) {
            if (!known.contains(qaid)) {
                throw new BadRequestException("QAID_NOT_IN_QUESTION",
                        "Lựa chọn " + qaid + " không thuộc câu " + qid + ".");
            }
        }

        Integer expected = question.getExpectedAnswerCount();
        if (expected != null && chosen.size() != expected) {
            throw new BadRequestException("ANSWER_COUNT_MISMATCH",
                    "Câu " + qid + " cần chọn đúng " + expected + " đáp án, đang chọn " + chosen.size() + ".");
        }

        options.forEach(option -> option.setCorrect(chosen.contains(option.getQaid())));
        answerRepository.saveAll(options);

        question.applyAnswer(AnswerSource.MANUAL, null);
        questionRepository.save(question);
    }

    @Transactional
    public PaperSummaryResponse publish(UUID paperId) {
        GradingPaperEntity paper = requireDraft(paperId);
        List<GradingPaperQuestionEntity> questions =
                questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId());

        // A SUGGESTED answer leaves answered=false, so an untrusted key cannot publish a paper.
        List<Long> missing = questions.stream()
                .filter(question -> !question.isAnswered())
                .map(GradingPaperQuestionEntity::getQid)
                .toList();
        if (!missing.isEmpty()) {
            StringJoiner qids = new StringJoiner(", ");
            missing.forEach(qid -> qids.add(Long.toString(qid)));
            throw new ConflictException("PAPER_INCOMPLETE",
                    "Còn " + missing.size() + " câu chưa có đáp án: " + qids + ".");
        }

        paper.markReady();
        paperRepository.save(paper);
        return summary(paper, questions);
    }

    @Transactional
    public void softDelete(UUID paperId) {
        GradingPaperEntity paper = paperRepository.findByIdAndDeletedAtIsNull(paperId)
                .orElseThrow(() -> new NotFoundException("PAPER_NOT_FOUND", "Không tìm thấy đề."));
        paper.softDelete();
        paperRepository.save(paper);
    }

    private GradingPaperEntity requireDraft(UUID paperId) {
        GradingPaperEntity paper = paperRepository.findByIdAndDeletedAtIsNull(paperId)
                .orElseThrow(() -> new NotFoundException("PAPER_NOT_FOUND", "Không tìm thấy đề."));
        if (paper.getStatus() == PaperStatus.READY) {
            throw new ConflictException("PAPER_ALREADY_PUBLISHED",
                    "Đề đã phát hành, không sửa được đáp án.");
        }
        return paper;
    }

    private PaperSummaryResponse summary(GradingPaperEntity paper,
                                         List<GradingPaperQuestionEntity> questions) {
        int answered = (int) questions.stream().filter(GradingPaperQuestionEntity::isAnswered).count();
        return new PaperSummaryResponse(
                paper.getId(),
                paper.getExamCode(),
                paper.getSubjectCode(),
                paper.getStatus().name(),
                questions.size(),
                answered,
                questions.size() - answered,
                paper.getCreatedAt(),
                paper.getPublishedAt());
    }
}
