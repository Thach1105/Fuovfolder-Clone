"use client";

import { useState } from "react";
import type { PublicQuestion } from "@/lib/api/source";
import { sourceMediaUrl } from "@/lib/api/source";

type Props = {
  questions: PublicQuestion[];
};

export function SourceQuestionRunner({ questions }: Props) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [checked, setChecked] = useState(false);

  if (questions.length === 0) {
    return (
      <div className="card p-5 text-sm text-slate-500">
        Tài liệu này chưa có câu hỏi ôn tập.
      </div>
    );
  }

  const question = questions[currentIndex];

  function toggleOption(optionId: string) {
    if (checked) return;
    setSelected((prev) => {
      const next = new Set(prev);
      if (question.multipleCorrect) {
        if (next.has(optionId)) next.delete(optionId);
        else next.add(optionId);
      } else {
        next.clear();
        next.add(optionId);
      }
      return next;
    });
  }

  function handleCheck() {
    if (selected.size === 0) return;
    setChecked(true);
  }

  function handleReset() {
    setSelected(new Set());
    setChecked(false);
  }

  function handleNext() {
    setCurrentIndex((i) => Math.min(i + 1, questions.length - 1));
    handleReset();
  }

  function handlePrev() {
    setCurrentIndex((i) => Math.max(i - 1, 0));
    handleReset();
  }

  const questionImage = sourceMediaUrl(question.questionImageUrl);

  return (
    <div className="card space-y-4 p-5">
      <div className="flex items-center justify-between gap-4">
        <h2 className="text-sm font-semibold text-slate-900">Câu hỏi ôn tập</h2>
        <span className="text-xs text-slate-500">
          {currentIndex + 1} / {questions.length}
        </span>
      </div>

      <div className="space-y-3">
        {question.questionText && (
          <p className="text-sm font-medium text-slate-800">{question.questionText}</p>
        )}
        {questionImage && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={questionImage} alt="" className="max-h-64 rounded-lg border border-slate-200" />
        )}

        <div className="space-y-2">
          {question.options.map((option) => {
            const isSelected = selected.has(option.id);
            const isCorrect = option.isCorrect;
            let borderClass = "border-slate-200";
            if (checked) {
              if (isCorrect) borderClass = "border-emerald-400 bg-emerald-50";
              else if (isSelected) borderClass = "border-red-400 bg-red-50";
            } else if (isSelected) {
              borderClass = "border-fuo-400 bg-fuo-50";
            }

            const optionImage = sourceMediaUrl(option.optionImageUrl);
            return (
              <label
                key={option.id}
                className={`flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2 text-sm ${borderClass}`}
              >
                <input
                  type={question.multipleCorrect ? "checkbox" : "radio"}
                  name={`q-${question.id}`}
                  checked={isSelected}
                  disabled={checked}
                  onChange={() => toggleOption(option.id)}
                  className="mt-1"
                />
                <span className="flex-1 space-y-2">
                  {option.optionText && <span className="text-slate-700">{option.optionText}</span>}
                  {optionImage && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={optionImage} alt="" className="max-h-32 rounded border border-slate-200" />
                  )}
                </span>
              </label>
            );
          })}
        </div>

        {checked && question.explanation && (
          <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
            <p className="font-medium text-slate-700">Giải thích</p>
            <p className="mt-1 whitespace-pre-line">{question.explanation}</p>
          </div>
        )}
      </div>

      <div className="flex flex-wrap gap-2">
        {!checked ? (
          <button
            type="button"
            className="btn-primary"
            disabled={selected.size === 0}
            onClick={handleCheck}
          >
            Kiểm tra
          </button>
        ) : (
          <button type="button" className="rounded-lg border border-slate-300 px-4 py-2 text-sm" onClick={handleReset}>
            Làm lại
          </button>
        )}
        <button
          type="button"
          className="rounded-lg border border-slate-300 px-4 py-2 text-sm disabled:opacity-40"
          disabled={currentIndex === 0}
          onClick={handlePrev}
        >
          Câu trước
        </button>
        <button
          type="button"
          className="rounded-lg border border-slate-300 px-4 py-2 text-sm disabled:opacity-40"
          disabled={currentIndex >= questions.length - 1}
          onClick={handleNext}
        >
          Câu sau
        </button>
      </div>
    </div>
  );
}
