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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  type AdminSubject,
  createExamSubject,
  deleteExamSubject,
  listExamSubjects,
  updateExamSubject,
} from "@/lib/api/exam";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const EMPTY_FORM = {
  code: "",
  title: "",
  description: "",
  coverImageUrl: "",
  cardColor: "",
  categorySlug: "on-thi",
  fePreviewCount: "3",
  active: true,
  sortOrder: "0",
};

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
      fePreviewCount: String(item.fePreviewCount),
      active: item.active,
      sortOrder: String(item.sortOrder),
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
      fePreviewCount: parseInt(form.fePreviewCount, 10) || 0,
      active: form.active,
      sortOrder: parseInt(form.sortOrder, 10) || 0,
    };
    try {
      if (editingId) {
        await updateExamSubject(editingId, body);
        toast.success("Đã cập nhật môn thi.");
      } else {
        await createExamSubject(body);
        toast.success("Đã tạo môn thi.");
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

  async function confirmDelete() {
    if (!deleteId) return;
    try {
      await deleteExamSubject(deleteId);
      toast.success("Đã xóa môn thi.");
      if (editingId === deleteId) resetForm();
      await load();
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

  return (
    <AdminShell
      title="Exam FE/PE — Môn thi"
      description="Quản lý môn thi, số câu preview FE và tài liệu PE"
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
                  purpose="exam_fe_image"
                  value={form.coverImageUrl || null}
                  onChange={(url) => setForm({ ...form, coverImageUrl: url ?? "" })}
                />
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="fePreview">Số câu preview FE (chưa mua membership)</Label>
                    <Input
                      id="fePreview"
                      type="number"
                      min={0}
                      value={form.fePreviewCount}
                      onChange={(e) => setForm({ ...form, fePreviewCount: e.target.value })}
                    />
                  </div>
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
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Mã</TableHead>
                    <TableHead>FE</TableHead>
                    <TableHead>PE</TableHead>
                    <TableHead>Trạng thái</TableHead>
                    <TableHead className="w-[160px] text-right">Hành động</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredItems.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <span className="font-mono text-primary">{item.code}</span>
                        <p className="max-w-[180px] truncate text-xs text-muted-foreground">
                          {item.title}
                        </p>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {item.feQuestionCount}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{item.pePaperCount}</TableCell>
                      <TableCell className="text-xs">
                        {item.active ? "Hiển thị" : "Ẩn"}
                      </TableCell>
                      <TableCell className="text-right align-top">
                        <div className="flex flex-wrap justify-end gap-x-3 gap-y-1">
                          <Link
                            href={`/exam/subjects/${item.id}/fe`}
                            className="text-sm text-emerald-500 hover:underline"
                          >
                            FE
                          </Link>
                          <Link
                            href={`/exam/subjects/${item.id}/pe`}
                            className="text-sm text-sky-500 hover:underline"
                          >
                            PE
                          </Link>
                          {canWrite && (
                            <button
                              type="button"
                              className="text-sm text-primary hover:underline"
                              onClick={() => startEdit(item)}
                            >
                              Sửa
                            </button>
                          )}
                          {canDelete && (
                            <button
                              type="button"
                              className="text-sm text-destructive hover:underline"
                              onClick={() => setDeleteId(item.id)}
                            >
                              Xóa
                            </button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                  {filteredItems.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} className="text-center text-sm text-muted-foreground">
                        Không có môn thi nào.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
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
