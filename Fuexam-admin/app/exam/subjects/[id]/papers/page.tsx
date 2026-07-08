"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronRight, FileArchive } from "lucide-react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { FileUploader, type UploadedFileInfo } from "@/components/admin/FileUploader";
import { MultiImageUploader } from "@/components/admin/MultiImageUploader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  type AdminPaper,
  type AdminPaperResource,
  type PaperBody,
  type PaperType,
  addExamPaperResource,
  createExamPaper,
  deleteExamPaper,
  deleteExamPaperResource,
  examMediaUrl,
  getExamSubject,
  listExamPapers,
  updateExamPaper,
  uploadExamImage,
  uploadExamResource,
} from "@/lib/api/exam";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const EMPTY_FORM = {
  type: "FE" as PaperType,
  term: "",
  retakeLabel: "",
  title: "",
  description: "",
  sortOrder: "0",
};

function formatSize(bytes: number | null): string {
  if (bytes == null) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function paperLabel(p: AdminPaper): string {
  return [p.type, p.term, p.retakeLabel].filter(Boolean).join(" ");
}

export default function AdminExamPapersPage() {
  const params = useParams<{ id: string }>();
  const subjectId = params.id;

  const { user } = useAuth();
  const canWrite =
    can(user, "exam.paper.admin:create") || can(user, "exam.paper.admin:update");
  const canDelete = can(user, "exam.paper.admin:delete");

  const [subjectCode, setSubjectCode] = useState("");
  const [papers, setPapers] = useState<AdminPaper[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [form, setForm] = useState(EMPTY_FORM);
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [deletePaperId, setDeletePaperId] = useState<string | null>(null);
  const [deleteResource, setDeleteResource] = useState<{
    paperId: string;
    resourceId: string;
  } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [subject, paperList] = await Promise.all([
        getExamSubject(subjectId).catch(() => null),
        listExamPapers(subjectId),
      ]);
      setSubjectCode(subject?.code ?? subjectId);
      setPapers(paperList);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được đề thi.");
    } finally {
      setLoading(false);
    }
  }, [subjectId]);

  useEffect(() => {
    load();
  }, [load]);

  const sortedPapers = useMemo(() => {
    return [...papers].sort((a, b) => {
      if (a.type !== b.type) return a.type === "FE" ? -1 : 1;
      if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
      return (a.term ?? "").localeCompare(b.term ?? "");
    });
  }, [papers]);

  function resetForm() {
    setForm(EMPTY_FORM);
    setImageUrls([]);
    setEditingId(null);
  }

  function startEdit(paper: AdminPaper) {
    setEditingId(paper.id);
    setForm({
      type: paper.type,
      term: paper.term ?? "",
      retakeLabel: paper.retakeLabel ?? "",
      title: paper.title,
      description: paper.description ?? "",
      sortOrder: String(paper.sortOrder),
    });
    setImageUrls(paper.imageUrls ?? []);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!form.title.trim()) {
      const msg = "Tiêu đề đề thi là bắt buộc.";
      setError(msg);
      toast.error(msg);
      return;
    }
    setSaving(true);
    const body: PaperBody = {
      type: form.type,
      term: form.term.trim() || undefined,
      retakeLabel: form.retakeLabel.trim() || undefined,
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      imageUrls: imageUrls.length > 0 ? imageUrls : undefined,
      sortOrder: Number.parseInt(form.sortOrder, 10) || 0,
    };
    try {
      if (editingId) {
        const updated = await updateExamPaper(subjectId, editingId, body);
        setPapers((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
        toast.success("Đã cập nhật đề thi.");
      } else {
        const created = await createExamPaper(subjectId, body);
        setPapers((prev) => [...prev, created]);
        toast.success("Đã tạo đề thi.");
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

  async function confirmDeletePaper() {
    if (!deletePaperId) return;
    try {
      await deleteExamPaper(subjectId, deletePaperId);
      setPapers((prev) => prev.filter((p) => p.id !== deletePaperId));
      if (editingId === deletePaperId) resetForm();
      toast.success("Đã xóa đề thi.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeletePaperId(null);
    }
  }

  async function confirmDeleteResource() {
    if (!deleteResource) return;
    const { paperId, resourceId } = deleteResource;
    try {
      await deleteExamPaperResource(subjectId, paperId, resourceId);
      setPapers((prev) =>
        prev.map((p) =>
          p.id === paperId
            ? { ...p, resources: p.resources.filter((r) => r.id !== resourceId) }
            : p,
        ),
      );
      toast.success("Đã xóa tài liệu.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteResource(null);
    }
  }

  function toggleExpanded(paperId: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(paperId)) next.delete(paperId);
      else next.add(paperId);
      return next;
    });
  }

  return (
    <AdminShell
      title={`Đề thi — ${subjectCode}`}
      description="Quản lý các đề thi (FE/PE) kèm ảnh và tài liệu tải về"
    >
      <Link
        href="/exam/subjects"
        className="mb-4 inline-block text-sm text-primary hover:underline"
      >
        ← Quay lại danh sách môn
      </Link>

      <div className="grid gap-6 lg:grid-cols-2">
        {canWrite && (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                {editingId ? "Sửa đề thi" : "Thêm đề thi"}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-4">
                {error && (
                  <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                    {error}
                  </div>
                )}
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Loại</Label>
                    <Select
                      value={form.type}
                      onValueChange={(v) => setForm({ ...form, type: v as PaperType })}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="FE">FE</SelectItem>
                        <SelectItem value="PE">PE</SelectItem>
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
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="term">Kỳ (term)</Label>
                    <Input
                      id="term"
                      placeholder="SU25"
                      value={form.term}
                      onChange={(e) => setForm({ ...form, term: e.target.value })}
                    />
                    <p className="text-xs text-muted-foreground">
                      Đặt theo format Kỳ, vd SU25, FA24
                    </p>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="retake">Retake</Label>
                    <Input
                      id="retake"
                      placeholder="RE (nếu là đề retake)"
                      value={form.retakeLabel}
                      onChange={(e) => setForm({ ...form, retakeLabel: e.target.value })}
                    />
                  </div>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="title">Tiêu đề</Label>
                  <Input
                    id="title"
                    placeholder="PRF192 SU25 FE"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    Gợi ý format: &quot;PRF192 SU25 FE&quot;
                  </p>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="desc">Mô tả</Label>
                  <Textarea
                    id="desc"
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </div>
                <MultiImageUploader
                  label="Ảnh đề thi"
                  purpose="exam_paper_image"
                  value={imageUrls}
                  onChange={setImageUrls}
                />
                <div className="flex gap-2">
                  <Button type="submit" disabled={saving}>
                    {saving ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo đề thi"}
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
            ) : sortedPapers.length === 0 ? (
              <p className="p-4 text-sm text-muted-foreground">Chưa có đề thi nào.</p>
            ) : (
              <ul className="divide-y divide-border">
                {sortedPapers.map((paper) => {
                  const expanded = expandedIds.has(paper.id);
                  const images = paper.imageUrls ?? [];
                  return (
                    <li key={paper.id} className="text-sm">
                      <div className="flex items-start gap-2 p-4">
                        <button
                          type="button"
                          className="mt-0.5 shrink-0 text-muted-foreground hover:text-foreground"
                          onClick={() => toggleExpanded(paper.id)}
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
                          onClick={() => toggleExpanded(paper.id)}
                        >
                          <p className="font-medium text-foreground">
                            <span
                              className={
                                paper.type === "FE"
                                  ? "mr-2 rounded bg-emerald-500/20 px-1.5 py-0.5 text-xs font-semibold text-emerald-500"
                                  : "mr-2 rounded bg-sky-500/20 px-1.5 py-0.5 text-xs font-semibold text-sky-500"
                              }
                            >
                              {paper.type}
                            </span>
                            {paper.title}
                          </p>
                          <p className="mt-0.5 text-xs text-muted-foreground">
                            {paperLabel(paper)} · {images.length} ảnh ·{" "}
                            {paper.resources.length} tài liệu
                          </p>
                        </button>
                        <div className="flex shrink-0 flex-col items-end gap-1 text-xs">
                          {canWrite && (
                            <button
                              type="button"
                              className="text-primary hover:underline"
                              onClick={() => startEdit(paper)}
                            >
                              Sửa
                            </button>
                          )}
                          {canDelete && (
                            <button
                              type="button"
                              className="text-destructive hover:underline"
                              onClick={() => setDeletePaperId(paper.id)}
                            >
                              Xóa
                            </button>
                          )}
                        </div>
                      </div>

                      {expanded && (
                        <div className="space-y-4 border-t border-border bg-muted/20 px-4 py-4 pl-10">
                          {paper.description && (
                            <p className="whitespace-pre-line text-muted-foreground">
                              {paper.description}
                            </p>
                          )}
                          {images.length > 0 && (
                            <div className="grid grid-cols-2 gap-2">
                              {images.map((url, idx) => (
                                // eslint-disable-next-line @next/next/no-img-element
                                <img
                                  key={`${url}-${idx}`}
                                  src={examMediaUrl(url) ?? undefined}
                                  alt={`Ảnh ${idx + 1}`}
                                  loading="lazy"
                                  className="max-h-32 rounded-lg border border-border object-cover"
                                />
                              ))}
                            </div>
                          )}

                          <PaperResources
                            subjectId={subjectId}
                            paper={paper}
                            canWrite={canWrite}
                            canDelete={canDelete}
                            onResourceAdded={(updated) =>
                              setPapers((prev) =>
                                prev.map((p) => (p.id === updated.id ? updated : p)),
                              )
                            }
                            onRequestDeleteResource={(resourceId) =>
                              setDeleteResource({ paperId: paper.id, resourceId })
                            }
                          />
                        </div>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog
        open={deletePaperId != null}
        title="Xóa đề thi?"
        description="Toàn bộ ảnh và tài liệu của đề sẽ bị xóa. Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDeletePaper}
        onOpenChange={(o) => !o && setDeletePaperId(null)}
      />

      <ConfirmDialog
        open={deleteResource != null}
        title="Xóa tài liệu?"
        description="Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDeleteResource}
        onOpenChange={(o) => !o && setDeleteResource(null)}
      />
    </AdminShell>
  );
}

// ---------------------------------------------------------------------------
// Resource sub-section per paper
// ---------------------------------------------------------------------------

type PaperResourcesProps = {
  subjectId: string;
  paper: AdminPaper;
  canWrite: boolean;
  canDelete: boolean;
  onResourceAdded: (updated: AdminPaper) => void;
  onRequestDeleteResource: (resourceId: string) => void;
};

function PaperResources({
  subjectId,
  paper,
  canWrite,
  canDelete,
  onResourceAdded,
  onRequestDeleteResource,
}: PaperResourcesProps) {
  const [folderLabel, setFolderLabel] = useState("");
  const [adding, setAdding] = useState(false);

  const grouped = useMemo(() => {
    const map = new Map<string, AdminPaperResource[]>();
    for (const r of paper.resources) {
      const key = r.folderLabel ?? "";
      const list = map.get(key) ?? [];
      list.push(r);
      map.set(key, list);
    }
    return Array.from(map.entries());
  }, [paper.resources]);

  async function handleUploaded(info: UploadedFileInfo) {
    setAdding(true);
    try {
      const updated = await addExamPaperResource(subjectId, paper.id, {
        objectKey: info.objectKey,
        originalFilename: info.originalFilename,
        mimeType: info.mimeType,
        sizeBytes: info.sizeBytes,
        folderLabel: folderLabel.trim() || undefined,
      });
      onResourceAdded(updated);
      toast.success("Đã thêm tài liệu.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Thêm tài liệu thất bại.");
    } finally {
      setAdding(false);
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-xs font-semibold uppercase text-muted-foreground">Tài liệu</p>

      {grouped.length > 0 ? (
        <div className="space-y-3">
          {grouped.map(([label, resources]) => (
            <div key={label || "__none__"} className="space-y-1">
              {label && (
                <p className="text-xs font-medium text-foreground">📁 {label}</p>
              )}
              <ul className="space-y-1">
                {resources.map((r) => (
                  <li
                    key={r.id}
                    className="flex items-center gap-2 rounded-lg border border-border bg-card px-3 py-2"
                  >
                    <FileArchive className="h-4 w-4 shrink-0 text-muted-foreground" />
                    <a
                      href={examMediaUrl(r.objectKey) ?? undefined}
                      target="_blank"
                      rel="noreferrer"
                      className="min-w-0 flex-1 truncate hover:underline"
                    >
                      {r.originalFilename}
                    </a>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {formatSize(r.sizeBytes)}
                    </span>
                    {canDelete && (
                      <button
                        type="button"
                        className="shrink-0 text-xs text-destructive hover:underline"
                        onClick={() => onRequestDeleteResource(r.id)}
                      >
                        Xóa
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">Chưa có tài liệu.</p>
      )}

      {canWrite && (
        <div className="space-y-2 rounded-lg border border-dashed border-border p-3">
          <div className="space-y-1">
            <Label htmlFor={`folder-${paper.id}`} className="text-xs">
              Thư mục (tùy chọn)
            </Label>
            <Input
              id={`folder-${paper.id}`}
              placeholder="vd: Đề, Đáp án, Source code"
              value={folderLabel}
              onChange={(e) => setFolderLabel(e.target.value)}
            />
          </div>
          <FileUploader
            label="Tải tài liệu (mọi định dạng, tối đa 50MB)"
            accept="*"
            buttonLabel={adding ? "Đang thêm..." : "Tải tệp lên"}
            onUpload={uploadExamResource}
            onUploaded={handleUploaded}
          />
        </div>
      )}
    </div>
  );
}
