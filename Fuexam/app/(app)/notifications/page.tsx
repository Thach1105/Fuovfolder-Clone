"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { ErrorBanner } from "@/components/ui/error-banner";
import { LoadingState } from "@/components/ui/loading-state";
import { AuthGuard } from "@/components/shared/auth-guard";
import { PaginationBar } from "@/components/shared/pagination-bar";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useAsyncAction } from "@/hooks/use-async-action";
import { usePagination } from "@/hooks/use-pagination";
import { useSubmit } from "@/hooks/use-submit";
import {
  type NotificationItem,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  NOTIFICATION_POLL_MS,
  notificationThreadHref,
} from "@/lib/api/notifications";
import { formatDateTime } from "@/lib/format-datetime";

import { DEFAULT_PAGE_SIZE } from "@/lib/constants/pagination";

export default function NotificationsPage() {
  const { user } = useAuth();
  const [items, setItems] = useState<NotificationItem[]>([]);
  const pagination = usePagination();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const { loading, error, run } = useAsyncAction("Không tải được thông báo");
  const { error: actionError, submit } = useSubmit("Không thể đánh dấu đã đọc");

  const load = useCallback(() => {
    if (!user) return;
    run(async () => {
      const result = await listNotifications({
        unreadOnly,
        page: pagination.page,
        size: DEFAULT_PAGE_SIZE,
      });
      setItems(result.items);
      pagination.updateFromResponse(result);
    });
  }, [user, pagination.page, unreadOnly, run]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!user) return;
    let timer = window.setInterval(load, NOTIFICATION_POLL_MS);

    function handleVisibility() {
      if (document.hidden) {
        window.clearInterval(timer);
      } else {
        load();
        timer = window.setInterval(load, NOTIFICATION_POLL_MS);
      }
    }

    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [user, load]);

  function handleMarkAllRead() {
    submit(async () => {
      await markAllNotificationsRead();
      load();
    });
  }

  function handleMarkRead(notification: NotificationItem) {
    if (notification.read) return;
    submit(async () => {
      await markNotificationRead(notification.id);
      setItems((current) =>
        current.map((item) =>
          item.id === notification.id ? { ...item, read: true } : item,
        ),
      );
    });
  }

  return (
    <AuthGuard>
    <div className="space-y-5">
      <PromoBanner />

      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Thông báo</h1>
          <p className="mt-1 text-sm text-slate-500">
            Cập nhật từ các chủ đề bạn đang theo dõi.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className={!unreadOnly ? "btn-primary text-xs" : "btn-secondary text-xs"}
            onClick={() => {
              pagination.setPage(0);
              setUnreadOnly(false);
            }}
          >
            Tất cả
          </button>
          <button
            type="button"
            className={unreadOnly ? "btn-primary text-xs" : "btn-secondary text-xs"}
            onClick={() => {
              pagination.setPage(0);
              setUnreadOnly(true);
            }}
          >
            Chưa đọc
          </button>
          <button type="button" className="btn-secondary text-xs" onClick={handleMarkAllRead}>
            Đánh dấu tất cả đã đọc
          </button>
          <Link href="/settings/notifications" className="btn-secondary text-xs">
            Cài đặt
          </Link>
        </div>
      </div>

      <div className="card overflow-hidden">
        <ErrorBanner message={error ?? actionError} />
        {loading ? (
          <LoadingState className="px-4 py-8" />
        ) : items.length === 0 ? (
          <p className="px-4 py-10 text-center text-sm text-slate-500">
            {unreadOnly ? "Không có thông báo chưa đọc." : "Chưa có thông báo."}
          </p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {items.map((notification) => {
              const href = notificationThreadHref(notification.data);
              return (
                <li
                  key={notification.id}
                  className={
                    notification.read
                      ? "bg-white px-4 py-4"
                      : "bg-fuo-50/40 px-4 py-4"
                  }
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="font-medium text-slate-900">{notification.title}</p>
                      {notification.body && (
                        <p className="mt-1 text-sm text-slate-600">{notification.body}</p>
                      )}
                      <p className="mt-2 text-xs text-slate-400">
                        {formatDateTime(notification.createdAt)}
                      </p>
                    </div>
                    <div className="flex shrink-0 gap-2">
                      {href && (
                        <Link href={href} className="btn-secondary text-xs">
                          Xem chủ đề
                        </Link>
                      )}
                      {!notification.read && (
                        <button
                          type="button"
                          className="btn-secondary text-xs"
                          onClick={() => handleMarkRead(notification)}
                        >
                          Đã đọc
                        </button>
                      )}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}

        <PaginationBar
          page={pagination.page}
          totalPages={pagination.totalPages}
          onPageChange={pagination.setPage}
        />
      </div>
    </div>
    </AuthGuard>
  );
}
