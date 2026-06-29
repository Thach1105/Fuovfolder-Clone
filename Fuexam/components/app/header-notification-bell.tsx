"use client";

import { Bell, CheckCheck, Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useAuth } from "@/lib/auth/AuthProvider";
import { NOTIFICATION_PREVIEW_SIZE } from "@/lib/constants/pagination";
import {
  getUnreadNotificationCount,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  NOTIFICATION_POLL_MS,
  notificationThreadHref,
  type NotificationItem,
} from "@/lib/api/notifications";
import { cn } from "@/lib/utils";

function timeAgo(value: string) {
  const created = new Date(value).getTime();
  const diff = Math.max(0, Date.now() - created);
  const minute = 60_000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diff < minute) return "Vừa xong";
  if (diff < hour) return `${Math.floor(diff / minute)} phút trước`;
  if (diff < day) return `${Math.floor(diff / hour)} giờ trước`;
  return new Date(value).toLocaleDateString("vi-VN");
}

export function HeaderNotificationBell() {
  const router = useRouter();
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [count, setCount] = useState(0);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);

  const refreshCount = useCallback(async () => {
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

  const refreshItems = useCallback(async () => {
    if (!user) {
      setItems([]);
      return;
    }
    setLoading(true);
    try {
      const result = await listNotifications({ page: 0, size: NOTIFICATION_PREVIEW_SIZE });
      setItems(result.items);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    refreshCount();
    let timer = window.setInterval(refreshCount, NOTIFICATION_POLL_MS);

    function handleVisibility() {
      if (document.hidden) {
        window.clearInterval(timer);
      } else {
        refreshCount();
        timer = window.setInterval(refreshCount, NOTIFICATION_POLL_MS);
      }
    }

    document.addEventListener("visibilitychange", handleVisibility);
    window.addEventListener("focus", refreshCount);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", handleVisibility);
      window.removeEventListener("focus", refreshCount);
    };
  }, [refreshCount]);

  useEffect(() => {
    if (open) refreshItems();
  }, [open, refreshItems]);

  if (!user) return null;

  async function openNotification(notification: NotificationItem) {
    const href = notificationThreadHref(notification.data) ?? "/notifications";
    if (!notification.read) {
      setItems((prev) => prev.map((item) => item.id === notification.id ? { ...item, read: true } : item));
      setCount((prev) => Math.max(0, prev - 1));
      markNotificationRead(notification.id).catch(() => refreshItems());
    }
    setOpen(false);
    router.push(href);
  }

  async function handleMarkAllRead() {
    setMarkingAll(true);
    try {
      await markAllNotificationsRead();
      setItems((prev) => prev.map((item) => ({ ...item, read: true })));
      setCount(0);
      setConfirmOpen(false);
    } finally {
      setMarkingAll(false);
    }
  }

  return (
    <>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <button
            type="button"
            aria-label={`Thông báo${count > 0 ? `, ${count} chưa đọc` : ""}`}
            className="relative rounded-full p-2 text-foreground/60 transition hover:bg-foreground/5 hover:text-foreground"
          >
            <Bell className="h-4 w-4" />
            {count > 0 && (
              <span className="absolute right-0 top-0 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
                {count > 99 ? "99+" : count}
              </span>
            )}
          </button>
        </PopoverTrigger>
        <PopoverContent align="end" className="w-[min(92vw,380px)] overflow-hidden rounded-2xl p-0 shadow-xl">
          <div className="flex items-center justify-between border-b border-foreground/10 px-4 py-3">
            <div>
              <p className="font-display text-lg">Thông báo</p>
              <p className="text-xs text-muted-foreground">{count} thông báo chưa đọc</p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="rounded-full"
              disabled={count === 0 || markingAll}
              onClick={() => setConfirmOpen(true)}
            >
              <CheckCheck className="h-4 w-4" />
              Đã đọc
            </Button>
          </div>

          <div className="max-h-[420px] overflow-y-auto py-1">
            {loading ? (
              <div className="flex items-center justify-center gap-2 px-4 py-10 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                Đang tải...
              </div>
            ) : items.length === 0 ? (
              <div className="px-4 py-10 text-center text-sm text-muted-foreground">
                Chưa có thông báo mới.
              </div>
            ) : (
              items.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  className="flex w-full gap-3 px-4 py-3 text-left transition hover:bg-foreground/5"
                  onClick={() => openNotification(notification)}
                >
                  <span
                    className={cn(
                      "mt-1 h-2.5 w-2.5 shrink-0 rounded-full",
                      notification.read ? "bg-foreground/20" : "bg-sky-500",
                    )}
                  />
                  <span className="min-w-0 flex-1">
                    <span className={cn("block text-sm", notification.read ? "font-medium" : "font-semibold")}>
                      {notification.title}
                    </span>
                    {notification.body && (
                      <span className="mt-0.5 line-clamp-2 block text-xs text-muted-foreground">
                        {notification.body}
                      </span>
                    )}
                    <span className="mt-1 block text-[11px] text-muted-foreground">
                      {timeAgo(notification.createdAt)}
                    </span>
                  </span>
                </button>
              ))
            )}
          </div>

          <button
            type="button"
            className="w-full border-t border-foreground/10 px-4 py-3 text-center text-sm font-medium transition hover:bg-foreground/5"
            onClick={() => {
              setOpen(false);
              router.push("/notifications");
            }}
          >
            Xem tất cả thông báo
          </button>
        </PopoverContent>
      </Popover>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Đánh dấu tất cả đã đọc?</AlertDialogTitle>
            <AlertDialogDescription>
              Toàn bộ thông báo chưa đọc sẽ được chuyển sang trạng thái đã đọc.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={handleMarkAllRead} disabled={markingAll}>
              {markingAll ? "Đang xử lý..." : "Xác nhận"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
