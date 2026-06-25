"use client";

import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { cn } from "@/lib/utils";
import type { PublicQuestion } from "@/lib/api/source";
import { sourceMediaUrl } from "@/lib/api/source";
import { Lightbox } from "@/components/exam/Lightbox";

type Props = { questions: PublicQuestion[] };

type AnswerState = { selected: Set<string>; checked: boolean; correct: boolean };

const EMPTY_STATE: AnswerState = { selected: new Set<string>(), checked: false, correct: false };

function isAnswerCorrect(question: PublicQuestion, selected: Set<string>): boolean {
  const correctIds = question.options.filter((o) => o.isCorrect).map((o) => o.id);
  if (correctIds.length === 0 || selected.size !== correctIds.length) return false;
  return correctIds.every((id) => selected.has(id));
}

function questionImageUrls(question: PublicQuestion) {
  if (question.questionImageUrls?.length) {
    return question.questionImageUrls.map(sourceMediaUrl).filter(Boolean) as string[];
  }
  const single = sourceMediaUrl(question.questionImageUrl);
  return single ? [single] : [];
}

export function SourceQuestionRunner({ questions }: Props) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, AnswerState>>({});
  const [finished, setFinished] = useState(false);
  const [finishConfirmOpen, setFinishConfirmOpen] = useState(false);
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const [warning, setWarning] = useState<string | null>(null);

  const total = questions.length;
  const stats = useMemo(() => {
    const values = Object.values(answers);
    const selected = values.filter((a) => a.selected.size > 0).length;
    const checked = values.filter((a) => a.checked).length;
    const correct = values.filter((a) => a.checked && a.correct).length;
    return { selected, checked, correct, unchecked: Math.max(0, total - checked) };
  }, [answers, total]);

  if (total === 0) {
    return (
      <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 text-sm text-muted-foreground">
        Tài liệu này chưa có câu hỏi ôn tập.
      </div>
    );
  }

  if (finished) {
    const score = Math.round((stats.correct / total) * 100);
    return (
      <div className="rounded-2xl border border-foreground/10 bg-background/70 p-8 text-center backdrop-blur-xl">
        <p className="app-eyebrow">Kết quả ôn tập</p>
        <p className="mt-2 font-display text-6xl">{score}%</p>
        <p className="mt-2 text-sm text-muted-foreground">
          Đúng {stats.correct}/{total} câu - Đã kiểm tra {stats.checked}/{total} - Bỏ qua {stats.unchecked}
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Button
            className="rounded-full bg-foreground text-background hover:bg-foreground/90"
            onClick={() => {
              setAnswers({});
              setCurrentIndex(0);
              setFinished(false);
              setWarning(null);
            }}
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
  const state = answers[question.id] ?? EMPTY_STATE;
  const { selected, checked } = state;
  const images = questionImageUrls(question);
  const progress = Math.round((stats.checked / total) * 100);

  function setState(updater: (prev: AnswerState) => AnswerState) {
    setAnswers((prev) => ({
      ...prev,
      [question.id]: updater(prev[question.id] ?? { selected: new Set(), checked: false, correct: false }),
    }));
  }

  function toggleOption(optionId: string) {
    if (checked) return;
    setWarning(null);
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
    if (selected.size === 0) {
      setWarning("Chọn ít nhất một đáp án trước khi kiểm tra.");
      return;
    }
    setWarning(null);
    setState((prev) => ({ ...prev, checked: true, correct: isAnswerCorrect(question, prev.selected) }));
  }

  function resetCurrentQuestion() {
    setWarning(null);
    setState(() => ({ selected: new Set(), checked: false, correct: false }));
  }

  function goTo(index: number) {
    setWarning(null);
    setCurrentIndex(Math.min(Math.max(index, 0), total - 1));
  }

  function requestFinish() {
    if (stats.checked < total) {
      setFinishConfirmOpen(true);
      return;
    }
    setFinished(true);
  }

  function questionStatus(q: PublicQuestion) {
    const answer = answers[q.id];
    if (!answer || answer.selected.size === 0) return "empty";
    if (!answer.checked) return "selected";
    return answer.correct ? "correct" : "wrong";
  }

  return (
    <>
      <div className="space-y-5 rounded-2xl border border-foreground/10 bg-background/70 p-6 backdrop-blur-xl">
        <div className="space-y-3">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="font-display text-lg">Luyện câu hỏi</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Chọn đáp án, bấm kiểm tra từng câu, rồi nộp khi muốn xem tổng kết.
              </p>
            </div>
            <div className="text-right font-mono text-xs text-muted-foreground">
              <p>Câu {currentIndex + 1}/{total}</p>
              <p>Đã chọn {stats.selected}/{total} - Đã kiểm tra {stats.checked}/{total} - Đúng {stats.correct}/{stats.checked}</p>
            </div>
          </div>
          <Progress value={progress} className="h-1.5" />
          <div className="flex flex-wrap gap-1.5">
            {questions.map((q, index) => {
              const status = questionStatus(q);
              return (
                <button
                  key={q.id}
                  type="button"
                  onClick={() => goTo(index)}
                  className={cn(
                    "flex h-8 w-8 items-center justify-center rounded-full border text-xs font-semibold transition",
                    index === currentIndex && "ring-2 ring-foreground/30",
                    status === "empty" && "border-foreground/15 text-muted-foreground hover:bg-foreground/5",
                    status === "selected" && "border-sky-500/40 bg-sky-500/10 text-sky-700",
                    status === "correct" && "border-emerald-500/40 bg-emerald-500/10 text-emerald-700",
                    status === "wrong" && "border-destructive/40 bg-destructive/10 text-destructive",
                  )}
                  aria-label={`Đi tới câu ${index + 1}`}
                >
                  {index + 1}
                </button>
              );
            })}
          </div>
          <div className="flex flex-wrap gap-3 text-[11px] text-muted-foreground">
            <span>Trắng: chưa làm</span>
            <span>Xanh dương: đã chọn</span>
            <span>Xanh lá: đúng</span>
            <span>Đỏ: sai</span>
          </div>
        </div>

        <div className="space-y-3">
          {question.multipleCorrect && <Badge variant="secondary">Nhiều đáp án đúng</Badge>}
          {question.questionText && <p className="text-base font-medium">{question.questionText}</p>}
          {images.length > 0 && (
            <div className={images.length > 1 ? "grid grid-cols-2 gap-2" : ""}>
              {images.map((url, idx) => (
                <button
                  key={`${url}-${idx}`}
                  type="button"
                  onClick={() => setLightboxIndex(idx)}
                  className="group relative overflow-hidden rounded-xl border border-foreground/10"
                  aria-label="Xem ảnh lớn"
                >
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={url} alt="" className="max-h-64 w-full object-cover transition-transform group-hover:scale-[1.02]" />
                  <span className="absolute inset-0 bg-black/0 transition-colors group-hover:bg-black/10" />
                </button>
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
                cls = "border-sky-500 bg-sky-500/10";
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
                  {checked && isSelected && !isCorrect && <span className="text-destructive">×</span>}
                </label>
              );
            })}
          </div>

          {warning && <p className="rounded-xl bg-amber-500/10 px-4 py-3 text-sm text-amber-700">{warning}</p>}

          {checked && (
            <div className={cn("rounded-xl px-4 py-3 text-sm", state.correct ? "bg-emerald-500/10 text-emerald-700" : "bg-amber-500/10 text-amber-700")}>
              <p className="font-semibold">{state.correct ? "Chính xác" : "Chưa đúng"}</p>
              {question.explanation && <p className="mt-1 whitespace-pre-line text-muted-foreground">{question.explanation}</p>}
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2 border-t border-foreground/10 pt-4">
          {!checked ? (
            <Button className="rounded-full bg-foreground text-background hover:bg-foreground/90" onClick={handleCheck}>
              Kiểm tra
            </Button>
          ) : (
            <Button variant="outline" className="rounded-full" onClick={resetCurrentQuestion}>
              Làm lại câu này
            </Button>
          )}
          <div className="ml-auto flex gap-2">
            <Button variant="ghost" className="rounded-full" disabled={currentIndex === 0} onClick={() => goTo(currentIndex - 1)}>
              Trước
            </Button>
            {currentIndex >= total - 1 ? (
              <Button className="rounded-full bg-foreground text-background hover:bg-foreground/90" onClick={requestFinish}>
                Nộp bài
              </Button>
            ) : (
              <Button variant="outline" className="rounded-full" onClick={() => goTo(currentIndex + 1)}>
                Sau
              </Button>
            )}
          </div>
        </div>
      </div>

      {lightboxIndex !== null && images.length > 0 && (
        <Lightbox
          images={images}
          currentIndex={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}

      <AlertDialog open={finishConfirmOpen} onOpenChange={setFinishConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Nộp bài khi còn câu chưa kiểm tra?</AlertDialogTitle>
            <AlertDialogDescription>
              Bạn đã kiểm tra {stats.checked}/{total} câu. Các câu chưa kiểm tra sẽ được tính là bỏ qua trong kết quả.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Xem tiếp</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                setFinishConfirmOpen(false);
                setFinished(true);
              }}
            >
              Vẫn nộp bài
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
