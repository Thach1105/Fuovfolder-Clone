"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronRight, FileArchive, X } from "lucide-react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { FileUploader, type UploadedFileInfo } from "@/components/admin/FileUploader";
import { MultiImageUploader } from "@/components/admin/MultiImageUploader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  type AdminPeItem,
  type AdminPeResource,
  type PeItemBody,
  addExamPeResource,
  createExamPeItem,
  deleteExamPeItem,
  deleteExamPeResource,
  examMediaUrl,
  getExamSubject,
  listExamPeItems,
  updateExamPeItem,
  uploadExamResource,
} from "@/lib/api/exam";
import { ApiError } from "@/lib/api/client";

function formatSize(bytes: number | null): string {
  if (bytes == null) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function AdminExamPeItemsPage() {
  const params = useParams<{ id: string }>();
  const subjectId = params.id;

  const [subjectCode, setSubjectCode] = useState("");
  const [items, setItems] = useState<AdminPeItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [examImageUrls, setExamImageUrls] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [deleteItemId, setDeleteItemId] = useState<string | null>(null);
  const [deleteResource, setDeleteResource] = useState<{ itemId: string; resourceId: string } | null>(
    null,
  );

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [subject, itemList] = await Promise.all([
        getExamSubject(subjectId),
        listExamPeItems(subjectId),
      ]);
      setSubjectCode(subject?.code ?? subjectId);
      setItems(itemList);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được đề PE.");
    } finally {
      setLoading(false);
    }
  }, [subjectId]);

  useEffect(() => {
    load();
  }, [load]);

  function resetForm() {
    setEditingId(null);
    setTitle("");
    setDescription("");
    setExamImageUrls([]);
  }

  function startEdit(item: AdminPeItem) {
    setEditingId(item.id);
    setTitle(item.title);
    setDescription(item.description ?? "");
    setExamImageUrls(item.examImageUrls ?? []);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!title.trim()) {
      const msg = "Cần nhập tiêu đề đề PE.";
      setError(msg);
      toast.error(msg);
      return;
    }
    const body: PeItemBody = {
      title: title.trim(),
      description: description.trim() || undefined,
      examImageUrls: examImageUrls.length > 0 ? examImageUrls : undefined,
    };
    setSaving(true);
    try {
      if (editingId) {
        await updateExamPeItem(subjectId, editingId, body);
        toast.success("Đã cập nhật đề PE.");
      } else {
        await createExamPeItem(subjectId, body);
        toast.success("Đã tạo đề PE.");
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

  async function confirmDeleteItem() {
    if (!deleteItemId) return;
    try {
      await deleteExamPeItem(subjectId, deleteItemId);
      toast.success("Đã xóa đề PE.");
      if (editingId === deleteItemId) resetForm();
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteItemId(null);
    }
  }

  async function confirmDeleteResource() {
    if (!deleteResource) return;
    try {
      await deleteExamPeResource(subjectId, deleteResource.itemId, deleteResource.resourceId);
      toast.success("Đã xóa tệp.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteResource(null);
    }
  }

  function toggleExpanded(itemId: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(itemId)) next.delete(itemId);
      else next.add(itemId);
      return next;
    });
  }

  return (
    <AdminShell
      title={`Đề PE — ${subjectCode}`}
      description="Quản lý đề thực hành PE và tệp đính kèm (.zip) cho môn thi"
    >
      <Link
        href="/exam/subjects"
        className="mb-4 inline-block text-sm text-primary hover:underline"
      >
        ← Quay lại môn thi
      </Link>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              {editingId ? "Sửa đề PE" : "Thêm đề PE"}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              {error && (
                <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {error}
                </div>
              )}
              <Input
                placeholder="Tiêu đề đề PE"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
              />
              <Textarea
                placeholder="Mô tả (tùy chọn)"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
              <MultiImageUploader
                label="Anh de PE"
                purpose="exam_pe_image"
                value={examImageUrls}
                onChange={setExamImageUrls}
              />
              <div className="flex gap-2">
                <Button type="submit" disabled={saving}>
                  {saving ? "Đang lưu..." : editingId ? "Cập nhật" : "Tạo đề PE"}
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
          ) : items.length === 0 ? (
            <p className="p-4 text-sm text-muted-foreground">Chưa có đề PE.</p>
          ) : (
            <ul className="divide-y divide-border">
              {items.map((item, index) => {
                const expanded = expandedIds.has(item.id);
                return (
                  <li key={item.id} className="text-sm">
                    <div className="flex items-start gap-2 p-4">
                      <button
                        type="button"
                        className="mt-0.5 shrink-0 text-muted-foreground hover:text-foreground"
                        onClick={() => toggleExpanded(item.id)}
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
                        onClick={() => toggleExpanded(item.id)}
                      >
                        <p className="font-medium text-foreground">
                          {index + 1}. {item.title}
                        </p>
                        <p className="mt-0.5 text-xs text-muted-foreground">
                          {item.resources.length} tệp
                          {item.examImageUrls && item.examImageUrls.length > 0
                            ? ` · ${item.examImageUrls.length} ảnh`
                            : ""}
                        </p>
                      </button>
                      <div className="flex shrink-0 flex-col items-end gap-1 text-xs">
                        <button
                          type="button"
                          className="text-primary hover:underline"
                          onClick={() => startEdit(item)}
                        >
                          Sửa
                        </button>
                        <button
                          type="button"
                          className="text-destructive hover:underline"
                          onClick={() => setDeleteItemId(item.id)}
                        >
                          Xóa
                        </button>
                      </div>
                    </div>

                    {expanded && (
                      <div className="space-y-4 border-t border-border bg-muted/20 px-4 py-4 pl-10">
                        {item.description && (
                          <p className="whitespace-pre-line text-muted-foreground">
                            {item.description}
                          </p>
                        )}
                        {item.examImageUrls && item.examImageUrls.length > 0 && (
                          <div className="grid grid-cols-2 gap-2">
                            {item.examImageUrls.map((url, idx) => {
                              const imgUrl = examMediaUrl(url);
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
                        <PeResourceSection
                          item={item}
                          onDeleteResource={(resourceId) =>
                            setDeleteResource({ itemId: item.id, resourceId })
                          }
                          onAdded={load}
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

      <ConfirmDialog
        open={deleteItemId != null}
        title="Xóa đề PE?"
        description="Hành động này không thể hoàn tác và sẽ xóa các tệp đính kèm."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDeleteItem}
        onOpenChange={(o) => !o && setDeleteItemId(null)}
      />

      <ConfirmDialog
        open={deleteResource != null}
        title="Xóa tệp?"
        description="Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDeleteResource}
        onOpenChange={(o) => !o && setDeleteResource(null)}
      />
    </AdminShell>
  );
}

function PeResourceSection({
  item,
  onDeleteResource,
  onAdded,
}: {
  item: AdminPeItem;
  onDeleteResource: (resourceId: string) => void;
  onAdded: () => Promise<void> | void;
}) {
  const params = useParams<{ id: string }>();
  const subjectId = params.id;

  const [folderLabel, setFolderLabel] = useState("");
  const [pending, setPending] = useState<UploadedFileInfo | null>(null);
  const [saving, setSaving] = useState(false);

  const grouped = useMemo(() => {
    const map = new Map<string, AdminPeResource[]>();
    for (const r of item.resources) {
      const key = r.folderLabel ?? "";
      const list = map.get(key) ?? [];
      list.push(r);
      map.set(key, list);
    }
    return Array.from(map.entries());
  }, [item.resources]);

  async function attach() {
    if (!pending) return;
    setSaving(true);
    try {
      await addExamPeResource(subjectId, item.id, {
        objectKey: pending.objectKey,
        originalFilename: pending.originalFilename,
        mimeType: pending.mimeType,
        sizeBytes: pending.sizeBytes,
        folderLabel: folderLabel.trim() || undefined,
      });
      toast.success("Đã thêm tệp.");
      setPending(null);
      setFolderLabel("");
      await onAdded();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Thêm tệp thất bại.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-xs font-semibold uppercase text-muted-foreground">Tệp đính kèm (.zip)</p>

      {grouped.length > 0 && (
        <div className="space-y-3">
          {grouped.map(([label, resources]) => (
            <div key={label || "__default"} className="space-y-1">
              {label && (
                <p className="text-xs font-medium text-foreground">{label}</p>
              )}
              <ul className="space-y-1">
                {resources.map((r) => (
                  <li
                    key={r.id}
                    className="flex items-center gap-2 rounded-lg border border-border bg-card px-3 py-2"
                  >
                    <FileArchive className="h-4 w-4 shrink-0 text-muted-foreground" />
                    <span className="min-w-0 flex-1 truncate">{r.originalFilename}</span>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {formatSize(r.sizeBytes)}
                    </span>
                    <button
                      type="button"
                      className="shrink-0 text-destructive hover:text-destructive/80"
                      onClick={() => onDeleteResource(r.id)}
                      aria-label="Xóa tệp"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      <div className="space-y-2 rounded-lg border border-dashed border-border p-3">
        <Input
          placeholder="Nhãn thư mục (tùy chọn, ví dụ: Đề 1)"
          value={folderLabel}
          onChange={(e) => setFolderLabel(e.target.value)}
        />
        <FileUploader
          label="Tệp .zip"
          accept=".zip,application/zip"
          buttonLabel="Tải tệp .zip lên"
          onUpload={(file) => uploadExamResource(file)}
          onUploaded={setPending}
        />
        <Button type="button" size="sm" disabled={!pending || saving} onClick={attach}>
          {saving ? "Đang thêm..." : "Thêm tệp vào đề"}
        </Button>
      </div>
    </div>
  );
}
