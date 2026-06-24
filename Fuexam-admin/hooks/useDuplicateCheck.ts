import { useCallback } from "react";
import type { AdminQuestion, QuestionBody } from "@/lib/api/source";
import { normalizeText, levenshteinSimilarity } from "@/lib/utils/text-similarity";

export interface DuplicateMatch {
  existingQuestion: AdminQuestion;
  similarity: number;
}

export interface OptionDuplicateMatch {
  newOptionText: string;
  existingQuestion: AdminQuestion;
  existingOptionText: string;
  similarity: number;
}

export interface DuplicateResult {
  hasDuplicates: boolean;
  questionMatches: DuplicateMatch[];
  optionMatches: OptionDuplicateMatch[];
}

const DEFAULT_THRESHOLD = 0.8;

export function useDuplicateCheck(
  existingQuestions: AdminQuestion[],
  threshold: number = DEFAULT_THRESHOLD,
) {
  const checkDuplicates = useCallback(
    (newQuestion: QuestionBody, editingId?: string): DuplicateResult => {
      const questionMatches: DuplicateMatch[] = [];
      const optionMatches: OptionDuplicateMatch[] = [];

      const newQNorm = normalizeText(newQuestion.questionText);

      if (newQNorm) {
        for (const existing of existingQuestions) {
          if (existing.id === editingId) continue;
          const existQNorm = normalizeText(existing.questionText);
          if (!existQNorm) continue;
          const sim = levenshteinSimilarity(newQNorm, existQNorm);
          if (sim >= threshold) {
            questionMatches.push({ existingQuestion: existing, similarity: sim });
          }
        }
      }

      const newOptionNorms = (newQuestion.options ?? [])
        .map((o) => normalizeText(o.optionText))
        .filter((t) => t.length > 0);

      for (const newOptNorm of newOptionNorms) {
        for (const existing of existingQuestions) {
          if (existing.id === editingId) continue;
          for (const existOpt of existing.options) {
            const existOptNorm = normalizeText(existOpt.optionText);
            if (!existOptNorm) continue;
            const sim = levenshteinSimilarity(newOptNorm, existOptNorm);
            if (sim >= threshold) {
              optionMatches.push({
                newOptionText: newOptNorm,
                existingQuestion: existing,
                existingOptionText: existOpt.optionText ?? "",
                similarity: sim,
              });
            }
          }
        }
      }

      return {
        hasDuplicates: questionMatches.length > 0 || optionMatches.length > 0,
        questionMatches,
        optionMatches,
      };
    },
    [existingQuestions, threshold],
  );

  return { checkDuplicates };
}
