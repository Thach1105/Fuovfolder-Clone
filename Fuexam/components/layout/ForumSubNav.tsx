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
    <div className="border-b border-ink-200/70 bg-ink-50/60 backdrop-blur">
      <div className="mx-auto flex max-w-7xl flex-wrap gap-1.5 px-4 py-2.5">
        {FORUM_SUB_NAV_ITEMS.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.label}
              href={item.href}
              className={cn(
                "inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium transition-colors",
                active
                  ? "bg-ink-900 text-ink-50"
                  : "text-ink-600 hover:bg-ink-100 hover:text-ink-900",
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