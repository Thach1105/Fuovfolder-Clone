"use client";

import Link from "next/link";
import { useState } from "react";
import {
  MOCK_CONTRIBUTORS,
  MOCK_THREADS,
  filterThreadsByTab,
  type ThreadTab,
} from "@/data/mock-threads";
import { ThreadTable } from "@/components/forum/ThreadTable";
import { ThreadTabs } from "@/components/forum/ThreadTabs";
import { TopContributors } from "@/components/forum/TopContributors";
import { BackendStatus } from "@/components/layout/BackendStatus";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { useAuth } from "@/lib/auth/AuthProvider";

export default function HomePage() {
  const [tab, setTab] = useState<ThreadTab>("discussion");
  const { user } = useAuth();
  const threads = filterThreadsByTab(MOCK_THREADS, tab);

  return (
    <div className="space-y-4">
      <BackendStatus />
      <PromoBanner />

      <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
        <section className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h1 className="text-xl font-bold text-slate-900">
                FuOverflow Community
              </h1>
              {user && (
                <p className="text-sm text-slate-500">
                  Xin chào, {user.displayName}
                </p>
              )}
            </div>
            <button type="button" className="btn-primary" disabled title="Sắp có API thread">
              Chủ đề mới
            </button>
          </div>

          <div className="flex flex-wrap gap-2">
            <Link href="/whats-new" className="btn-secondary text-xs">
              Bài mới
            </Link>
            <Link href="/search" className="btn-secondary text-xs">
              Tìm trong diễn đàn
            </Link>
            <Link href="/awards" className="btn-secondary text-xs">
              Danh hiệu
            </Link>
          </div>

          <div className="card overflow-hidden">
            <ThreadTabs active={tab} onChange={setTab} />
            <ThreadTable threads={threads} />
            <div className="border-t border-slate-100 px-4 py-3">
              <input
                type="search"
                placeholder="Tìm kiếm nhanh..."
                className="input-field max-w-xs"
                disabled
              />
              <p className="mt-2 text-xs text-slate-400">
                Dữ liệu thread đang dùng mock — sẽ gắn API khi backend forum/thread sẵn sàng.
              </p>
            </div>
          </div>

          <section className="card p-4">
            <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Khu vực điều hành
            </h2>
            <div className="grid gap-3 sm:grid-cols-3">
              {["Thông báo từ BQT", "Reports", "Thành viên vi phạm"].map(
                (title) => (
                  <div
                    key={title}
                    className="rounded-lg border border-slate-100 bg-slate-50 p-3 text-sm"
                  >
                    <p className="font-medium text-slate-800">{title}</p>
                    <p className="mt-1 text-xs text-slate-500">0 chủ đề · 0 bài</p>
                  </div>
                ),
              )}
            </div>
          </section>
        </section>

        <aside className="space-y-4">
          <TopContributors contributors={MOCK_CONTRIBUTORS} />
        </aside>
      </div>
    </div>
  );
}
