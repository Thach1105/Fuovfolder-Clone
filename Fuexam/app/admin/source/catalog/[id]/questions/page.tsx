"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { SourceMediaUploader } from "@/components/source/SourceMediaUploader";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { useSubmit } from "@/hooks/use-submit";
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

const inputClass =
  "w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white";

const EMPTY_OPTION = (): QuestionOptionBody => ({
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
  const { loading, error: loadError, run } = useAsyncAction();
  const { error: submitError, submit } = useSubmit("Lưu thất bại");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [questionText, setQuestionText] = useState("");
  const [questionImageUrl, setQuestionImageUrl] = useState<string | null>(null);
  const [explanation, setExplanation] = useState("");
  const [options, setOptions] = useState<QuestionOptionBody[]>([EMPTY_OPTION(), EMPTY_OPTION(), EMPTY_OPTION(), EMPTY_OPTION()]);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const error = loadError || submitError;

  const load = useCallback(() => run(async () => {
    const [catalogItems, questionList] = await Promise.all([
      listAdminSourceCatalog(),
      listAdminQuestions(catalogItemId),
    ]);
    const item = catalogItems.find((c) => c.id === catalogItemId);
    setCatalogCode(item?.code ?? catalogItemId);
    setQuestions(questionList);
  }), [run, catalogItemId]);

  useEffect(() => {
    load();
  }, [load]);

  function resetForm() {
    setEditingId(null);
    setQuestionText("");
    setQuestionImageUrl(null);
    setExplanation("");
    setOptions([EMPTY_OPTION(), EMPTY_OPTION(), EMPTY_OPTION(), EMPTY_OPTION()]);
  }

  function startEdit(question: AdminQuestion) {
    setEditingId(question.id);
    setQuestionText(question.questionText ?? "");
    setQuestionImageUrl(question.questionImageUrl);
    setExplanation(question.explanation ?? "");
    setOptions(
      question.options.map((o) => ({
        optionText: o.optionText ?? "",
        optionImageUrl: o.optionImageUrl ?? undefined,
        isCorrect: o.isCorrect,
        sortOrder: o.sortOrder,
      })),
    );
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
      questionImageUrl: questionImageUrl ?? undefined,
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
    const body = buildBody();
    const result = await submit(async () => {
      if (editingId) {
        await updateQuestion(catalogItemId, editingId, body);
      } else {
        await createQuestion(catalogItemId, body);
      }
      return true;
    });
    if (result) {
      resetForm();
      await load();
    }
  }

  async function handleDelete(questionId: string) {
    if (!confirm("Xóa câu hỏi này?")) return;
    await deleteQuestion(catalogItemId, questionId);
    if (editingId === questionId) resetForm();
    await load();
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
    await reorderQuestions(catalogItemId, ids);
    await load();
  }

  return (
    <AdminShell
      title={`Source — Câu hỏi ${catalogCode}`}
      description="Thêm câu hỏi trắc nghiệm với ảnh cho tài liệu"
    >
      <p className="mb-4 text-sm text-slate-400">
        <Link href="/admin/source/catalog" className="text-amber-400 hover:underline">
          ← Quay lại danh mục
        </Link>
      </p>

      <div className="grid gap-8 lg:grid-cols-2">
        <form onSubmit={handleSubmit} className="space-y-4 rounded-xl border border-slate-800 bg-slate-900/50 p-5">
          <h2 className="text-sm font-semibold text-white">
            {editingId ? "Sửa câu hỏi" : "Thêm câu hỏi"}
          </h2>

          <textarea
            className={inputClass}
            placeholder="Nội dung câu hỏi (có thể để trống nếu dùng ảnh)"
            value={questionText}
            onChange={(e) => setQuestionText(e.target.value)}
          />
          <SourceMediaUploader
            label="Ảnh câu hỏi"
            value={questionImageUrl}
            onChange={setQuestionImageUrl}
          />
          <textarea
            className={inputClass}
            placeholder="Giải thích (hiện sau khi kiểm tra)"
            value={explanation}
            onChange={(e) => setExplanation(e.target.value)}
          />

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-xs font-semibold uppercase text-slate-500">Đáp án</p>
              <button
                type="button"
                className="text-xs text-amber-400 hover:underline"
                onClick={addOption}
              >
                + Thêm đáp án
              </button>
            </div>
            {options.map((option, index) => (
              <div key={index} className="space-y-2 rounded-lg border border-slate-800 p-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs text-slate-500">Đáp án {index + 1}</span>
                  <label className="flex items-center gap-2 text-xs text-slate-300">
                    <input
                      type="checkbox"
                      checked={Boolean(option.isCorrect)}
                      onChange={(e) => updateOption(index, { isCorrect: e.target.checked })}
                    />
                    Đúng
                  </label>
                  {options.length > 2 && (
                    <button
                      type="button"
                      className="text-xs text-red-400"
                      onClick={() => removeOption(index)}
                    >
                      Xóa
                    </button>
                  )}
                </div>
                <input
                  className={inputClass}
                  placeholder="Nội dung đáp án"
                  value={option.optionText ?? ""}
                  onChange={(e) => updateOption(index, { optionText: e.target.value })}
                />
                <SourceMediaUploader
                  label="Ảnh đáp án"
                  value={option.optionImageUrl ?? null}
                  onChange={(url) => updateOption(index, { optionImageUrl: url ?? undefined })}
                />
              </div>
            ))}
          </div>

          <ErrorBanner message={error} />
          <div className="flex gap-2">
            <button type="submit" className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-semibold text-slate-950">
              {editingId ? "Cập nhật" : "Tạo câu hỏi"}
            </button>
            {editingId && (
              <button
                type="button"
                className="rounded-lg border border-slate-600 px-4 py-2 text-sm text-slate-300"
                onClick={resetForm}
              >
                Hủy
              </button>
            )}
          </div>
        </form>

        <div className="overflow-hidden rounded-xl border border-slate-800">
          {loading ? (
            <LoadingState className="p-4" />
          ) : questions.length === 0 ? (
            <p className="p-4 text-slate-400">Chưa có câu hỏi.</p>
          ) : (
            <ul className="divide-y divide-slate-800">
              {questions.map((q, index) => {
                const preview = q.questionText?.trim() || "(Ảnh)";
                const correctCount = q.options.filter((o) => o.isCorrect).length;
                const expanded = expandedIds.has(q.id);
                const questionImage = sourceMediaUrl(q.questionImageUrl);
                return (
                  <li key={q.id} className="text-sm text-slate-300">
                    <div className="flex items-start gap-2 p-4">
                      <button
                        type="button"
                        className="mt-0.5 shrink-0 text-slate-500 hover:text-slate-300"
                        aria-expanded={expanded}
                        aria-label={expanded ? "Thu gọn câu hỏi" : "Mở chi tiết câu hỏi"}
                        onClick={() => toggleExpanded(q.id)}
                      >
                        <span className="inline-block w-4 text-center text-xs">
                          {expanded ? "▼" : "▶"}
                        </span>
                      </button>
                      <button
                        type="button"
                        className="min-w-0 flex-1 text-left"
                        onClick={() => toggleExpanded(q.id)}
                      >
                        <p className="font-medium text-white">
                          {index + 1}. {preview}
                        </p>
                        <p className="mt-0.5 text-xs text-slate-500">
                          {q.options.length} đáp án · {correctCount} đúng
                          {q.multipleCorrect ? " · chọn nhiều" : ""}
                        </p>
                      </button>
                      <div className="flex shrink-0 flex-col gap-1 text-xs">
                        <button type="button" className="text-amber-400 hover:underline" onClick={() => startEdit(q)}>
                          Sửa
                        </button>
                        <button type="button" className="text-red-400 hover:underline" onClick={() => handleDelete(q.id)}>
                          Xóa
                        </button>
                        <button
                          type="button"
                          className="text-slate-400 hover:underline disabled:opacity-30"
                          disabled={index === 0}
                          onClick={() => moveQuestion(q.id, -1)}
                        >
                          ↑
                        </button>
                        <button
                          type="button"
                          className="text-slate-400 hover:underline disabled:opacity-30"
                          disabled={index === questions.length - 1}
                          onClick={() => moveQuestion(q.id, 1)}
                        >
                          ↓
                        </button>
                      </div>
                    </div>

                    {expanded && (
                      <div className="space-y-4 border-t border-slate-800 bg-slate-950/40 px-4 py-4 pl-10">
                        {q.questionText && (
                          <div>
                            <p className="text-xs font-semibold uppercase text-slate-500">Nội dung</p>
                            <p className="mt-1 whitespace-pre-line text-slate-200">{q.questionText}</p>
                          </div>
                        )}
                        {questionImage && (
                          <div>
                            <p className="text-xs font-semibold uppercase text-slate-500">Ảnh câu hỏi</p>
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img
                              src={questionImage}
                              alt=""
                              loading="lazy"
                              className="mt-2 max-h-48 rounded-lg border border-slate-700"
                            />
                          </div>
                        )}
                        {q.explanation && (
                          <div>
                            <p className="text-xs font-semibold uppercase text-slate-500">Giải thích</p>
                            <p className="mt-1 whitespace-pre-line text-slate-400">{q.explanation}</p>
                          </div>
                        )}
                        <div>
                          <p className="text-xs font-semibold uppercase text-slate-500">Đáp án</p>
                          <ul className="mt-2 space-y-2">
                            {q.options.map((option, optionIndex) => {
                              const optionImage = sourceMediaUrl(option.optionImageUrl);
                              return (
                                <li
                                  key={option.id}
                                  className={`rounded-lg border px-3 py-2 ${
                                    option.isCorrect
                                      ? "border-emerald-700/60 bg-emerald-950/30"
                                      : "border-slate-800 bg-slate-900/50"
                                  }`}
                                >
                                  <div className="flex items-center gap-2 text-xs">
                                    <span className="text-slate-500">#{optionIndex + 1}</span>
                                    {option.isCorrect && (
                                      <span className="rounded bg-emerald-800/60 px-1.5 py-0.5 font-medium text-emerald-300">
                                        Đúng
                                      </span>
                                    )}
                                  </div>
                                  {option.optionText && (
                                    <p className="mt-1 text-slate-200">{option.optionText}</p>
                                  )}
                                  {optionImage && (
                                    // eslint-disable-next-line @next/next/no-img-element
                                    <img
                                      src={optionImage}
                                      alt=""
                                      loading="lazy"
                                      className="mt-2 max-h-28 rounded border border-slate-700"
                                    />
                                  )}
                                </li>
                              );
                            })}
                          </ul>
                        </div>
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </AdminShell>
  );
}
