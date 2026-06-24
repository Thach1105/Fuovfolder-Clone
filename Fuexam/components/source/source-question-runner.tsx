"use client";

import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import type { PublicQuestion } from "@/lib/api/source";
import { sourceMediaUrl } from "@/lib/api/source";

type Props = { questions: PublicQuestion[] };

type AnswerState = { selected: Set<string>; checked: boolean; correct: boolean };

function isAnswerCorrect(question: PublicQuestion, selected: Set<string>): boolean {
  const correctIds = question.options.filter((o) => o.isCorrect).map((o) => o.id);
  if (correctIds.length === 0 || selected.size !== correctIds.length) return false;
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
      <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 text-sm text-muted-foreground">
        Tài liệu này chưa có câu hỏi ôn tập.
      </div>
    );
  }

  if (finished) {
    const score = stats.answered > 0 ? Math.round((stats.correct / stats.answered) * 100) : 0;
    return (
      <div className="rounded-2xl border border-foreground/10 bg-background/70 p-8 text-center backdrop-blur-xl">
        <p className="app-eyebrow">Kết quả ôn tập</p>
        <p className="mt-2 font-display text-6xl">{score}%</p>
        <p className="mt-2 text-sm text-muted-foreground">
          Đúng {stats.correct}/{stats.answered} câu đã làm · Tổng {total} câu
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Button
            className="rounded-full bg-foreground text-background hover:bg-foreground/90"
            onClick={() => { setAnswers({}); setCurrentIndex(0); setFinished(false); }}
          >
            Làm lại từ đầu
          </Button>
          <Button variant="outline" className="rounded-full" onClick={() => setFinished(false)}>
            Xem lại câu hỏi
          </Button>
        </div>
      </div>
    );
  }

  const question = questions[currentIndex];
  const state = answers[question.id] ?? { selected: new Set<string>(), checked: false, correct: false };
  const { selected, checked } = state;
  const questionImages = (question.questionImageUrls && question.questionImageUrls.length > 0)
    ? question.questionImageUrls.map(sourceMediaUrl).filter(Boolean) as string[]
    : question.questionImageUrl ? [sourceMediaUrl(question.questionImageUrl)].filter(Boolean) as string[]
    : [];
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
    setState((prev) => ({ ...prev, checked: true, correct: isAnswerCorrect(question, prev.selected) }));
  }

  function goTo(index: number) {
    setCurrentIndex(Math.min(Math.max(index, 0), total - 1));
  }

  return (
    <div className="space-y-5 rounded-2xl border border-foreground/10 bg-background/70 p-6 backdrop-blur-xl">
      <div className="space-y-2">
        <div className="flex items-center justify-between gap-4">
          <h2 className="font-display text-lg">Luyện câu hỏi</h2>
          <span className="font-mono text-xs text-muted-foreground">
            Câu {currentIndex + 1}/{total} · Đúng {stats.correct}/{stats.answered}
          </span>
        </div>
        <Progress value={progress} className="h-1.5" />
      </div>

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
                "h-7 w-7 rounded-md text-xs font-medium transition",
                i === currentIndex
                  ? "bg-foreground text-background"
                  : a?.checked
                    ? a.correct
                      ? "bg-emerald-500/15 text-emerald-600"
                      : "bg-destructive/15 text-destructive"
                    : "bg-foreground/5 text-muted-foreground hover:bg-foreground/10",
              )}
            >
              {i + 1}
            </button>
          );
        })}
      </div>

      <div className="space-y-3">
        {question.multipleCorrect && <Badge variant="secondary">Nhiều đáp án</Badge>}
        {question.questionText && <p className="text-base font-medium">{question.questionText}</p>}
        {questionImages.length > 0 && (
          <div className={questionImages.length > 1 ? "grid grid-cols-2 gap-2" : ""}>
            {questionImages.map((url, idx) => (
              // eslint-disable-next-line @next/next/no-img-element
              <img key={`${url}-${idx}`} src={url} alt="" className="max-h-64 rounded-xl border border-foreground/10 object-cover" />
            ))}
          </div>
        )}

        <div className="space-y-2">
          {question.options.map((option) => {
            const isSelected = selected.has(option.id);
            const isCorrect = option.isCorrect;
            let cls = "border-foreground/10 hover:border-foreground/30";
            if (checked) {
              if (isCorrect) cls = "border-emerald-500/50 bg-emerald-500/10";
              else if (isSelected) cls = "border-destructive/50 bg-destructive/10";
              else cls = "border-foreground/10 opacity-60";
            } else if (isSelected) {
              cls = "border-foreground bg-foreground/5";
            }
            const optionImage = sourceMediaUrl(option.optionImageUrl);
            return (
              <label
                key={option.id}
                className={cn("flex items-start gap-3 rounded-xl border px-4 py-3 text-sm transition", cls, checked ? "cursor-default" : "cursor-pointer")}
              >
                <input
                  type={question.multipleCorrect ? "checkbox" : "radio"}
                  name={`q-${question.id}`}
                  checked={isSelected}
                  disabled={checked}
                  onChange={() => toggleOption(option.id)}
                  className="mt-1 accent-foreground"
                />
                <span className="flex-1 space-y-2">
                  {option.optionText && <span>{option.optionText}</span>}
                  {optionImage && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={optionImage} alt="" className="max-h-32 rounded-lg border border-foreground/10" />
                  )}
                </span>
                {checked && isCorrect && <span className="text-emerald-600">✓</span>}
                {checked && isSelected && !isCorrect && <span className="text-destructive">✕</span>}
              </label>
            );
          })}
        </div>

        {checked && (
          <div className={cn("rounded-xl px-4 py-3 text-sm", state.correct ? "bg-emerald-500/10 text-emerald-700" : "bg-amber-500/10 text-amber-700")}>
            <p className="font-semibold">{state.correct ? "Chính xác!" : "Chưa đúng"}</p>
            {question.explanation && <p className="mt-1 whitespace-pre-line text-muted-foreground">{question.explanation}</p>}
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2 border-t border-foreground/10 pt-4">
        {!checked ? (
          <Button className="rounded-full bg-foreground text-background hover:bg-foreground/90" disabled={selected.size === 0} onClick={handleCheck}>
            Kiểm tra
          </Button>
        ) : (
          <Button variant="outline" className="rounded-full" onClick={() => setState(() => ({ selected: new Set(), checked: false, correct: false }))}>
            Làm lại câu này
          </Button>
        )}
        <div className="ml-auto flex gap-2">
          <Button variant="ghost" className="rounded-full" disabled={currentIndex === 0} onClick={() => goTo(currentIndex - 1)}>
            ← Trước
          </Button>
          {currentIndex >= total - 1 ? (
            <Button className="rounded-full bg-foreground text-background hover:bg-foreground/90" onClick={() => setFinished(true)}>
              Hoàn thành
            </Button>
          ) : (
            <Button variant="outline" className="rounded-full" onClick={() => goTo(currentIndex + 1)}>
              Sau →
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}