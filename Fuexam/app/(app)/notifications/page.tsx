"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { PromoBanner } from "@/components/layout/PromoBanner";
import { useAuth } from "@/lib/auth/AuthProvider";
import { ApiError } from "@/lib/api/client";
import {
  type NotificationItem,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  NOTIFICATION_POLL_MS,
  notificationThreadHref,
} from "@/lib/api/notifications";
import { formatDateTime } from "@/lib/format-datetime";

const PAGE_SIZE = 20;

export default function NotificationsPage() {
  const { user, loading: authLoading } = useAuth();
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    setError(null);
    try {
      const result = await listNotifications({
        unreadOnly,
        page,
        size: PAGE_SIZE,
      });
      setItems(result.items);
      setTotalPages(result.totalPages);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được thông báo");
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [user, page, unreadOnly]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!user) return;
    const timer = window.setInterval(load, NOTIFICATION_POLL_MS);
    return () => window.clearInterval(timer);
  }, [user, load]);

  async function handleMarkAllRead() {
    try {
      await markAllNotificationsRead();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể đánh dấu đã đọc");
    }
  }

  async function handleMarkRead(notification: NotificationItem) {
    if (notification.read) return;
    try {
      await markNotificationRead(notification.id);
      setItems((current) =>
        current.map((item) =>
          item.id === notification.id ? { ...item, read: true } : item,
        ),
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể đánh dấu đã đọc");
    }
  }

  if (authLoading) {
    return <p className="text-sm text-slate-500">Đang kiểm tra đăng nhập...</p>;
  }

  if (!user) {
    return (
      <div className="card p-6 text-sm text-slate-600">
        <p>Bạn cần đăng nhập để xem thông báo.</p>
        <Link href="/login" className="mt-3 inline-block font-medium text-fuo-600 hover:underline">
          Đăng nhập
        </Link>
      </div>
    );
  }

  return (
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
              setPage(0);
              setUnreadOnly(false);
            }}
          >
            Tất cả
          </button>
          <button
            type="button"
            className={unreadOnly ? "btn-primary text-xs" : "btn-secondary text-xs"}
            onClick={() => {
              setPage(0);
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
        {error && (
          <p className="border-b border-red-100 bg-red-50 px-4 py-2 text-sm text-red-800">
            {error}
          </p>
        )}
        {loading ? (
          <p className="px-4 py-8 text-sm text-slate-500">Đang tải...</p>
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

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-3 border-t border-slate-100 py-3">
            <button
              type="button"
              className="btn-secondary"
              disabled={page === 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              ← Trước
            </button>
            <span className="text-sm text-slate-600">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              className="btn-secondary"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((value) => value + 1)}
            >
              Sau →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
