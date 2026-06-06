"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ForumCard } from "@/components/forum/ForumCard";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { ApiError } from "@/lib/api/client";
import { type Forum, listForums } from "@/lib/api/forum";

export default function ForumsPage() {
  const [forums, setForums] = useState<Forum[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listForums()
      .then(setForums)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Không tải được danh sách diễn đàn"),
      )
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-5">
      <PromoBanner />

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Diễn đàn</h1>
          <p className="text-sm text-slate-600">
            Danh sách khu vực thảo luận trong hệ thống.
          </p>
        </div>
        <Link href="/" className="text-sm font-medium text-fuo-600 hover:underline">
          ← Trang chủ
        </Link>
      </div>

      {error && (
        <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">{error}</p>
      )}

      {loading ? (
        <p className="text-sm text-slate-500">Đang tải...</p>
      ) : forums.length === 0 ? (
        <div className="card p-6 text-sm text-slate-600">
          <p>Chưa có diễn đàn nào trong hệ thống.</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {forums.map((forum) => (
            <ForumCard key={forum.id} forum={forum} />
          ))}
        </div>
      )}
    </div>
  );
}
