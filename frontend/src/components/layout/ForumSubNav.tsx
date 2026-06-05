"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ForumNavIcon } from "@/components/layout/ForumNavIcon";
import { FORUM_SUB_NAV_ITEMS, isForumSectionPath } from "@/lib/forum-nav";
import { cn } from "@/lib/utils";

export function ForumSubNav() {
  const pathname = usePathname();
  if (!isForumSectionPath(pathname)) {
    return null;
  }

  return (
    <div className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-7xl flex-wrap gap-1 px-4 py-2">
        {FORUM_SUB_NAV_ITEMS.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.label}
              href={item.href}
              className={cn(
                "inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition",
                active
                  ? "bg-fuo-50 text-fuo-700"
                  : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
              )}
            >
              <ForumNavIcon name={item.icon} />
              {item.label}
            </Link>
          );
        })}
      </div>
    </div>
  );
}
