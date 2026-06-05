import Link from "next/link";
import type { Forum } from "@/lib/api/forum";

export function ForumCard({ forum }: { forum: Forum }) {
  return (
    <Link
      href={`/forums/${forum.slug}`}
      className="card block p-4 transition hover:border-fuo-200 hover:shadow-md"
    >
      <h3 className="font-semibold text-slate-900">{forum.title}</h3>
      {forum.description && (
        <p className="mt-1 line-clamp-2 text-sm text-slate-600">{forum.description}</p>
      )}
      <p className="mt-3 text-xs text-slate-400">/{forum.slug}</p>
    </Link>
  );
}
