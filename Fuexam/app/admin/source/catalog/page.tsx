"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { useSubmit } from "@/hooks/use-submit";
import {
  type AdminSourceCatalogItem,
  createSourceCatalogItem,
  deleteSourceCatalogItem,
  formatPoints,
  listAdminSourceCatalog,
  updateSourceCatalogItem,
} from "@/lib/api/source";

const EMPTY_FORM = {
  code: "",
  title: "",
  description: "",
  pricePoints: "",
  accessDays: "60",
  duplicationRatePercent: "0",
  passRatePercent: "0",
  cardColor: "",
  categorySlug: "on-thi",
  active: true,
  featured: false,
  sortOrder: "0",
};

const inputClass =
  "w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white";

export default function AdminSourceCatalogPage() {
  const [items, setItems] = useState<AdminSourceCatalogItem[]>([]);
  const { loading, error: loadError, run } = useAsyncAction();
  const { error: submitError, submit } = useSubmit("Lưu thất bại");
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [search, setSearch] = useState("");

  const error = loadError || submitError;

  const load = useCallback(() => run(async () => {
    setItems(await listAdminSourceCatalog());
  }), [run]);

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
      categorySlug: item.categorySlug ?? "",
      active: item.active,
      featured: item.featured,
      sortOrder: String(item.sortOrder),
    });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const body = {
      code: form.code.trim(),
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      pricePoints: parseInt(form.pricePoints, 10),
      accessDays: parseInt(form.accessDays, 10) || 60,
      duplicationRateBp: Math.round((parseFloat(form.duplicationRatePercent) || 0) * 100),
      passRateBp: Math.round((parseFloat(form.passRatePercent) || 0) * 100),
      cardColor: form.cardColor.trim() || undefined,
      categorySlug: form.categorySlug.trim() || undefined,
      active: form.active,
      featured: form.featured,
      sortOrder: parseInt(form.sortOrder, 10) || 0,
    };
    const result = await submit(async () => {
      if (editingId) {
        await updateSourceCatalogItem(editingId, body);
      } else {
        await createSourceCatalogItem(body);
      }
      return true;
    });
    if (result) {
      resetForm();
      await load();
    }
  }

  async function handleDelete(id: string) {
    if (!confirm("Xóa tài liệu này?")) return;
    await deleteSourceCatalogItem(id);
    await load();
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
    <AdminShell title="Source — Danh mục tài liệu" description="Quản lý mã môn, giá Fuexam Point và thời hạn">
      <div className="mb-4">
        <input
          className={`${inputClass} max-w-md`}
          placeholder="Tìm theo mã, tên hoặc danh mục..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        {search.trim() && (
          <p className="mt-1 text-xs text-slate-500">
            {filteredItems.length} / {items.length} tài liệu
          </p>
        )}
      </div>
      <div className="grid gap-8 lg:grid-cols-2">
        <form onSubmit={handleSubmit} className="space-y-3 rounded-xl border border-slate-800 bg-slate-900/50 p-5">
          <h2 className="text-sm font-semibold text-white">
            {editingId ? "Sửa tài liệu" : "Thêm tài liệu"}
          </h2>
          <input
            className={inputClass}
            placeholder="Mã (MLN111)"
            value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })}
            required
          />
          <input
            className={inputClass}
            placeholder="Tên tài liệu"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            required
          />
          <textarea
            className={inputClass}
            placeholder="Mô tả"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          <div className="grid grid-cols-2 gap-3">
            <label className="text-xs text-slate-400">
              Giá Fuexam Point
              <input
                className={`${inputClass} mt-1`}
                type="number"
                min={0}
                value={form.pricePoints}
                onChange={(e) => setForm({ ...form, pricePoints: e.target.value })}
                required
              />
            </label>
            <label className="text-xs text-slate-400">
              Thời hạn (ngày)
              <input
                className={`${inputClass} mt-1`}
                type="number"
                min={1}
                value={form.accessDays}
                onChange={(e) => setForm({ ...form, accessDays: e.target.value })}
              />
            </label>
            {editingId && (
              <p className="text-xs text-slate-400">
                Số câu hỏi:{" "}
                <span className="text-white">
                  {items.find((i) => i.id === editingId)?.questionCount ?? 0}
                </span>{" "}
                (tự đồng bộ từ ngân hàng câu hỏi)
              </p>
            )}
            <label className="text-xs text-slate-400">
              % Trùng lặp
              <input
                className={`${inputClass} mt-1`}
                type="number"
                min={0}
                step="0.01"
                value={form.duplicationRatePercent}
                onChange={(e) => setForm({ ...form, duplicationRatePercent: e.target.value })}
              />
            </label>
            <label className="text-xs text-slate-400">
              % Đậu
              <input
                className={`${inputClass} mt-1`}
                type="number"
                min={0}
                step="0.01"
                value={form.passRatePercent}
                onChange={(e) => setForm({ ...form, passRatePercent: e.target.value })}
              />
            </label>
            <label className="text-xs text-slate-400">
              Màu thẻ (hex)
              <input
                className={`${inputClass} mt-1`}
                placeholder="#6d28d9"
                value={form.cardColor}
                onChange={(e) => setForm({ ...form, cardColor: e.target.value })}
              />
            </label>
            <label className="text-xs text-slate-400">
              Danh mục (slug)
              <input
                className={`${inputClass} mt-1`}
                value={form.categorySlug}
                onChange={(e) => setForm({ ...form, categorySlug: e.target.value })}
              />
            </label>
            <label className="text-xs text-slate-400">
              Thứ tự hiển thị
              <input
                className={`${inputClass} mt-1`}
                type="number"
                min={0}
                value={form.sortOrder}
                onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
              />
            </label>
          </div>
          <div className="flex flex-wrap gap-6 text-sm text-slate-300">
            <label className="flex cursor-pointer items-center gap-2">
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) => setForm({ ...form, active: e.target.checked })}
              />
              <span>Đang bán</span>
            </label>
            <label className="flex cursor-pointer items-center gap-2">
              <input
                type="checkbox"
                checked={form.featured}
                onChange={(e) => setForm({ ...form, featured: e.target.checked })}
              />
              <span>Nổi bật</span>
            </label>
          </div>
          <ErrorBanner message={error} />
          <div className="flex gap-2">
            <button type="submit" className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-semibold text-slate-950">
              {editingId ? "Cập nhật" : "Tạo mới"}
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
          ) : (
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-slate-900 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Mã</th>
                  <th className="px-3 py-2">Câu hỏi</th>
                  <th className="px-3 py-2">Giá</th>
                  <th className="px-3 py-2">Hạn</th>
                  <th className="px-3 py-2">Trạng thái</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((item) => (
                  <tr key={item.id} className="border-t border-slate-800">
                    <td className="px-3 py-2">
                      <span className="font-mono text-amber-400">{item.code}</span>
                      <p className="max-w-[200px] truncate text-xs text-slate-500">{item.title}</p>
                    </td>
                    <td className="px-3 py-2 text-slate-400">{item.questionCount}</td>
                    <td className="px-3 py-2">{formatPoints(item.pricePoints)}</td>
                    <td className="px-3 py-2 text-slate-400">{item.accessDays}d</td>
                    <td className="px-3 py-2 text-xs">
                      {item.active ? "Đang bán" : "Ẩn"}
                      {item.featured && " · ★"}
                    </td>
                    <td className="space-x-2 px-3 py-2 text-right">
                      <Link
                        href={`/admin/source/catalog/${item.id}/questions`}
                        className="text-emerald-400 hover:underline"
                      >
                        Câu hỏi
                      </Link>
                      <button type="button" className="text-amber-400 hover:underline" onClick={() => startEdit(item)}>
                        Sửa
                      </button>
                      <button type="button" className="text-red-400 hover:underline" onClick={() => handleDelete(item.id)}>
                        Xóa
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </AdminShell>
  );
}
