"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { ImageUploader } from "@/components/admin/ImageUploader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
  type AdminSourceCatalogItem,
  createSourceCatalogItem,
  deleteSourceCatalogItem,
  formatPoints,
  getSourceDetail,
  listAdminSourceCatalog,
  setRelatedSourceItems,
  updateSourceCatalogItem,
} from "@/lib/api/source";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const EMPTY_FORM = {
  code: "",
  title: "",
  description: "",
  pricePoints: "",
  accessDays: "60",
  duplicationRatePercent: "0",
  passRatePercent: "0",
  cardColor: "",
  coverImageUrl: "",
  categorySlug: "on-thi",
  active: true,
  featured: false,
  sortOrder: "0",
};

export default function AdminSourceCatalogPage() {
  const { user } = useAuth();
  const canWrite =
    can(user, "source.catalog.admin:create") || can(user, "source.catalog.admin:update");
  const canDelete = can(user, "source.catalog.admin:delete");

  const [items, setItems] = useState<AdminSourceCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState("");
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const [relatedTarget, setRelatedTarget] = useState<AdminSourceCatalogItem | null>(null);
  const [relatedSelected, setRelatedSelected] = useState<Set<string>>(new Set());
  const [relatedLoading, setRelatedLoading] = useState(false);
  const [relatedSaving, setRelatedSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listAdminSourceCatalog());
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
    setForm(EMPTY_FORM);
    setEditingId(null);
  }

  function startEdit(item: AdminSourceCatalogItem) {
    setEditingId(item.id);
    setForm({
      code: item.code,
      title: item.title,
      description: item.description ?? "",
      pricePoints: String(item.pricePoints),
      accessDays: String(item.accessDays),
      duplicationRatePercent: String(item.duplicationRateBp / 100),
      passRatePercent: String(item.passRateBp / 100),
      cardColor: item.cardColor ?? "",
      coverImageUrl: item.coverImageUrl ?? "",
      categorySlug: item.categorySlug ?? "",
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
      accessDays: parseInt(form.accessDays, 10) || 60,
      duplicationRateBp: Math.round((parseFloat(form.duplicationRatePercent) || 0) * 100),
      passRateBp: Math.round((parseFloat(form.passRatePercent) || 0) * 100),
      cardColor: form.cardColor.trim() || undefined,
      coverImageUrl: form.coverImageUrl || undefined,
      categorySlug: form.categorySlug.trim() || undefined,
      active: form.active,
      featured: form.featured,
      sortOrder: parseInt(form.sortOrder, 10) || 0,
    };
    try {
      if (editingId) {
        await updateSourceCatalogItem(editingId, body);
        toast.success("Đã cập nhật tài liệu.");
      } else {
        await createSourceCatalogItem(body);
        toast.success("Đã tạo tài liệu.");
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
      await deleteSourceCatalogItem(deleteId);
      toast.success("Đã xóa tài liệu.");
      if (editingId === deleteId) resetForm();
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setDeleteId(null);
    }
  }

  async function openRelated(item: AdminSourceCatalogItem) {
    setRelatedTarget(item);
    setRelatedSelected(new Set());
    setRelatedLoading(true);
    try {
      const detail = await getSourceDetail(item.id);
      setRelatedSelected(new Set(detail.related.map((r) => r.id)));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không tải được tài liệu liên quan.");
    } finally {
      setRelatedLoading(false);
    }
  }

  function toggleRelated(id: string) {
    setRelatedSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function saveRelated() {
    if (!relatedTarget) return;
    setRelatedSaving(true);
    try {
      await setRelatedSourceItems(relatedTarget.id, Array.from(relatedSelected));
      toast.success("Đã lưu tài liệu liên quan.");
      setRelatedTarget(null);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Lưu thất bại.");
    } finally {
      setRelatedSaving(false);
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
      title="Source — Tài liệu"
      description="Quản lý mã môn, giá FUO Point và thời hạn truy cập"
    >
      <div className="mb-4 max-w-md">
        <Input
          placeholder="Tìm theo mã, tên hoặc danh mục..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {search.trim() && (
          <p className="mt-1 text-xs text-muted-foreground">
            {filteredItems.length} / {items.length} tài liệu
          </p>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {canWrite && (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                {editingId ? "Sửa tài liệu" : "Thêm tài liệu"}
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
                    placeholder="MLN111"
                    value={form.code}
                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                    disabled={!!editingId}
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="title">Tên tài liệu</Label>
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
                  purpose="source_cover"
                  value={form.coverImageUrl || null}
                  onChange={(url) => setForm({ ...form, coverImageUrl: url ?? "" })}
                />
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="price">Giá (FUO Point)</Label>
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
                    <Label htmlFor="access">Thời hạn (ngày)</Label>
                    <Input
                      id="access"
                      type="number"
                      min={1}
                      value={form.accessDays}
                      onChange={(e) => setForm({ ...form, accessDays: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="dup">% Trùng lặp</Label>
                    <Input
                      id="dup"
                      type="number"
                      min={0}
                      step="0.01"
                      value={form.duplicationRatePercent}
                      onChange={(e) =>
                        setForm({ ...form, duplicationRatePercent: e.target.value })
                      }
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="pass">% Đậu</Label>
                    <Input
                      id="pass"
                      type="number"
                      min={0}
                      step="0.01"
                      value={form.passRatePercent}
                      onChange={(e) => setForm({ ...form, passRatePercent: e.target.value })}
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
                  {editingId && (
                    <p className="self-end text-xs text-muted-foreground">
                      Số câu hỏi:{" "}
                      <span className="text-foreground">
                        {items.find((i) => i.id === editingId)?.questionCount ?? 0}
                      </span>
                    </p>
                  )}
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
                    <TableHead>Câu hỏi</TableHead>
                    <TableHead>Giá</TableHead>
                    <TableHead>Hạn</TableHead>
                    <TableHead>Trạng thái</TableHead>
                    <TableHead className="text-right">Hành động</TableHead>
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
                      <TableCell className="text-muted-foreground">{item.questionCount}</TableCell>
                      <TableCell>{formatPoints(item.pricePoints)}</TableCell>
                      <TableCell className="text-muted-foreground">{item.accessDays}d</TableCell>
                      <TableCell className="text-xs">
                        {item.active ? "Đang bán" : "Ẩn"}
                        {item.featured && " · ★"}
                      </TableCell>
                      <TableCell className="space-x-2 text-right">
                        <Link
                          href={`/source/catalog/${item.id}/questions`}
                          className="text-sm text-emerald-500 hover:underline"
                        >
                          Câu hỏi
                        </Link>
                        {canWrite && (
                          <button
                            type="button"
                            className="text-sm text-sky-500 hover:underline"
                            onClick={() => openRelated(item)}
                          >
                            Liên quan
                          </button>
                        )}
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
                  {filteredItems.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="text-center text-sm text-muted-foreground">
                        Không có tài liệu nào.
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
        title="Xóa tài liệu?"
        description="Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Xóa"
        onConfirm={confirmDelete}
        onOpenChange={(o) => !o && setDeleteId(null)}
      />

      <Dialog open={relatedTarget != null} onOpenChange={(o) => !o && setRelatedTarget(null)}>
        <DialogContent className="max-h-[80vh] overflow-hidden">
          <DialogHeader>
            <DialogTitle>Tài liệu liên quan</DialogTitle>
            <DialogDescription>
              {relatedTarget && `Chọn tài liệu hiển thị kèm ${relatedTarget.code}.`}
            </DialogDescription>
          </DialogHeader>
          {relatedLoading ? (
            <p className="text-sm text-muted-foreground">Đang tải...</p>
          ) : (
            <div className="max-h-[50vh] space-y-1 overflow-auto pr-1">
              {items
                .filter((i) => i.id !== relatedTarget?.id)
                .map((i) => (
                  <label
                    key={i.id}
                    className="flex cursor-pointer items-center gap-3 rounded-lg px-2 py-2 text-sm hover:bg-accent/40"
                  >
                    <Checkbox
                      checked={relatedSelected.has(i.id)}
                      onCheckedChange={() => toggleRelated(i.id)}
                    />
                    <span>
                      <span className="font-mono text-primary">{i.code}</span>
                      <span className="ml-2 text-muted-foreground">{i.title}</span>
                    </span>
                  </label>
                ))}
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setRelatedTarget(null)} disabled={relatedSaving}>
              Hủy
            </Button>
            <Button onClick={saveRelated} disabled={relatedSaving || relatedLoading}>
              {relatedSaving ? "Đang lưu..." : `Lưu (${relatedSelected.size})`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminShell>
  );
}
