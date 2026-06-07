"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ForumNavIcon } from "@/components/layout/ForumNavIcon";
import { FORUM_DROPDOWN_ITEMS, isForumSectionPath } from "@/lib/forum-nav";
import { cn } from "@/lib/utils";

export function ForumNavDropdown() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const active = isForumSectionPath(pathname);

  useEffect(() => {
    function onPointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className={cn(
          "flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition",
          active
            ? "bg-fuo-50 text-fuo-700"
            : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
        )}
        aria-expanded={open}
        aria-haspopup="menu"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
        Diễn đàn
        <svg
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          className={cn("transition", open && "rotate-180")}
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>

      {open && (
        <div
          role="menu"
          className="absolute left-0 top-full z-50 mt-1 min-w-[300px] rounded-xl border border-slate-200 bg-white py-2 shadow-lg"
        >
          {FORUM_DROPDOWN_ITEMS.map((item) => (
            <div key={`${item.href}-${item.label}`}>
              {item.dividerBefore && <div className="my-1 border-t border-slate-100" />}
              <Link
                href={item.href}
                role="menuitem"
                onClick={() => setOpen(false)}
                className="flex items-center gap-3 px-4 py-2 text-sm text-slate-700 transition hover:bg-slate-50 hover:text-fuo-700"
              >
                <span className="text-slate-400">
                  <ForumNavIcon name={item.icon} />
                </span>
                {item.label}
              </Link>
              {item.children?.map((child) => (
                <Link
                  key={`${child.href}-${child.label}`}
                  href={child.href}
                  role="menuitem"
                  onClick={() => setOpen(false)}
                  className="flex items-center gap-3 py-2 pl-11 pr-4 text-sm text-slate-600 transition hover:bg-slate-50 hover:text-fuo-700"
                >
                  <span className="text-slate-400">
                    <ForumNavIcon name={child.icon} />
                  </span>
                  {child.label}
                </Link>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
