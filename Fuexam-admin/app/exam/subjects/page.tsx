"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { ImageUploader } from "@/components/admin/ImageUploader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ChevronDown, Folder, FileStack } from "lucide-react";
import { formatDateTime } from "@/lib/format-datetime";
import {
  type AdminSubject,
  type AdminSubjectLatestPaper,
  createExamSubject,
  deleteExamSubject,
  listExamSubjects,
  updateExamSubject,
} from "@/lib/api/exam";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const UNASSIGNED_TERM = "unassigned";

const EMPTY_FORM = {
  code: "",
  title: "",
  description: "",
  coverImageUrl: "",
  cardColor: "",
  categorySlug: "on-thi",
  curriculumTerm: UNASSIGNED_TERM,
  active: true,
  sortOrder: "0",
  fePreviewImageCount: "2",
};

type TermGroup = {
  term: number | null;
  label: string;
  subjects: AdminSubject[];
  feQuestionTotal: number;
  paperTotal: number;
  latestPaper: (AdminSubjectLatestPaper & { subjectCode: string }) | null;
};

/** Cycles through a fixed palette so each "Kỳ" badge gets a distinct, stable color. */
const TERM_BADGE_COLORS = [
  "bg-amber-500 text-amber-950",
  "bg-sky-500 text-sky-950",
  "bg-rose-500 text-rose-950",
  "bg-emerald-500 text-emerald-950",
  "bg-violet-500 text-violet-950",
  "bg-cyan-500 text-cyan-950",
  "bg-orange-500 text-orange-950",
  "bg-lime-500 text-lime-950",
  "bg-fuchsia-500 text-fuchsia-950",
  "bg-teal-500 text-teal-950",
];

function termBadgeColor(term: number): string {
  return TERM_BADGE_COLORS[term % TERM_BADGE_COLORS.length];
}

function groupSubjects(items: AdminSubject[]): TermGroup[] {
  const byTerm = new Map<number | null, AdminSubject[]>();
  for (const item of items) {
    const key = item.curriculumTerm ?? null;
    const bucket = byTerm.get(key);
    if (bucket) {
      bucket.push(item);
    } else {
      byTerm.set(key, [item]);
    }
  }

  const terms = [...byTerm.keys()].sort((a, b) => {
    if (a === null) return -1;
    if (b === null) return 1;
    return a - b;
  });

  return terms.map((term) => {
    const subjects = byTerm.get(term)!;
    const feQuestionTotal = subjects.reduce(
      (sum, s) => sum + s.feQuestionCount + s.pePaperCount,
      0,
    );
    const paperTotal = subjects.reduce(
      (sum, s) => sum + s.fePaperCount + s.pePaperCountAllStatuses,
      0,
    );
    const latestPaper = subjects.reduce<(AdminSubjectLatestPaper & { subjectCode: string }) | null>(
      (latest, s) => {
        if (!s.latestPaper) return latest;
        if (!latest || s.latestPaper.createdAt > latest.createdAt) {
          return { ...s.latestPaper, subjectCode: s.code };
        }
        return latest;
      },
      null,
    );
    return {
      term,
      label: term === null ? "Tổng hợp - Chưa rõ kỳ" : `Kỳ ${term}`,
      subjects,
      feQuestionTotal,
      paperTotal,
      latestPaper,
    };
  });
}

export default function AdminExamSubjectsPage() {
  const { user } = useAuth();
  const canWrite =
    can(user, "exam.subject.admin:create") || can(user, "exam.subject.admin:update");
  const canDelete = can(user, "exam.subject.admin:delete");

  const [items, setItems] = useState<AdminSubject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState("");
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listExamSubjects());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được môn thi.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function resetForm() {
    setForm(EMPTY_FORM);
    setEditingId(null);
  }

  function startEdit(item: AdminSubject) {
    setEditingId(item.id);
    setForm({
      code: item.code,
      title: item.title,
      description: item.description ?? "",
      coverImageUrl: item.coverImageUrl ?? "",
      cardColor: item.cardColor ?? "",
      categorySlug: item.categorySlug ?? "",
      curriculumTerm: item.curriculumTerm != null ? String(item.curriculumTerm) : UNASSIGNED_TERM,
      active: item.active,
      sortOrder: String(item.sortOrder),
      fePreviewImageCount: String(item.fePreviewImageCount),
    });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaving(true);
    const body = {
      code: form.code.trim(),
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      coverImageUrl: form.coverImageUrl || undefined,
      cardColor: form.cardColor.trim() || undefined,
      categorySlug: form.categorySlug.trim() || undefined,
      curriculumTerm:
        form.curriculumTerm === UNASSIGNED_TERM ? undefined : Number.parseInt(form.curriculumTerm, 10),
      active: form.active,
      sortOrder: Number.parseInt(form.sortOrder, 10) || 0,
      fePreviewImageCount: Number.parseInt(form.fePreviewImageCount, 10) || 2,
    };
    try {
      if (editingId) {
        const updated = await updateExamSubject(editingId, body);
        setItems((prev) => prev.map((it) => (it.id === updated.id ? updated : it)));
        toast.success("Đã cập nhật môn thi.");
      } else {
        const created = await createExamSubject(body);
        setItems((prev) => [...prev, created]);
        toast.success("Đã tạo môn thi.");
      }
      resetForm();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Lưu thất bại.";
      setError(message);
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  async function confirmDelete() {
    if (!deleteId) return;
    try {
      await deleteExamSubject(deleteId);
      setItems((prev) => prev.filter((it) => it.id !== deleteId));
      toast.success("Đã xóa môn thi.");
      if (editingId === deleteId) resetForm();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteId(null);
    }
  }

  const filteredItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (item) =>
        item.code.toLowerCase().includes(q) ||
        item.title.toLowerCase().includes(q) ||
        (item.categorySlug ?? "").toLowerCase().includes(q),
    );
  }, [items, search]);

  const editingItem = editingId ? items.find((i) => i.id === editingId) ?? null : null;
  const clearTermDisabled = editingItem != null && editingItem.curriculumTerm != null;

  return (
    <AdminShell
      title="Exam FE/PE — Môn thi"
      description="Quản lý môn thi và các đề thi (FE/PE) kèm tài liệu"
    >
      <div className="mb-4 max-w-md">
        <Input
          placeholder="Tìm theo mã, tên hoặc danh mục..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {search.trim() && (
          <p className="mt-1 text-xs text-muted-foreground">
            {filteredItems.length} / {items.length} môn thi
          </p>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {canWrite && (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                {editingId ? "Sửa môn thi" : "Thêm môn thi"}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-4">
                {error && (
                  <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                    {error}
                  </div>
                )}
                <div className="space-y-2">
                  <Label htmlFor="code">Mã môn</Label>
                  <Input
                    id="code"
                    placeholder="PRF192"
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    disabled={!!editingId}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="title">Tên môn thi</Label>
                  <Input
                    id="title"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="desc">Mô tả</Label>
                  <Textarea
                    id="desc"
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </div>
                <ImageUploader
                  label="Ảnh bìa"
                  purpose="exam_paper_image"
                  value={form.coverImageUrl || null}
                  onChange={(url) => setForm({ ...form, coverImageUrl: url ?? "" })}
                />
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="color">Màu thẻ (hex)</Label>
                    <Input
                      id="color"
                      placeholder="#6d28d9"
                      value={form.cardColor}
                      onChange={(e) => setForm({ ...form, cardColor: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="cat">Danh mục (slug)</Label>
                    <Input
                      id="cat"
                      value={form.categorySlug}
                      onChange={(e) => setForm({ ...form, categorySlug: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="curriculumTerm">Kỳ học</Label>
                    <Select
                      value={form.curriculumTerm}
                      onValueChange={(v) => setForm({ ...form, curriculumTerm: v })}
                    >
                      <SelectTrigger id="curriculumTerm">
                        <SelectValue placeholder="Chưa rõ kỳ" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={UNASSIGNED_TERM} disabled={clearTermDisabled}>
                          Chưa rõ kỳ{clearTermDisabled ? " (không thể bỏ gán)" : ""}
                        </SelectItem>
                        {Array.from({ length: 10 }, (_, i) => i).map((term) => (
                          <SelectItem key={term} value={String(term)}>
                            Kỳ {term}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="sort">Thứ tự</Label>
                    <Input
                      id="sort"
                      type="number"
                      min={0}
                      value={form.sortOrder}
                      onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="fePreviewImageCount">Số ảnh xem trước (free user)</Label>
                    <Input
                      id="fePreviewImageCount"
                      type="number"
                      min={1}
                      max={10}
                      value={form.fePreviewImageCount}
                      onChange={(e) => setForm({ ...form, fePreviewImageCount: e.target.value })}
                    />
                  </div>
                </div>
                <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
                  <Label htmlFor="active" className="cursor-pointer">
                    Đang hiển thị
                  </Label>
                  <Switch
                    id="active"
                    checked={form.active}
                    onCheckedChange={(v) => setForm({ ...form, active: v })}
                  />
                </div>
                <div className="flex gap-2">
                  <Button type="submit" disabled={saving}>
                    {saving ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo mới"}
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
        )}

        <div className={canWrite ? "" : "lg:col-span-2"}>
          <div className="rounded-xl border border-border">
            {loading ? (
              <p className="p-4 text-sm text-muted-foreground">Đang tải...</p>
            ) : (
              <Collapsible defaultOpen>
                <div className="flex items-center justify-between border-b border-border px-4 py-3">
                  <div>
                    <p className="text-sm font-semibold">Danh sách môn thi theo kỳ</p>
                    <p className="text-xs text-muted-foreground">
                      {items.length} môn · nhóm theo kỳ học
                    </p>
                  </div>
                  <CollapsibleTrigger asChild>
                    <Button type="button" variant="ghost" size="sm">
                      <ChevronDown className="h-4 w-4" />
                    </Button>
                  </CollapsibleTrigger>
                </div>
                <CollapsibleContent>
                  <div className="divide-y divide-border">
                    {groupSubjects(filteredItems).map((group) => (
                      <div key={group.term ?? "none"} className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-start">
                        <div className="flex shrink-0 items-center gap-2 sm:w-40">
                          {group.term === null ? (
                            <span className="flex h-8 w-8 items-center justify-center rounded-md bg-muted text-muted-foreground">
                              <FileStack className="h-4 w-4" />
                            </span>
                          ) : (
                            <span
                              className={`flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold ${termBadgeColor(group.term)}`}
                            >
                              {group.term}
                            </span>
                          )}
                          <span className="text-sm font-medium">{group.label}</span>
                        </div>

                        <div className="flex flex-1 flex-wrap gap-x-3 gap-y-1.5">
                          {group.subjects.map((item) => (
                            <span key={item.id} className="inline-flex items-center gap-1">
                              <Folder className="h-3.5 w-3.5 text-muted-foreground" />
                              <Link
                                href={`/exam/subjects/${item.id}/papers`}
                                className="font-mono text-sm text-sky-500 hover:underline"
                                title={item.title}
                              >
                                {item.code}
                              </Link>
                              {!item.active && (
                                <span className="rounded bg-muted px-1 text-[10px] font-medium text-muted-foreground">
                                  Ẩn
                                </span>
                              )}
                              {canWrite && (
                                <button
                                  type="button"
                                  className="text-xs text-muted-foreground hover:text-primary"
                                  onClick={() => startEdit(item)}
                                  aria-label={`Sửa ${item.code}`}
                                >
                                  ✎
                                </button>
                              )}
                              {canDelete && (
                                <button
                                  type="button"
                                  className="text-xs text-muted-foreground hover:text-destructive"
                                  onClick={() => setDeleteId(item.id)}
                                  aria-label={`Xóa ${item.code}`}
                                >
                                  ✕
                                </button>
                              )}
                            </span>
                          ))}
                          {group.subjects.length === 0 && (
                            <span className="text-xs text-muted-foreground">Không có môn phù hợp.</span>
                          )}
                        </div>

                        <div className="flex shrink-0 gap-6 text-right sm:w-28">
                          <div>
                            <p className="text-sm font-semibold">{group.feQuestionTotal}</p>
                            <p className="text-xs text-muted-foreground">Câu hỏi</p>
                          </div>
                          <div>
                            <p className="text-sm font-semibold">{group.paperTotal}</p>
                            <p className="text-xs text-muted-foreground">Đề thi</p>
                          </div>
                        </div>

                        <div className="shrink-0 sm:w-56 sm:text-right">
                          {group.latestPaper ? (
                            <div className="flex flex-col items-start gap-1 sm:items-end">
                              <Badge variant={group.latestPaper.paperType === "FE" ? "secondary" : "outline"}>
                                {group.latestPaper.paperType === "FE" ? "Đề Thi FE" : "Đề Thi PE"}
                              </Badge>
                              <p className="max-w-[200px] truncate text-xs" title={group.latestPaper.examCode}>
                                {group.latestPaper.subjectCode} · {group.latestPaper.examCode}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {formatDateTime(group.latestPaper.createdAt)}
                              </p>
                            </div>
                          ) : (
                            <p className="text-xs text-muted-foreground">Chưa có đề nào</p>
                          )}
                        </div>
                      </div>
                    ))}
                    {filteredItems.length === 0 && (
                      <p className="px-4 py-6 text-center text-sm text-muted-foreground">
                        Không có môn thi nào.
                      </p>
                    )}
                  </div>
                </CollapsibleContent>
              </Collapsible>
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={deleteId != null}
        title="Xóa môn thi?"
        description="Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDelete}
        onOpenChange={(o) => !o && setDeleteId(null)}
      />
    </AdminShell>
  );
}
