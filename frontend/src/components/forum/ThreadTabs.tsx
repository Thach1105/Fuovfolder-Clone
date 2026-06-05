"use client";

import type { ThreadTab } from "@/lib/api/forum";
import { cn } from "@/lib/utils";

const TABS: { id: ThreadTab; label: string }[] = [
  { id: "discussion", label: "Thảo luận" },
  { id: "confession", label: "Confession mới" },
  { id: "popular", label: "Xem nhiều" },
];

interface ThreadTabsProps {
  active: ThreadTab;
  onChange: (tab: ThreadTab) => void;
}

export function ThreadTabs({ active, onChange }: ThreadTabsProps) {
  return (
    <div className="flex gap-1 border-b border-slate-200">
      {TABS.map((tab) => (
        <button
          key={tab.id}
          type="button"
          onClick={() => onChange(tab.id)}
          className={cn(
            "px-4 py-2.5 text-sm font-medium transition",
            active === tab.id
              ? "border-b-2 border-fuo-600 text-fuo-700"
              : "text-slate-500 hover:text-slate-700",
          )}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
