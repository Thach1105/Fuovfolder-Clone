"use client";

import Link from "next/link";
import { Bell } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  getUnreadNotificationCount,
  NOTIFICATION_POLL_MS,
} from "@/lib/api/notifications";

/**
 * Chuông thông báo trên header — hiển thị số thông báo chưa đọc, tự cập nhật
 * theo chu kỳ và khi cửa sổ được focus. Dùng token theme cho khớp header mới.
 */
export function HeaderNotificationBell() {
  const { user } = useAuth();
  const [count, setCount] = useState(0);

  const refresh = useCallback(async () => {
    if (!user) {
      setCount(0);
      return;
    }
    try {
      const result = await getUnreadNotificationCount();
      setCount(result.count);
    } catch {
      setCount(0);
    }
  }, [user]);

  useEffect(() => {
    refresh();
    const timer = window.setInterval(refresh, NOTIFICATION_POLL_MS);
    const onFocus = () => refresh();
    window.addEventListener("focus", onFocus);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", onFocus);
    };
  }, [refresh]);

  if (!user) return null;

  return (
    <Link
      href="/notifications"
      aria-label={`Thông báo${count > 0 ? `, ${count} chưa đọc` : ""}`}
      className="relative rounded-full p-2 text-foreground/60 transition hover:bg-foreground/5 hover:text-foreground"
    >
      <Bell className="h-4 w-4" />
      {count > 0 && (
        <span className="absolute right-0 top-0 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
          {count > 99 ? "99+" : count}
        </span>
      )}
    </Link>
  );
}
