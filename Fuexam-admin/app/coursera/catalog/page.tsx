"use client";

import { useCallback, useEffect, useState } from "react";
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
  type AdminCatalogItem,
  createCatalogItem,
  deleteCatalogItem,
  formatPoints,
  listAdminCatalog,
  updateCatalogItem,
} from "@/lib/api/coursera";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const EMPTY = {
  code: "",
  title: "",
  description: "",
  pricePoints: "",
  coverImageUrl: "",
  active: true,
  featured: false,
  sortOrder: "0",
};

export default function AdminCourseraCatalogPage() {
  const { user } = useAuth();
  const canWrite = can(user, "coursera.catalog.admin:create") || can(user, "coursera.catalog.admin:update");
  const canDelete = can(user, "coursera.catalog.admin:delete");

  const [items, setItems] = useState<AdminCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listAdminCatalog());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh mục.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function resetForm() {
    setForm(EMPTY);
    setEditingId(null);
  }

  function startEdit(item: AdminCatalogItem) {
    setEditingId(item.id);
    setForm({
      code: item.code,
      title: item.title,
      description: item.description ?? "",
      pricePoints: String(item.pricePoints),
      coverImageUrl: item.coverImageUrl ?? "",
      active: item.active,
      featured: item.featured,
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
      pricePoints: parseInt(form.pricePoints, 10),
      coverImageUrl: form.coverImageUrl || undefined,
      active: form.active,
      featured: form.featured,
      sortOrder: parseInt(form.sortOrder, 10) || 0,
    };
    try {
      if (editingId) {
        await updateCatalogItem(editingId, body);
        toast.success("Đã cập nhật khóa học.");
      } else {
        await createCatalogItem(body);
        toast.success("Đã tạo khóa học.");
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
      await deleteCatalogItem(deleteId);
      toast.success("Đã xóa khóa học.");
      if (editingId === deleteId) resetForm();
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteId(null);
    }
  }

  return (
    <AdminShell title="Coursera — Khóa học" description="Quản lý mã khóa học và giá Fuexam Point">
      <div className="grid gap-6 lg:grid-cols-2">
        {canWrite && (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                {editingId ? "Sửa khóa học" : "Thêm khóa học"}
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
                  <Label htmlFor="code">Mã khóa</Label>
                  <Input
                    id="code"
                    placeholder="WOU203C"
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    disabled={!!editingId}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="title">Tên khóa học</Label>
                  <Input
                    id="title"
                    value={form.title}
                    onChange={(e) => setForm({ ...form, title: e.target.value })}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="description">Mô tả</Label>
                  <Textarea
                    id="description"
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                  />
                </div>
                <ImageUploader
                  label="Ảnh bìa"
                  purpose="coursera_cover"
                  value={form.coverImageUrl || null}
                  onChange={(url) => setForm({ ...form, coverImageUrl: url ?? "" })}
                />
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="price">Giá (Fuexam Point)</Label>
                    <Input
                      id="price"
                      type="number"
                      min={0}
                      value={form.pricePoints}
                      onChange={(e) => setForm({ ...form, pricePoints: e.target.value })}
                      required
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="sort">Thứ tự (nhỏ → lên trên)</Label>
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
                    Đang bán
                  </Label>
                  <Switch
                    id="active"
                    checked={form.active}
                    onCheckedChange={(v) => setForm({ ...form, active: v })}
                  />
                </div>
                <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
                  <Label htmlFor="featured" className="cursor-pointer">
                    Nổi bật (★)
                  </Label>
                  <Switch
                    id="featured"
                    checked={form.featured}
                    onCheckedChange={(v) => setForm({ ...form, featured: v })}
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
                    <TableHead>Giá</TableHead>
                    <TableHead>Thứ tự</TableHead>
                    <TableHead>Trạng thái</TableHead>
                    <TableHead className="text-right">Hành động</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <span className="font-mono text-primary">{item.code}</span>
                        <p className="max-w-[200px] truncate text-xs text-muted-foreground">
                          {item.title}
                        </p>
                      </TableCell>
                      <TableCell>{formatPoints(item.pricePoints)}</TableCell>
                      <TableCell className="font-mono text-muted-foreground">
                        {item.sortOrder}
                      </TableCell>
                      <TableCell className="text-xs">
                        {item.active ? "Đang bán" : "Ẩn"}
                        {item.featured && " · ★"}
                      </TableCell>
                      <TableCell className="space-x-2 text-right">
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
                      </TableCell>
                    </TableRow>
                  ))}
                  {items.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} className="text-center text-sm text-muted-foreground">
                        Chưa có khóa học nào.
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
        title="Xóa khóa học?"
        description="Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDelete}
        onOpenChange={(o) => !o && setDeleteId(null)}
      />
    </AdminShell>
  );
}
