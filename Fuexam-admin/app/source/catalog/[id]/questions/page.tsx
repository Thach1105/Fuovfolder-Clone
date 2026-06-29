"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { ImageUploader } from "@/components/admin/ImageUploader";
import { MultiImageUploader } from "@/components/admin/MultiImageUploader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  type AdminQuestion,
  type QuestionBody,
  type QuestionOptionBody,
  createQuestion,
  deleteQuestion,
  listAdminQuestions,
  listAdminSourceCatalog,
  reorderQuestions,
  sourceMediaUrl,
  updateQuestion,
} from "@/lib/api/source";
import { ApiError } from "@/lib/api/client";
import { useDuplicateCheck, type DuplicateResult } from "@/hooks/useDuplicateCheck";
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

/** Option with a stable client-side key so React keeps row state aligned on add/remove. */
type LocalOption = QuestionOptionBody & { _key: string };

let optionKeySeq = 0;
function nextOptionKey() {
  optionKeySeq += 1;
  return `opt-${optionKeySeq}`;
}

const EMPTY_OPTION = (): LocalOption => ({
  _key: nextOptionKey(),
  optionText: "",
  optionImageUrl: undefined,
  isCorrect: false,
  sortOrder: 0,
});

export default function AdminSourceQuestionsPage() {
  const params = useParams<{ id: string }>();
  const catalogItemId = params.id;

  const [catalogCode, setCatalogCode] = useState("");
  const [questions, setQuestions] = useState<AdminQuestion[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [questionText, setQuestionText] = useState("");
  const [questionImageUrls, setQuestionImageUrls] = useState<string[]>([]);
  const [explanation, setExplanation] = useState("");
  const [options, setOptions] = useState<LocalOption[]>([EMPTY_OPTION(), EMPTY_OPTION()]);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [duplicateResult, setDuplicateResult] = useState<DuplicateResult | null>(null);
  const [showDuplicateWarning, setShowDuplicateWarning] = useState(false);
  const { checkDuplicates } = useDuplicateCheck(questions);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [catalogItems, questionList] = await Promise.all([
        listAdminSourceCatalog(),
        listAdminQuestions(catalogItemId),
      ]);
      const item = catalogItems.find((c) => c.id === catalogItemId);
      setCatalogCode(item?.code ?? catalogItemId);
      setQuestions(questionList);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được câu hỏi.");
    } finally {
      setLoading(false);
    }
  }, [catalogItemId]);

  useEffect(() => {
    load();
  }, [load]);

  function resetForm() {
    setEditingId(null);
    setQuestionText("");
    setQuestionImageUrls([]);
    setExplanation("");
    setOptions([EMPTY_OPTION(), EMPTY_OPTION()]);
  }

  function startEdit(question: AdminQuestion) {
    setEditingId(question.id);
    setQuestionText(question.questionText ?? "");
    setQuestionImageUrls(question.questionImageUrls ?? []);
    setExplanation(question.explanation ?? "");
    setOptions(
      question.options.map((o) => ({
        _key: nextOptionKey(),
        optionText: o.optionText ?? "",
        optionImageUrl: o.optionImageUrl ?? undefined,
        isCorrect: o.isCorrect,
        sortOrder: o.sortOrder,
      })),
    );
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function updateOption(index: number, patch: Partial<QuestionOptionBody>) {
    setOptions((prev) => prev.map((o, i) => (i === index ? { ...o, ...patch } : o)));
  }

  function addOption() {
    setOptions((prev) => [...prev, EMPTY_OPTION()]);
  }

  function removeOption(index: number) {
    setOptions((prev) => (prev.length <= 2 ? prev : prev.filter((_, i) => i !== index)));
  }

  function buildBody(): QuestionBody {
    return {
      questionText: questionText.trim() || undefined,
      questionImageUrls: questionImageUrls.length > 0 ? questionImageUrls : undefined,
      explanation: explanation.trim() || undefined,
      options: options.map((o, index) => ({
        optionText: o.optionText?.trim() || undefined,
        optionImageUrl: o.optionImageUrl || undefined,
        isCorrect: Boolean(o.isCorrect),
        sortOrder: index,
      })),
    };
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    // Each option needs text or image; the question needs ≥1 correct answer.
    const emptyOption = options.some(
      (o) => !o.optionText?.trim() && !o.optionImageUrl,
    );
    if (emptyOption) {
      const msg = "Mỗi đáp án cần có nội dung hoặc ảnh.";
      setError(msg);
      toast.error(msg);
      return;
    }
    if (!options.some((o) => o.isCorrect)) {
      const msg = "Cần ít nhất một đáp án đúng.";
      setError(msg);
      toast.error(msg);
      return;
    }
    if (!questionText.trim() && questionImageUrls.length === 0) {
      const msg = "Câu hỏi cần có nội dung hoặc ảnh.";
      setError(msg);
      toast.error(msg);
      return;
    }

    const result = checkDuplicates(buildBody(), editingId ?? undefined);
    if (result.hasDuplicates) {
      setDuplicateResult(result);
      setShowDuplicateWarning(true);
      return;
    }

    await saveQuestion();
  }

  async function saveQuestion() {
    setSaving(true);
    try {
      if (editingId) {
        await updateQuestion(catalogItemId, editingId, buildBody());
        toast.success("Đã cập nhật câu hỏi.");
      } else {
        await createQuestion(catalogItemId, buildBody());
        toast.success("Đã tạo câu hỏi.");
      }
      resetForm();
      await load();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Lưu thất bại.";
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  async function handleForceSubmit() {
    setShowDuplicateWarning(false);
    setDuplicateResult(null);
    await saveQuestion();
  }

  async function confirmDelete() {
    if (!deleteId) return;
    try {
      await deleteQuestion(catalogItemId, deleteId);
      toast.success("Đã xóa câu hỏi.");
      if (editingId === deleteId) resetForm();
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteId(null);
    }
  }

  function toggleExpanded(questionId: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(questionId)) next.delete(questionId);
      else next.add(questionId);
      return next;
    });
  }

  async function moveQuestion(questionId: string, direction: -1 | 1) {
    const index = questions.findIndex((q) => q.id === questionId);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= questions.length) return;
    const ids = questions.map((q) => q.id);
    [ids[index], ids[target]] = [ids[target], ids[index]];
    try {
      await reorderQuestions(catalogItemId, ids);
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Sắp xếp thất bại.");
    }
  }

  return (
    <AdminShell
      title={`Câu hỏi — ${catalogCode}`}
      description="Thêm câu hỏi trắc nghiệm (có thể kèm ảnh) cho tài liệu"
    >
      <Link
        href="/source/catalog"
        className="mb-4 inline-block text-sm text-primary hover:underline"
      >
        ← Quay lại danh mục
      </Link>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {editingId ? "Sửa câu hỏi" : "Thêm câu hỏi"}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              {error && (
                <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {error}
                </div>
              )}
              <Textarea
                placeholder="Nội dung câu hỏi (có thể để trống nếu dùng ảnh)"
                value={questionText}
                onChange={(e) => setQuestionText(e.target.value)}
              />
              <MultiImageUploader
                label="Anh cau hoi"
                purpose="source_question"
                value={questionImageUrls}
                onChange={setQuestionImageUrls}
              />
              <Textarea
                placeholder="Giải thích (hiện sau khi kiểm tra)"
                value={explanation}
                onChange={(e) => setExplanation(e.target.value)}
              />

              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <p className="text-xs font-semibold uppercase text-muted-foreground">Đáp án</p>
                  <button
                    type="button"
                    className="text-xs text-primary hover:underline"
                    onClick={addOption}
                  >
                    + Thêm đáp án
                  </button>
                </div>
                {options.map((option, index) => (
                  <div key={option._key} className="space-y-2 rounded-lg border border-border p-3">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs text-muted-foreground">Đáp án {index + 1}</span>
                      <label className="flex items-center gap-2 text-xs">
                        <Checkbox
                          checked={Boolean(option.isCorrect)}
                          onCheckedChange={(v) => updateOption(index, { isCorrect: Boolean(v) })}
                        />
                        Đúng
                      </label>
                      {options.length > 2 && (
                        <button
                          type="button"
                          className="text-xs text-destructive"
                          onClick={() => removeOption(index)}
                        >
                          Xóa
                        </button>
                      )}
                    </div>
                    <Input
                      placeholder="Nội dung đáp án"
                      value={option.optionText ?? ""}
                      onChange={(e) => updateOption(index, { optionText: e.target.value })}
                    />
                    <ImageUploader
                      label="Ảnh đáp án"
                      purpose="source_question"
                      value={option.optionImageUrl ?? null}
                      onChange={(url) => updateOption(index, { optionImageUrl: url ?? undefined })}
                    />
                  </div>
                ))}
              </div>

              <div className="flex gap-2">
                <Button type="submit" disabled={saving}>
                  {saving ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo câu hỏi"}
                </Button>
                {editingId && (
                  <Button type="button" variant="outline" onClick={resetForm}>
                    Hủy
                  </Button>
                )}
              </div>
            </form>
          </CardContent>
        </Card>

        <div className="rounded-xl border border-border">
          {loading ? (
            <p className="p-4 text-sm text-muted-foreground">Đang tải...</p>
          ) : questions.length === 0 ? (
            <p className="p-4 text-sm text-muted-foreground">Chưa có câu hỏi.</p>
          ) : (
            <ul className="divide-y divide-border">
              {questions.map((q, index) => {
                const preview = q.questionText?.trim() || "(Ảnh)";
                const correctCount = q.options.filter((o) => o.isCorrect).length;
                const expanded = expandedIds.has(q.id);
                const questionImage = sourceMediaUrl(q.questionImageUrl);
                return (
                  <li key={q.id} className="text-sm">
                    <div className="flex items-start gap-2 p-4">
                      <button
                        type="button"
                        className="mt-0.5 shrink-0 text-muted-foreground hover:text-foreground"
                        onClick={() => toggleExpanded(q.id)}
                        aria-label={expanded ? "Thu gọn" : "Mở rộng"}
                      >
                        {expanded ? (
                          <ChevronDown className="h-4 w-4" />
                        ) : (
                          <ChevronRight className="h-4 w-4" />
                        )}
                      </button>
                      <button
                        type="button"
                        className="min-w-0 flex-1 text-left"
                        onClick={() => toggleExpanded(q.id)}
                      >
                        <p className="font-medium text-foreground">
                          {index + 1}. {preview}
                        </p>
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {q.options.length} đáp án · {correctCount} đúng
                          {q.multipleCorrect ? " · chọn nhiều" : ""}
                        </p>
                      </button>
                      <div className="flex shrink-0 flex-col items-end gap-1 text-xs">
                        <button
                          type="button"
                          className="text-primary hover:underline"
                          onClick={() => startEdit(q)}
                        >
                          Sửa
                        </button>
                        <button
                          type="button"
                          className="text-destructive hover:underline"
                          onClick={() => setDeleteId(q.id)}
                        >
                          Xóa
                        </button>
                        <div className="flex gap-1">
                          <button
                            type="button"
                            className="text-muted-foreground hover:underline disabled:opacity-30"
                            disabled={index === 0}
                            onClick={() => moveQuestion(q.id, -1)}
                          >
                            ↑
                          </button>
                          <button
                            type="button"
                            className="text-muted-foreground hover:underline disabled:opacity-30"
                            disabled={index === questions.length - 1}
                            onClick={() => moveQuestion(q.id, 1)}
                          >
                            ↓
                          </button>
                        </div>
                      </div>
                    </div>

                    {expanded && (
                      <div className="space-y-4 border-t border-border bg-muted/20 px-4 py-4 pl-10">
                        {q.questionImageUrls && q.questionImageUrls.length > 0 && (
                          <div className="grid grid-cols-2 gap-2">
                            {q.questionImageUrls.map((url, idx) => {
                              const imgUrl = sourceMediaUrl(url);
                              return (
                                // eslint-disable-next-line @next/next/no-img-element
                                <img
                                  key={`${url}-${idx}`}
                                  src={imgUrl ?? undefined}
                                  alt={`Anh ${idx + 1}`}
                                  loading="lazy"
                                  className="max-h-32 rounded-lg border border-border object-cover"
                                />
                              );
                            })}
                          </div>
                        )}
                        {q.explanation && (
                          <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground">
                              Giải thích
                            </p>
                            <p className="mt-1 whitespace-pre-line text-muted-foreground">
                              {q.explanation}
                            </p>
                          </div>
                        )}
                        <ul className="space-y-2">
                          {q.options.map((option, optionIndex) => {
                            const optionImage = sourceMediaUrl(option.optionImageUrl);
                            return (
                              <li
                                key={option.id}
                                className={`rounded-lg border px-3 py-2 ${
                                  option.isCorrect
                                    ? "border-emerald-500/50 bg-emerald-500/10"
                                    : "border-border bg-card"
                                }`}
                              >
                                <div className="flex items-center gap-2 text-xs">
                                  <span className="text-muted-foreground">#{optionIndex + 1}</span>
                                  {option.isCorrect && (
                                    <span className="rounded bg-emerald-500/20 px-1.5 py-0.5 font-medium text-emerald-500">
                                      Đúng
                                    </span>
                                  )}
                                </div>
                                {option.optionText && (
                                  <p className="mt-1 text-foreground">{option.optionText}</p>
                                )}
                                {optionImage && (
                                  /* eslint-disable-next-line @next/next/no-img-element */
                                  <img
                                    src={optionImage}
                                    alt=""
                                    loading="lazy"
                                    className="mt-2 max-h-28 rounded border border-border"
                                  />
                                )}
                              </li>
                            );
                          })}
                        </ul>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={deleteId != null}
        title="Xóa câu hỏi?"
        description="Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDelete}
        onOpenChange={(o) => !o && setDeleteId(null)}
      />

      <AlertDialog
        open={showDuplicateWarning}
        onOpenChange={(o) => {
          if (!o) {
            setShowDuplicateWarning(false);
            setDuplicateResult(null);
          }
        }}
      >
        <AlertDialogContent className="max-h-[80vh] overflow-y-auto">
          <AlertDialogHeader>
            <AlertDialogTitle>Phát hiện nội dung trùng lặp</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-3 text-sm">
                {duplicateResult?.questionMatches.length ? (
                  <div>
                    <p className="font-semibold text-foreground">Câu hỏi trùng:</p>
                    <ul className="mt-1 list-disc space-y-1 pl-5">
                      {duplicateResult.questionMatches.map((m, i) => {
                        const idx = questions.findIndex((q) => q.id === m.existingQuestion.id) + 1;
                        const preview = (m.existingQuestion.questionText ?? "").slice(0, 60);
                        return (
                          <li key={i}>
                            Giống {Math.round(m.similarity * 100)}% với câu #{idx}:
                            &ldquo;{preview}{(m.existingQuestion.questionText?.length ?? 0) > 60 ? "…" : ""}&rdquo;
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                ) : null}
                {duplicateResult?.optionMatches.length ? (
                  <div>
                    <p className="font-semibold text-foreground">Đáp án trùng:</p>
                    <ul className="mt-1 list-disc space-y-1 pl-5">
                      {duplicateResult.optionMatches.map((m, i) => {
                        const idx = questions.findIndex((q) => q.id === m.existingQuestion.id) + 1;
                        return (
                          <li key={i}>
                            Đáp án &ldquo;{m.newOptionText.slice(0, 40)}&rdquo; giống{" "}
                            {Math.round(m.similarity * 100)}% với đáp án &ldquo;
                            {m.existingOptionText.slice(0, 40)}&rdquo; trong câu #{idx}
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                ) : null}
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Quay lại sửa</AlertDialogCancel>
            <AlertDialogAction onClick={handleForceSubmit}>Vẫn tạo</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </AdminShell>
  );
}
