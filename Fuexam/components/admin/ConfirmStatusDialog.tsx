"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api/client";
import { REQUEST_STATUS_LABELS, formatPoints } from "@/lib/format-points";

export type StatusActionTarget = "in_progress" | "completed" | "cancelled";

const ACTION_COPY: Record<
  StatusActionTarget,
  { title: string; description: string; confirmLabel: string; destructive?: boolean }
> = {
  in_progress: {
    title: "Bắt đầu xử lý đơn",
    description: "Đơn sẽ chuyển sang trạng thái Đang thực hiện. Fuexam Point của user không thay đổi.",
    confirmLabel: "Bắt đầu xử lý",
  },
  completed: {
    title: "Hoàn thành đơn",
    description: "Xác nhận dịch vụ đã hoàn tất. Đơn sẽ không thể đổi trạng thái sau bước này.",
    confirmLabel: "Hoàn thành",
  },
  cancelled: {
    title: "Hủy đơn dịch vụ",
    description: "",
    confirmLabel: "Hủy đơn",
    destructive: true,
  },
};

type Props = {
  open: boolean;
  targetStatus: StatusActionTarget | null;
  totalPoints: number;
  willRefund: boolean;
  onClose: () => void;
  onConfirm: (note: string) => Promise<void>;
};

export function ConfirmStatusDialog({
  open,
  targetStatus,
  totalPoints,
  willRefund,
  onClose,
  onConfirm,
}: Props) {
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open || !targetStatus) return null;

  const copy = ACTION_COPY[targetStatus];

  async function handleConfirm() {
    setSubmitting(true);
    setError(null);
    try {
      await onConfirm(note);
      setNote("");
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Cập nhật thất bại");
    } finally {
      setSubmitting(false);
    }
  }

  function handleClose() {
    if (submitting) return;
    setNote("");
    setError(null);
    onClose();
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="status-dialog-title"
    >
      <div className="w-full max-w-md rounded-xl border border-slate-700 bg-slate-900 p-5 shadow-xl">
        <h2 id="status-dialog-title" className="text-lg font-semibold text-white">
          {copy.title}
        </h2>
        <p className="mt-2 text-sm text-slate-400">
          {targetStatus === "cancelled" ? (
            <>
              Đơn sẽ chuyển sang{" "}
              <strong className="text-slate-200">{REQUEST_STATUS_LABELS.cancelled}</strong>.
              {willRefund ? (
                <>
                  {" "}
                  User sẽ được hoàn <strong className="text-amber-300">{formatPoints(totalPoints)}</strong>.
                </>
              ) : (
                <> Không hoàn Fuexam Point (đã hoàn trước đó hoặc không có thanh toán).</>
              )}
            </>
          ) : (
            copy.description
          )}
        </p>
        <label className="mt-4 block text-xs text-slate-500">Ghi chú nội bộ (tùy chọn)</label>
        <input
          className="mt-1 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-white"
          placeholder="Ghi chú cho lịch sử đơn"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          disabled={submitting}
        />
        {error && (
          <p className="mt-3 text-sm text-red-300">{error}</p>
        )}
        <div className="mt-5 flex justify-end gap-2">
          <button
            type="button"
            className="rounded-lg border border-slate-600 px-4 py-2 text-sm text-slate-300 hover:bg-slate-800"
            onClick={handleClose}
            disabled={submitting}
          >
            Đóng
          </button>
          <button
            type="button"
            className={`rounded-lg px-4 py-2 text-sm font-semibold disabled:opacity-50 ${
              copy.destructive
                ? "bg-red-600 text-white hover:bg-red-500"
                : "bg-amber-500 text-slate-950 hover:bg-amber-400"
            }`}
            onClick={() => handleConfirm()}
            disabled={submitting}
          >
            {submitting ? "Đang xử lý..." : copy.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
