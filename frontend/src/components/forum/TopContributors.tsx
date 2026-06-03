import type { MockContributor } from "@/types/api";

interface TopContributorsProps {
  contributors: MockContributor[];
}

export function TopContributors({ contributors }: TopContributorsProps) {
  return (
    <div className="card p-4">
      <h2 className="mb-4 text-base font-semibold text-slate-800">
        Top tương tác
      </h2>
      <ul className="space-y-3">
        {contributors.map((c) => (
          <li key={c.username} className="flex items-center gap-3">
            <span className="w-5 text-center text-xs font-medium text-slate-400">
              {c.rank}
            </span>
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-fuo-100 text-sm font-semibold text-fuo-700">
              {c.initial}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-slate-800">
                {c.username}
              </p>
              <p className="text-xs text-slate-500">
                {c.score.toLocaleString()} điểm
              </p>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
