"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import {
  getNotificationPreferences,
  updateNotificationPreferences,
  type NotificationPreferenceItem,
} from "@/lib/api/notifications";
import {
  getVapidPublicKey,
  subscribeToPush,
  unsubscribeFromPush,
} from "@/lib/push-notifications";

const CHANNEL_LABELS: Record<string, string> = {
  web: "Trên web",
  email: "Email",
  push: "Push trình duyệt",
};

const TYPE_LABELS: Record<string, string> = {
  "thread.reply": "Trả lời trong chủ đề đang theo dõi",
};

export function NotificationSettingsForm() {
  const [preferences, setPreferences] = useState<NotificationPreferenceItem[]>([]);
  const [pushAvailable, setPushAvailable] = useState(false);
  const [pushSubscribed, setPushSubscribed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [prefs, vapid] = await Promise.all([
        getNotificationPreferences(),
        getVapidPublicKey(),
      ]);
      setPreferences(prefs.preferences);
      setPushAvailable(vapid.enabled && Boolean(vapid.publicKey));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được cài đặt");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleToggle(type: string, channel: string, enabled: boolean) {
    const next = preferences.map((item) =>
      item.type === type && item.channel === channel ? { ...item, enabled } : item,
    );
    setPreferences(next);
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      const result = await updateNotificationPreferences({ preferences: next });
      setPreferences(result.preferences);
      setMessage("Đã lưu cài đặt thông báo.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không lưu được cài đặt");
      await load();
    } finally {
      setSaving(false);
    }
  }

  async function handlePushSubscribe() {
    setError(null);
    setMessage(null);
    try {
      const ok = await subscribeToPush();
      setPushSubscribed(ok);
      setMessage(ok ? "Đã bật push trình duyệt." : "Không thể đăng ký push. Kiểm tra quyền thông báo.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể đăng ký push");
    }
  }

  async function handlePushUnsubscribe() {
    setError(null);
    try {
      await unsubscribeFromPush();
      setPushSubscribed(false);
      setMessage("Đã tắt push trình duyệt.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể hủy push");
    }
  }

  if (loading) {
    return <p className="text-sm text-slate-500">Đang tải cài đặt...</p>;
  }

  return (
    <div className="space-y-6">
      {preferences.map((item) => (
        <label
          key={`${item.type}-${item.channel}`}
          className="flex items-center justify-between gap-4 rounded-lg border border-slate-100 px-4 py-3"
        >
          <div>
            <p className="font-medium text-slate-800">
              {TYPE_LABELS[item.type] ?? item.type}
            </p>
            <p className="text-sm text-slate-500">
              Kênh: {CHANNEL_LABELS[item.channel] ?? item.channel}
            </p>
          </div>
          <input
            type="checkbox"
            className="h-4 w-4 rounded border-slate-300"
            checked={item.enabled}
            disabled={saving || (item.channel === "push" && !pushAvailable)}
            onChange={(event) => handleToggle(item.type, item.channel, event.target.checked)}
          />
        </label>
      ))}

      <div className="rounded-lg border border-slate-100 bg-slate-50 p-4">
        <h3 className="font-medium text-slate-800">Push trình duyệt</h3>
        <p className="mt-1 text-sm text-slate-500">
          Nhận thông báo ngay cả khi không mở tab FUExam. Cần cấu hình VAPID trên server.
        </p>
        {!pushAvailable && (
          <p className="mt-2 text-xs text-amber-700">
            Push chưa được bật trên server (`NOTIFICATION_PUSH_ENABLED` và VAPID keys).
          </p>
        )}
        <div className="mt-3 flex flex-wrap gap-2">
          <button
            type="button"
            className="btn-primary text-xs"
            disabled={!pushAvailable}
            onClick={handlePushSubscribe}
          >
            Đăng ký push
          </button>
          <button type="button" className="btn-secondary text-xs" onClick={handlePushUnsubscribe}>
            Hủy push
          </button>
          {pushSubscribed && (
            <span className="self-center text-xs text-emerald-700">Đã đăng ký thiết bị này</span>
          )}
        </div>
      </div>

      {error && <p className="text-sm text-red-700">{error}</p>}
      {message && <p className="text-sm text-emerald-700">{message}</p>}
    </div>
  );
}
