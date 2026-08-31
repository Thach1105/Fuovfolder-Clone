"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { FileArchive, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { FileUploader, type UploadedFileInfo } from "@/components/admin/FileUploader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import {
  type AdminFeQuestion,
  type AdminPeItem,
  addExamPeResource,
  createExamFeQuestion,
  createExamPeItem,
  deleteExamFeQuestion,
  deleteExamPeItem,
  deleteExamPeResource,
  examMediaUrl,
  getExamSubject,
  listExamFeQuestions,
  listExamPeItems,
  reorderExamFeQuestions,
  updateExamFeQuestion,
  updateExamPeItem,
  uploadExamImage,
  uploadExamResource,
} from "@/lib/api/exam";

/**
 * Content of one exam subject, as the backend actually models it: a flat list of FE question
 * posts and a list of PE items with downloadable resources.
 *
 * <p>Paper-level metadata (mã đề, kỳ, loại) lives in the paper bank, which the webhook ingest
 * fills and a separate screen reviews — there is no admin endpoint for creating a paper by hand,
 * so this screen deliberately does not pretend there is.
 */

/** An FE image and the blur sidecar the server generated for it; the two must stay aligned. */
type FeImage = { objectKey: string; blurObjectKey: string | null };

function formatSize(bytes: number | null): string {
  if (bytes == null || bytes <= 0) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function ImageStrip({
  images,
  onRemove,
}: {
  images: string[];
  onRemove?: (index: number) => void;
}) {
  if (images.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {images.map((key, index) => {
        const src = examMediaUrl(key);
        return (
          <div key={`${key}-${index}`} className="relative">
            {src && (
              /* eslint-disable-next-line @next/next/no-img-element */
              <img
                src={src}
                alt=""
                loading="lazy"
                className="h-24 w-auto rounded border object-cover"
              />
            )}
            {onRemove && (
              <button
                type="button"
                onClick={() => onRemove(index)}
                className="absolute right-1 top-1 rounded bg-background/90 p-1 text-destructive shadow"
                aria-label="Xóa ảnh"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

export default function AdminExamSubjectContentPage() {
  const params = useParams<{ id: string }>();
  const subjectId = params.id;

  const { user } = useAuth();
  const canWriteFe =
    can(user, "exam.question.admin:create") || can(user, "exam.question.admin:update");
  const canDeleteFe = can(user, "exam.question.admin:delete");
  const canWritePe = can(user, "exam.pe.admin:create") || can(user, "exam.pe.admin:update");
  const canDeletePe = can(user, "exam.pe.admin:delete");

  const [subjectCode, setSubjectCode] = useState("");
  const [questions, setQuestions] = useState<AdminFeQuestion[]>([]);
  const [peItems, setPeItems] = useState<AdminPeItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // FE form
  const [feText, setFeText] = useState("");
  const [feImages, setFeImages] = useState<FeImage[]>([]);
  const [feEditingId, setFeEditingId] = useState<string | null>(null);
  const [feSaving, setFeSaving] = useState(false);
  const [feUploading, setFeUploading] = useState(false);

  // PE form
  const [peTitle, setPeTitle] = useState("");
  const [peDescription, setPeDescription] = useState("");
  const [peImages, setPeImages] = useState<string[]>([]);
  const [peEditingId, setPeEditingId] = useState<string | null>(null);
  const [peSaving, setPeSaving] = useState(false);
  const [peUploading, setPeUploading] = useState(false);

  const [deleteQuestionId, setDeleteQuestionId] = useState<string | null>(null);
  const [deleteItemId, setDeleteItemId] = useState<string | null>(null);
  const [deleteResource, setDeleteResource] = useState<{ itemId: string; resourceId: string } | null>(
    null,
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [subject, feList, peList] = await Promise.all([
        getExamSubject(subjectId).catch(() => null),
        listExamFeQuestions(subjectId),
        listExamPeItems(subjectId),
      ]);
      setSubjectCode(subject?.code ?? subjectId);
      setQuestions(feList);
      setPeItems(peList);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được nội dung môn thi.");
    } finally {
      setLoading(false);
    }
  }, [subjectId]);

  useEffect(() => {
    load();
  }, [load]);

  // --- FE questions ---------------------------------------------------------

  function resetFeForm() {
    setFeText("");
    setFeImages([]);
    setFeEditingId(null);
  }

  function startEditQuestion(question: AdminFeQuestion) {
    setFeEditingId(question.id);
    setFeText(question.questionText ?? "");
    const keys = question.questionImageUrls ?? [];
    const blurs = question.questionBlurUrls ?? [];
    setFeImages(keys.map((objectKey, i) => ({ objectKey, blurObjectKey: blurs[i] ?? null })));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function onFeImagePicked(file: File) {
    setFeUploading(true);
    try {
      const uploaded = await uploadExamImage(file, "exam_fe_image");
      setFeImages((prev) => [
        ...prev,
        { objectKey: uploaded.objectKey, blurObjectKey: uploaded.blurObjectKey },
      ]);
      if (!uploaded.blurObjectKey) {
        toast.warning("Ảnh đã tải lên nhưng server không tạo được bản blur.");
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Tải ảnh thất bại.");
    } finally {
      setFeUploading(false);
    }
  }

  async function submitQuestion(e: React.FormEvent) {
    e.preventDefault();
    if (!feText.trim() && feImages.length === 0) {
      toast.error("Câu hỏi phải có nội dung chữ hoặc ít nhất một ảnh.");
      return;
    }
    setFeSaving(true);
    const body = {
      questionText: feText.trim() || undefined,
      questionImageUrls: feImages.map((image) => image.objectKey),
      // Parallel array: index i of blur belongs to index i of image, which is how the public
      // read path pairs them.
      questionBlurUrls: feImages.map((image) => image.blurObjectKey ?? ""),
      sortOrder: feEditingId
        ? questions.find((q) => q.id === feEditingId)?.sortOrder
        : questions.length,
    };
    try {
      if (feEditingId) {
        await updateExamFeQuestion(subjectId, feEditingId, body);
        toast.success("Đã cập nhật câu hỏi.");
      } else {
        await createExamFeQuestion(subjectId, body);
        toast.success("Đã thêm câu hỏi.");
      }
      resetFeForm();
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Lưu câu hỏi thất bại.");
    } finally {
      setFeSaving(false);
    }
  }

  async function moveQuestion(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= questions.length) return;
    const reordered = [...questions];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    setQuestions(reordered);
    try {
      await reorderExamFeQuestions(
        subjectId,
        reordered.map((q) => q.id),
      );
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Đổi thứ tự thất bại.");
      await load();
    }
  }

  async function confirmDeleteQuestion() {
    if (!deleteQuestionId) return;
    try {
      await deleteExamFeQuestion(subjectId, deleteQuestionId);
      if (feEditingId === deleteQuestionId) resetFeForm();
      toast.success("Đã xóa câu hỏi.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteQuestionId(null);
    }
  }

  // --- PE items -------------------------------------------------------------

  function resetPeForm() {
    setPeTitle("");
    setPeDescription("");
    setPeImages([]);
    setPeEditingId(null);
  }

  function startEditItem(item: AdminPeItem) {
    setPeEditingId(item.id);
    setPeTitle(item.title);
    setPeDescription(item.description ?? "");
    setPeImages(item.examImageUrls ?? []);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function onPeImagePicked(file: File) {
    setPeUploading(true);
    try {
      const uploaded = await uploadExamImage(file, "exam_pe_image");
      setPeImages((prev) => [...prev, uploaded.objectKey]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Tải ảnh thất bại.");
    } finally {
      setPeUploading(false);
    }
  }

  async function submitItem(e: React.FormEvent) {
    e.preventDefault();
    if (!peTitle.trim()) {
      toast.error("Tiêu đề đề PE là bắt buộc.");
      return;
    }
    setPeSaving(true);
    const body = {
      title: peTitle.trim(),
      description: peDescription.trim() || undefined,
      examImageUrls: peImages,
      sortOrder: peEditingId
        ? peItems.find((item) => item.id === peEditingId)?.sortOrder
        : peItems.length,
    };
    try {
      if (peEditingId) {
        await updateExamPeItem(subjectId, peEditingId, body);
        toast.success("Đã cập nhật đề PE.");
      } else {
        await createExamPeItem(subjectId, body);
        toast.success("Đã thêm đề PE.");
      }
      resetPeForm();
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Lưu đề PE thất bại.");
    } finally {
      setPeSaving(false);
    }
  }

  async function attachResource(itemId: string, info: UploadedFileInfo) {
    try {
      await addExamPeResource(subjectId, itemId, {
        objectKey: info.objectKey,
        originalFilename: info.originalFilename,
        mimeType: info.mimeType,
        sizeBytes: info.sizeBytes,
      });
      toast.success("Đã thêm file tài nguyên.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Thêm file thất bại.");
    }
  }

  async function confirmDeleteItem() {
    if (!deleteItemId) return;
    try {
      await deleteExamPeItem(subjectId, deleteItemId);
      if (peEditingId === deleteItemId) resetPeForm();
      toast.success("Đã xóa đề PE.");
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
      toast.success("Đã xóa file.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa file thất bại.");
    } finally {
      setDeleteResource(null);
    }
  }

  return (
    <AdminShell
      title={`Exam — Nội dung ${subjectCode || ""}`}
      description="Quản lý câu hỏi FE và đề PE của môn thi"
    >
      <div className="mb-4 flex items-center gap-3 text-sm">
        <Link href="/exam/subjects" className="text-muted-foreground hover:underline">
          ← Danh sách môn thi
        </Link>
      </div>

      {error && <p className="mb-3 text-sm text-destructive">{error}</p>}
      {loading && <p className="mb-3 text-sm text-muted-foreground">Đang tải…</p>}

      {/* ---------------- FE questions ---------------- */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle>Câu hỏi FE ({questions.length})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {canWriteFe && (
            <form onSubmit={submitQuestion} className="space-y-3 rounded-lg border p-4">
              <p className="text-sm font-medium">
                {feEditingId ? "Sửa câu hỏi" : "Thêm câu hỏi mới"}
              </p>
              <div>
                <Label htmlFor="fe-text">Nội dung chữ (tùy chọn)</Label>
                <Textarea
                  id="fe-text"
                  value={feText}
                  onChange={(e) => setFeText(e.target.value)}
                  rows={2}
                  placeholder="Để trống nếu toàn bộ câu hỏi nằm trong ảnh"
                />
              </div>
              <div className="space-y-2">
                <Label>Ảnh câu hỏi</Label>
                <ImageStrip
                  images={feImages.map((image) => image.objectKey)}
                  onRemove={(index) =>
                    setFeImages((prev) => prev.filter((_, i) => i !== index))
                  }
                />
                <input
                  type="file"
                  accept="image/*"
                  disabled={feUploading}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) onFeImagePicked(file);
                    e.target.value = "";
                  }}
                  className="text-sm"
                />
                <p className="text-xs text-muted-foreground">
                  Mỗi ảnh được tải qua endpoint exam nên server tự sinh bản blur dùng cho phần
                  xem thử của người chưa có membership.
                </p>
              </div>
              <div className="flex gap-2">
                <Button type="submit" disabled={feSaving || feUploading}>
                  {feEditingId ? "Cập nhật" : "Thêm câu hỏi"}
                </Button>
                {feEditingId && (
                  <Button type="button" variant="outline" onClick={resetFeForm}>
                    Hủy
                  </Button>
                )}
              </div>
            </form>
          )}

          <div className="space-y-3">
            {questions.length === 0 ? (
              <p className="text-sm text-muted-foreground">Môn này chưa có câu hỏi FE.</p>
            ) : (
              questions.map((question, index) => (
                <div key={question.id} className="rounded-lg border p-3">
                  <div className="mb-2 flex items-start justify-between gap-3">
                    <div>
                      <p className="text-xs font-medium text-muted-foreground">
                        Câu {index + 1}
                        {question.questionText ? ` — ${question.questionText}` : ""}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {(question.questionImageUrls ?? []).length} ảnh
                      </p>
                    </div>
                    <div className="flex shrink-0 gap-1">
                      {canWriteFe && (
                        <>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => moveQuestion(index, -1)}
                            disabled={index === 0}
                          >
                            ↑
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => moveQuestion(index, 1)}
                            disabled={index === questions.length - 1}
                          >
                            ↓
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => startEditQuestion(question)}
                          >
                            Sửa
                          </Button>
                        </>
                      )}
                      {canDeleteFe && (
                        <Button
                          size="sm"
                          variant="destructive"
                          onClick={() => setDeleteQuestionId(question.id)}
                        >
                          Xóa
                        </Button>
                      )}
                    </div>
                  </div>
                  <ImageStrip images={question.questionImageUrls ?? []} />
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>

      {/* ---------------- PE items ---------------- */}
      <Card>
        <CardHeader>
          <CardTitle>Đề PE ({peItems.length})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {canWritePe && (
            <form onSubmit={submitItem} className="space-y-3 rounded-lg border p-4">
              <p className="text-sm font-medium">{peEditingId ? "Sửa đề PE" : "Thêm đề PE mới"}</p>
              <div>
                <Label htmlFor="pe-title">Tiêu đề *</Label>
                <Input
                  id="pe-title"
                  value={peTitle}
                  onChange={(e) => setPeTitle(e.target.value)}
                  placeholder="PE Practice 1"
                />
              </div>
              <div>
                <Label htmlFor="pe-desc">Mô tả</Label>
                <Textarea
                  id="pe-desc"
                  value={peDescription}
                  onChange={(e) => setPeDescription(e.target.value)}
                  rows={2}
                />
              </div>
              <div className="space-y-2">
                <Label>Ảnh đề</Label>
                <ImageStrip
                  images={peImages}
                  onRemove={(index) => setPeImages((prev) => prev.filter((_, i) => i !== index))}
                />
                <input
                  type="file"
                  accept="image/*"
                  disabled={peUploading}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) onPeImagePicked(file);
                    e.target.value = "";
                  }}
                  className="text-sm"
                />
              </div>
              <div className="flex gap-2">
                <Button type="submit" disabled={peSaving || peUploading}>
                  {peEditingId ? "Cập nhật" : "Thêm đề PE"}
                </Button>
                {peEditingId && (
                  <Button type="button" variant="outline" onClick={resetPeForm}>
                    Hủy
                  </Button>
                )}
              </div>
            </form>
          )}

          <div className="space-y-4">
            {peItems.length === 0 ? (
              <p className="text-sm text-muted-foreground">Môn này chưa có đề PE.</p>
            ) : (
              peItems.map((item) => (
                <div key={item.id} className="rounded-lg border p-3">
                  <div className="mb-2 flex items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-medium">{item.title}</p>
                      {item.description && (
                        <p className="text-xs text-muted-foreground">{item.description}</p>
                      )}
                    </div>
                    <div className="flex shrink-0 gap-1">
                      {canWritePe && (
                        <Button size="sm" variant="outline" onClick={() => startEditItem(item)}>
                          Sửa
                        </Button>
                      )}
                      {canDeletePe && (
                        <Button
                          size="sm"
                          variant="destructive"
                          onClick={() => setDeleteItemId(item.id)}
                        >
                          Xóa
                        </Button>
                      )}
                    </div>
                  </div>

                  <ImageStrip images={item.examImageUrls ?? []} />

                  <div className="mt-3 space-y-2">
                    <p className="text-xs font-medium text-muted-foreground">
                      File tài nguyên ({item.resources.length})
                    </p>
                    {item.resources.map((resource) => (
                      <div
                        key={resource.id}
                        className="flex items-center justify-between gap-3 rounded border p-2 text-sm"
                      >
                        <span className="flex min-w-0 items-center gap-2">
                          <FileArchive className="h-4 w-4 shrink-0 text-muted-foreground" />
                          <span className="truncate">
                            {resource.folderLabel ? `${resource.folderLabel} / ` : ""}
                            {resource.originalFilename}
                          </span>
                          <span className="shrink-0 text-xs text-muted-foreground">
                            {formatSize(resource.sizeBytes)}
                          </span>
                        </span>
                        {canWritePe && (
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() =>
                              setDeleteResource({ itemId: item.id, resourceId: resource.id })
                            }
                          >
                            <Trash2 className="h-4 w-4 text-destructive" />
                          </Button>
                        )}
                      </div>
                    ))}
                    {canWritePe && (
                      <FileUploader
                        onUpload={uploadExamResource}
                        onUploaded={(info) => attachResource(item.id, info)}
                        label="Thêm file (zip/rar)"
                        buttonLabel="Chọn file"
                      />
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deleteQuestionId !== null}
        title="Xóa câu hỏi FE?"
        description="Ảnh và bản blur của câu hỏi cũng bị xóa. Không thể hoàn tác."
        confirmLabel="Xóa"
        destructive
        onConfirm={confirmDeleteQuestion}
        onOpenChange={(open) => !open && setDeleteQuestionId(null)}
      />
      <ConfirmDialog
        open={deleteItemId !== null}
        title="Xóa đề PE?"
        description="Ảnh đề và toàn bộ file tài nguyên của đề cũng bị xóa. Không thể hoàn tác."
        confirmLabel="Xóa"
        destructive
        onConfirm={confirmDeleteItem}
        onOpenChange={(open) => !open && setDeleteItemId(null)}
      />
      <ConfirmDialog
        open={deleteResource !== null}
        title="Xóa file tài nguyên?"
        description="File bị xóa khỏi lưu trữ. Không thể hoàn tác."
        confirmLabel="Xóa"
        destructive
        onConfirm={confirmDeleteResource}
        onOpenChange={(open) => !open && setDeleteResource(null)}
      />
    </AdminShell>
  );
}
