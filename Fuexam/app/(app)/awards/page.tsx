"use client";

import { useEffect, useState } from "react";
import { resolveMediaUrl } from "@/lib/api/media";
import { listAwardDefinitions, type AwardDefinition } from "@/lib/api/awards";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { useAsyncAction } from "@/hooks/use-async-action";

export default function AwardsPage() {
  const [awards, setAwards] = useState<AwardDefinition[]>([]);
  const { loading, error, run } = useAsyncAction("Không tải được danh hiệu.");

  useEffect(() => {
    run(async () => {
      setAwards(await listAwardDefinitions());
    });
  }, [run]);

  return (
    <div className="mx-auto max-w-4xl px-4 py-10">
      <h1 className="text-3xl font-bold text-slate-900">Danh hiệu</h1>
      <p className="mt-2 text-slate-600">Các huy hiệu và thành tích trên Fuexam.</p>

      <ErrorBanner message={error} className="mt-6" />
      {loading && <LoadingState className="mt-6" />}

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
                <img src={iconUrl} alt="" loading="lazy" className="h-14 w-14 rounded-lg object-cover" />
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
