"use client";

import { useMemo, useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";
import { resolveMediaUrl } from "@/lib/api/media";
import type { PublicFeQuestion } from "@/lib/api/exam";
import { Lightbox } from "@/components/exam/Lightbox";
import { ImageWithWatermark } from "@/components/shared/image-with-watermark";
import { ExamCommentThread } from "@/components/exam/exam-comment-thread";

type Props = { questions: PublicFeQuestion[]; showComments?: boolean };
type AnswerState = { selected: Set<string>; checked: boolean; correct: boolean };

const EMPTY_STATE: AnswerState = { selected: new Set<string>(), checked: false, correct: false };

function isAnswerCorrect(question: PublicFeQuestion, selected: Set<string>): boolean {
  const correctIds = question.options.filter((o) => o.isCorrect).map((o) => o.id);
  if (correctIds.length === 0 || selected.size !== correctIds.length) return false;
  return correctIds.every((id) => selected.has(id));
}

function questionImageUrls(question: PublicFeQuestion) {
  if (question.questionImageUrls?.length) {
    return question.questionImageUrls.map(resolveMediaUrl).filter(Boolean) as string[];
  }
  return [];
}

export function ExamFeRunner({ questions, showComments = false }: Props) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, AnswerState>>({});
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

  const total = questions.length;
  const stats = useMemo(() => {
    const values = Object.values(answers);
    const selected = values.filter((a) => a.selected.size > 0).length;
    const checked = values.filter((a) => a.checked).length;
    const correct = values.filter((a) => a.checked && a.correct).length;
    return { selected, checked, correct };
  }, [answers]);

  if (total === 0) {
    return (
      <div className="rounded-2xl border border-foreground/10 bg-background/60 p-6 text-sm text-muted-foreground">
        Môn này chưa có câu hỏi trắc nghiệm FE.
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

  function checkCurrent() {
    setState((prev) => ({
      ...prev,
      checked: true,
      correct: isAnswerCorrect(question, prev.selected),
    }));
  }

  function resetCurrent() {
    setState(() => ({ selected: new Set(), checked: false, correct: false }));
  }

  function goTo(index: number) {
    setCurrentIndex(Math.min(Math.max(index, 0), total - 1));
  }

  return (
    <div className="space-y-4">
      <div className="space-y-5 rounded-2xl border border-foreground/10 bg-background/70 p-4 backdrop-blur-xl sm:p-6">
        <div className="space-y-3">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="font-display text-lg">Luyện câu hỏi FE</h2>
              <p className="mt-1 text-xs text-muted-foreground">
                Chọn đáp án rồi bấm kiểm tra để xem đáp án đúng và giải thích.
              </p>
            </div>
            <div className="font-mono text-xs text-muted-foreground sm:text-right">
              <p>Câu {currentIndex + 1}/{total}</p>
              <p>Đã kiểm tra {stats.checked}/{total} - Đúng {stats.correct}/{stats.checked || 0}</p>
            </div>
          </div>
          <Progress value={progress} className="h-1.5" />
        </div>

        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {question.preview && <Badge variant="secondary">Câu xem thử</Badge>}
            {question.multipleCorrect && <Badge variant="secondary">Nhiều đáp án đúng</Badge>}
          </div>
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
                  <ImageWithWatermark src={url} alt="" loading="lazy" className="max-h-64 w-full object-cover transition-transform group-hover:scale-[1.02]" />
                  <span className="absolute inset-0 bg-black/0 transition-colors group-hover:bg-black/10" />
                </button>
              ))}
            </div>
          )}

          <div className="space-y-2">
            {question.options.map((option) => {
              const isSelected = selected.has(option.id);
              const isCorrect = option.isCorrect;
              let cls = "border-foreground/10 bg-background hover:border-foreground/30 hover:bg-foreground/[0.03]";
              if (checked) {
                if (isCorrect) cls = "border-emerald-500/55 bg-emerald-500/15 text-emerald-950";
                else if (isSelected) cls = "border-destructive/55 bg-destructive/15 text-destructive";
                else cls = "border-foreground/10 bg-background opacity-65";
              } else if (isSelected) {
                cls = "border-sky-500/60 bg-sky-500/15 text-sky-950";
              }
              const optionImage = resolveMediaUrl(option.optionImageUrl);
              return (
                <label
                  key={option.id}
                  className={cn(
                    "flex items-start gap-3 rounded-xl border px-4 py-3 text-sm transition",
                    cls,
                    checked ? "cursor-default" : "cursor-pointer",
                  )}
                >
                  <input
                    type={question.multipleCorrect ? "checkbox" : "radio"}
                    name={`fe-q-${question.id}`}
                    checked={isSelected}
                    disabled={checked}
                    onChange={() => toggleOption(option.id)}
                    className="mt-1 accent-foreground"
                  />
                  <span className="flex-1 space-y-2">
                    {option.optionText && <span>{option.optionText}</span>}
                    {optionImage && (
                      <ImageWithWatermark src={optionImage} alt="" loading="lazy" className="max-h-32 rounded-lg border border-foreground/10" />
                    )}
                  </span>
                  {checked && isCorrect && <span className="text-emerald-700">✓</span>}
                  {checked && isSelected && !isCorrect && <span className="text-destructive">×</span>}
                </label>
              );
            })}
          </div>

          {checked && (
            <div className={cn("rounded-xl px-4 py-3 text-sm", state.correct ? "bg-emerald-500/10 text-emerald-700" : "bg-amber-500/10 text-amber-700")}>
              <p className="font-semibold">{state.correct ? "Chính xác" : "Chưa đúng"}</p>
              {question.explanation && <p className="mt-1 whitespace-pre-line text-muted-foreground">{question.explanation}</p>}
            </div>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2 border-t border-foreground/10 pt-4">
          {!checked ? (
            <Button className="rounded-full bg-foreground text-background hover:bg-foreground/90" disabled={selected.size === 0} onClick={checkCurrent}>
              Kiểm tra
            </Button>
          ) : (
            <Button variant="outline" className="rounded-full" onClick={resetCurrent}>
              Làm lại câu này
            </Button>
          )}
          <div className="ml-auto flex gap-2">
            <Button variant="ghost" className="rounded-full" disabled={currentIndex === 0} onClick={() => goTo(currentIndex - 1)}>
              Trước
            </Button>
            <Button variant="outline" className="rounded-full" disabled={currentIndex >= total - 1} onClick={() => goTo(currentIndex + 1)}>
              Sau
            </Button>
          </div>
        </div>
      </div>

      {showComments && <ExamCommentThread subjectType="fe_question" subjectId={question.id} />}

      {lightboxIndex !== null && images.length > 0 && (
        <Lightbox
          images={images}
          currentIndex={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}
    </div>
  );
}
