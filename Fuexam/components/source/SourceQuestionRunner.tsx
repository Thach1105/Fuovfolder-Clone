"use client";

import { useMemo, useState } from "react";
import type { PublicQuestion } from "@/lib/api/source";
import { sourceMediaUrl } from "@/lib/api/source";
import { cn } from "@/lib/utils";

type Props = {
  questions: PublicQuestion[];
};

type AnswerState = {
  selected: Set<string>;
  checked: boolean;
  correct: boolean;
};

function isAnswerCorrect(question: PublicQuestion, selected: Set<string>): boolean {
  const correctIds = question.options.filter((o) => o.isCorrect).map((o) => o.id);
  if (correctIds.length === 0) return false;
  if (selected.size !== correctIds.length) return false;
  return correctIds.every((id) => selected.has(id));
}

export function SourceQuestionRunner({ questions }: Props) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, AnswerState>>({});
  const [finished, setFinished] = useState(false);

  const total = questions.length;

  const stats = useMemo(() => {
    const answered = Object.values(answers).filter((a) => a.checked).length;
    const correct = Object.values(answers).filter((a) => a.checked && a.correct).length;
    return { answered, correct };
  }, [answers]);

  if (total === 0) {
    return (
      <div className="card p-5 text-sm text-ink-500">
        Tài liệu này chưa có câu hỏi ôn tập.
      </div>
    );
  }

  if (finished) {
    const score = stats.answered > 0 ? Math.round((stats.correct / stats.answered) * 100) : 0;
    return (
      <div className="card space-y-4 p-6 text-center">
        <p className="eyebrow">Kết quả ôn tập</p>
        <p className="font-display text-5xl text-fuo-700">{score}%</p>
        <p className="text-sm text-ink-600">
          Đúng {stats.correct}/{stats.answered} câu đã làm · Tổng {total} câu
        </p>
        <div className="flex flex-wrap justify-center gap-2 pt-2">
          <button
            type="button"
            className="btn-accent"
            onClick={() => {
              setAnswers({});
              setCurrentIndex(0);
              setFinished(false);
            }}
          >
            Làm lại từ đầu
          </button>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => setFinished(false)}
          >
            Xem lại câu hỏi
          </button>
        </div>
      </div>
    );
  }

  const question = questions[currentIndex];
  const state = answers[question.id] ?? { selected: new Set<string>(), checked: false, correct: false };
  const { selected, checked } = state;
  const questionImage = sourceMediaUrl(question.questionImageUrl);
  const progress = Math.round(((currentIndex + 1) / total) * 100);

  function setState(updater: (prev: AnswerState) => AnswerState) {
    setAnswers((prev) => ({
      ...prev,
      [question.id]: updater(prev[question.id] ?? { selected: new Set(), checked: false, correct: false }),
    }));
  }

  function toggleOption(optionId: string) {
    if (checked) return;
    setState((prev) => {
      const next = new Set(prev.selected);
      if (question.multipleCorrect) {
        if (next.has(optionId)) next.delete(optionId);
        else next.add(optionId);
      } else {
        next.clear();
        next.add(optionId);
      }
      return { ...prev, selected: next };
    });
  }

  function handleCheck() {
    if (selected.size === 0) return;
    setState((prev) => ({
      ...prev,
      checked: true,
      correct: isAnswerCorrect(question, prev.selected),
    }));
  }

  function handleRetryQuestion() {
    setState(() => ({ selected: new Set(), checked: false, correct: false }));
  }

  function goTo(index: number) {
    setCurrentIndex(Math.min(Math.max(index, 0), total - 1));
  }

  return (
    <div className="card space-y-4 p-5">
      {/* Header + progress */}
      <div className="space-y-2">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-sm font-semibold text-ink-900">Câu hỏi ôn tập</h2>
          <span className="text-xs text-ink-500">
            Câu {currentIndex + 1} / {total} · Đúng {stats.correct}/{stats.answered}
          </span>
        </div>
        <div className="h-1.5 overflow-hidden rounded-full bg-ink-100">
          <div
            className="h-full rounded-full bg-fuo-600 transition-all duration-300"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      {/* Question dots */}
      <div className="flex flex-wrap gap-1.5">
        {questions.map((q, i) => {
          const a = answers[q.id];
          return (
            <button
              key={q.id}
              type="button"
              onClick={() => goTo(i)}
              aria-label={`Câu ${i + 1}`}
              className={cn(
                "h-7 w-7 rounded-lg text-xs font-medium transition",
                i === currentIndex
                  ? "bg-ink-900 text-ink-50"
                  : a?.checked
                    ? a.correct
                      ? "bg-emerald-100 text-emerald-700"
                      : "bg-red-100 text-red-700"
                    : "bg-ink-100 text-ink-500 hover:bg-ink-200",
              )}
            >
              {i + 1}
            </button>
          );
        })}
      </div>

      {/* Question body */}
      <div className="space-y-3">
        <div className="flex items-start gap-2">
          {question.multipleCorrect && (
            <span className="rounded-full bg-fuo-50 px-2 py-0.5 text-[10px] font-semibold uppercase text-fuo-700">
              Nhiều đáp án
            </span>
          )}
        </div>
        {question.questionText && (
          <p className="text-base font-medium text-ink-900">{question.questionText}</p>
        )}
        {questionImage && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={questionImage} alt="" className="max-h-64 rounded-xl border border-ink-200" />
        )}

        <div className="space-y-2">
          {question.options.map((option) => {
            const isSelected = selected.has(option.id);
            const isCorrect = option.isCorrect;
            let cls = "border-ink-200 hover:border-ink-300";
            if (checked) {
              if (isCorrect) cls = "border-emerald-400 bg-emerald-50";
              else if (isSelected) cls = "border-red-400 bg-red-50";
              else cls = "border-ink-200 opacity-70";
            } else if (isSelected) {
              cls = "border-fuo-400 bg-fuo-50";
            }

            const optionImage = sourceMediaUrl(option.optionImageUrl);
            return (
              <label
                key={option.id}
                className={cn(
                  "flex cursor-pointer items-start gap-3 rounded-xl border px-3.5 py-2.5 text-sm transition",
                  cls,
                  checked && "cursor-default",
                )}
              >
                <input
                  type={question.multipleCorrect ? "checkbox" : "radio"}
                  name={`q-${question.id}`}
                  checked={isSelected}
                  disabled={checked}
                  onChange={() => toggleOption(option.id)}
                  className="mt-1 accent-fuo-600"
                />
                <span className="flex-1 space-y-2">
                  {option.optionText && <span className="text-ink-700">{option.optionText}</span>}
                  {optionImage && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={optionImage} alt="" className="max-h-32 rounded-lg border border-ink-200" />
                  )}
                </span>
                {checked && isCorrect && <span className="text-emerald-600">✓</span>}
                {checked && isSelected && !isCorrect && <span className="text-red-500">✕</span>}
              </label>
            );
          })}
        </div>

        {checked && (
          <div
            className={cn(
              "rounded-xl px-3.5 py-2.5 text-sm",
              state.correct ? "bg-emerald-50 text-emerald-800" : "bg-amber-50 text-amber-800",
            )}
          >
            <p className="font-semibold">{state.correct ? "Chính xác!" : "Chưa đúng"}</p>
            {question.explanation && (
              <p className="mt-1 whitespace-pre-line text-ink-600">{question.explanation}</p>
            )}
          </div>
        )}
      </div>

      {/* Controls */}
      <div className="flex flex-wrap items-center gap-2 border-t border-ink-200 pt-4">
        {!checked ? (
          <button
            type="button"
            className="btn-accent"
            disabled={selected.size === 0}
            onClick={handleCheck}
          >
            Kiểm tra
          </button>
        ) : (
          <button type="button" className="btn-secondary" onClick={handleRetryQuestion}>
            Làm lại câu này
          </button>
        )}
        <div className="ml-auto flex gap-2">
          <button
            type="button"
            className="btn-ghost"
            disabled={currentIndex === 0}
            onClick={() => goTo(currentIndex - 1)}
          >
            ← Câu trước
          </button>
          {currentIndex >= total - 1 ? (
            <button type="button" className="btn-primary" onClick={() => setFinished(true)}>
              Hoàn thành
            </button>
          ) : (
            <button type="button" className="btn-primary" onClick={() => goTo(currentIndex + 1)}>
              Câu sau →
            </button>
          )}
        </div>
      </div>
    </div>
  );
}