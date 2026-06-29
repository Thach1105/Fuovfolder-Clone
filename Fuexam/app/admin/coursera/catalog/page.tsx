"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";
import { useSubmit } from "@/hooks/use-submit";
import {
  type AdminCatalogItem,
  createCatalogItem,
  deleteCatalogItem,
  formatPoints,
  listAdminCatalog,
  updateCatalogItem,
} from "@/lib/api/coursera";

export default function AdminCourseraCatalogPage() {
  const [items, setItems] = useState<AdminCatalogItem[]>([]);
  const { loading, error: loadError, run } = useAsyncAction();
  const { error: submitError, submit } = useSubmit("Lưu thất bại");
  const [form, setForm] = useState({
    code: "",
    title: "",
    description: "",
    pricePoints: "",
    active: true,
    featured: false,
    sortOrder: "0",
  });
  const [editingId, setEditingId] = useState<string | null>(null);

  const error = loadError || submitError;

  const load = useCallback(() => run(async () => {
    setItems(await listAdminCatalog());
  }), [run]);

  useEffect(() => {
    load();
  }, [load]);

  function resetForm() {
    setForm({
      code: "",
      title: "",
      description: "",
      pricePoints: "",
      active: true,
      featured: false,
      sortOrder: "0",
    });
    setEditingId(null);
  }

  function startEdit(item: AdminCatalogItem) {
    setEditingId(item.id);
    setForm({
      code: item.code,
      title: item.title,
      description: item.description ?? "",
      pricePoints: String(item.pricePoints),
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
      active: form.active,
      featured: form.featured,
      sortOrder: parseInt(form.sortOrder, 10) || 0,
    };
    const result = await submit(async () => {
      if (editingId) {
        await updateCatalogItem(editingId, body);
      } else {
        await createCatalogItem(body);
      }
      return true;
    });
    if (result) {
      resetForm();
      await load();
    }
  }

  async function handleDelete(id: string) {
    if (!confirm("Xóa khóa học này?")) return;
    await deleteCatalogItem(id);
    await load();
  }

  return (
    <AdminShell title="Coursera — Danh mục khóa học" description="Quản lý mã khóa và giá Fuexam Point">
      <div className="grid gap-8 lg:grid-cols-2">
        <form onSubmit={handleSubmit} className="rounded-xl border border-slate-800 bg-slate-900/50 p-5 space-y-3">
          <h2 className="text-sm font-semibold text-white">
            {editingId ? "Sửa khóa học" : "Thêm khóa học"}
          </h2>
          <input
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
            placeholder="Mã (WOU203C)"
            value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })}
            required
          />
          <input
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
            placeholder="Tên khóa học"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            required
          />
          <textarea
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
            placeholder="Mô tả"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          <input
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
            type="number"
            min={0}
            placeholder="Giá Fuexam Point"
            value={form.pricePoints}
            onChange={(e) => setForm({ ...form, pricePoints: e.target.value })}
            required
          />
          <fieldset className="space-y-3 rounded-lg border border-slate-700/80 p-3">
            <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-slate-400">
              Hiển thị trên trang Coursera
            </legend>
            <div className="flex flex-wrap gap-6 text-sm text-slate-300">
              <label className="flex cursor-pointer items-start gap-2">
                <input
                  type="checkbox"
                  className="mt-0.5"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                <span>
                  <span className="font-medium text-slate-200">Đang bán</span>
                  <span className="mt-0.5 block text-xs text-slate-500">
                    Bật thì user thấy và được chọn khóa này khi tạo yêu cầu.
                  </span>
                </span>
              </label>
              <label className="flex cursor-pointer items-start gap-2">
                <input
                  type="checkbox"
                  className="mt-0.5"
                  checked={form.featured}
                  onChange={(e) => setForm({ ...form, featured: e.target.checked })}
                />
                <span>
                  <span className="font-medium text-slate-200">Nổi bật</span>
                  <span className="mt-0.5 block text-xs text-slate-500">
                    Đánh dấu khóa phổ biến (★ trong bảng). Dùng khi lọc / highlight sau này.
                  </span>
                </span>
              </label>
            </div>
            <div>
              <label htmlFor="catalog-sort-order" className="text-sm font-medium text-slate-200">
                Thứ tự hiển thị (Order)
              </label>
              <p className="mt-1 text-xs text-slate-500">
                Số càng <strong className="text-slate-400">nhỏ</strong> thì khóa càng{" "}
                <strong className="text-slate-400">lên trên</strong> trong danh sách user và admin.
                Ví dụ: Order 5 hiển thị trước Order 10, 20.
              </p>
              <input
                id="catalog-sort-order"
                className="mt-2 w-28 rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
                type="number"
                min={0}
                step={1}
                value={form.sortOrder}
                onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
              />
            </div>
          </fieldset>
          <ErrorBanner message={error} />
          <div className="flex gap-2">
            <button type="submit" className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-semibold text-slate-950">
              {editingId ? "Cập nhật" : "Tạo mới"}
            </button>
            {editingId && (
              <button type="button" className="rounded-lg border border-slate-600 px-4 py-2 text-sm text-slate-300" onClick={resetForm}>
                Hủy
              </button>
            )}
          </div>
        </form>

        <div className="rounded-xl border border-slate-800 overflow-hidden">
          {loading ? (
            <LoadingState className="p-4" />
          ) : (
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-slate-900 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2">Mã</th>
                  <th className="px-3 py-2">Giá</th>
                  <th className="px-3 py-2" title="Order — thứ tự hiển thị">
                    Order
                  </th>
                  <th className="px-3 py-2">Trạng thái</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id} className="border-t border-slate-800">
                    <td className="px-3 py-2">
                      <span className="font-mono text-amber-400">{item.code}</span>
                      <p className="text-xs text-slate-500 truncate max-w-[200px]">{item.title}</p>
                    </td>
                    <td className="px-3 py-2">{formatPoints(item.pricePoints)}</td>
                    <td className="px-3 py-2 font-mono text-slate-400">{item.sortOrder}</td>
                    <td className="px-3 py-2 text-xs">
                      {item.active ? "Đang bán" : "Ẩn"}
                      {item.featured && " · ★ Nổi bật"}
                    </td>
                    <td className="px-3 py-2 text-right space-x-2">
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
