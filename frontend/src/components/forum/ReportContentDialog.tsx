"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { createContentFlag } from "@/lib/api/moderation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

const REASONS = [
  "Spam hoặc quảng cáo",
  "Nội dung không phù hợp",
  "Thông tin sai lệch",
  "Vi phạm bản quyền",
  "Khác",
];

interface ReportContentDialogProps {
  targetType: "post" | "thread";
  targetId: string;
}

export function ReportContentDialog({ targetType, targetId }: ReportContentDialogProps) {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState(REASONS[0]);
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  if (!user || !can(user, "forum.flag:create")) {
    return null;
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setMessage(null);
    try {
      await createContentFlag({ targetType, targetId, reason, note: note || undefined });
      setMessage("Đã gửi báo cáo. Cảm ơn bạn!");
      setNote("");
      setTimeout(() => setOpen(false), 1200);
    } catch (err) {
      setMessage(err instanceof ApiError ? err.message : "Không gửi được báo cáo");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <button
        type="button"
        className="text-xs font-medium text-slate-500 hover:text-red-600"
        onClick={() => setOpen(true)}
      >
        Báo cáo
      </button>
      {open && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <form onSubmit={handleSubmit} className="card w-full max-w-md space-y-4 p-5">
            <h3 className="text-lg font-semibold text-slate-900">Báo cáo nội dung</h3>
            <label className="block text-sm">
              <span className="mb-1 block text-slate-600">Lý do</span>
              <select
                className="input-field"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              >
                {REASONS.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
            <label className="block text-sm">
              <span className="mb-1 block text-slate-600">Ghi chú (tuỳ chọn)</span>
              <textarea
                className="input-field min-h-[80px]"
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
            </label>
            {message && <p className="text-sm text-slate-600">{message}</p>}
            <div className="flex justify-end gap-2">
              <button type="button" className="btn-secondary" onClick={() => setOpen(false)}>
                Hủy
              </button>
              <button type="submit" className="btn-primary" disabled={submitting}>
                {submitting ? "Đang gửi..." : "Gửi báo cáo"}
              </button>
            </div>
          </form>
        </div>
      )}
    </>
  );
}
