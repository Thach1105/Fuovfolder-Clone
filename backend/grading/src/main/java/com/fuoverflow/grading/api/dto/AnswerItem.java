package com.fuoverflow.grading.api.dto;

import java.util.List;

/**
 * One question's answer key from an external source. Exactly one of the three keying strategies must
 * be set; they differ in how much they can be trusted.
 *
 * @param qaids       same identifier space as the paper, fully trusted
 * @param optionTexts matched by option text hash, fully trusted
 * @param letters     A/B/C/D positions, only a suggestion because option order is shuffled
 */
public record AnswerItem(long qid, List<Long> qaids, List<String> optionTexts, List<String> letters) {
}
