"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { resolveMediaUrl } from "@/lib/api/media";

interface AwardDefinition {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  iconUrl: string | null;
  awardType: string;
  active: boolean;
}

export default function AwardsPage() {
  const [awards, setAwards] = useState<AwardDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiFetch<AwardDefinition[]>("/api/v1/awards/definitions")
      .then(setAwards)
      .catch(() => setError("Không tải được danh hiệu."))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-4xl px-4 py-10">
      <h1 className="text-3xl font-bold text-slate-900">Danh hiệu</h1>
      <p className="mt-2 text-slate-600">Các huy hiệu và thành tích trên FUExam.</p>

      {error && <p className="mt-6 text-sm text-red-600">{error}</p>}
      {loading && <p className="mt-6 text-sm text-slate-500">Đang tải...</p>}

      {!loading && awards.length === 0 && !error && (
        <p className="mt-6 text-sm text-slate-500">Chưa có danh hiệu nào.</p>
      )}

      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        {awards.map((award) => {
          const iconUrl = resolveMediaUrl(award.iconUrl);
          return (
            <article key={award.id} className="card flex gap-4 p-4">
              {iconUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={iconUrl} alt="" className="h-14 w-14 rounded-lg object-cover" />
              ) : (
                <div className="flex h-14 w-14 items-center justify-center rounded-lg bg-fuo-50 text-lg font-bold text-fuo-700">
                  {award.name.charAt(0)}
                </div>
              )}
              <div>
                <h2 className="font-semibold text-slate-900">{award.name}</h2>
                {award.description && <p className="mt-1 text-sm text-slate-600">{award.description}</p>}
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
