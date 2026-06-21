"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { formatPoints } from "@/lib/format-points";

export type StatusActionTarget = "in_progress" | "completed" | "cancelled";

const COPY: Record<StatusActionTarget, { title: string; description: string; confirm: string }> = {
  in_progress: {
    title: "Bắt đầu xử lý đơn",
    description: "Chuyển đơn sang trạng thái đang thực hiện.",
    confirm: "Bắt đầu xử lý",
  },
  completed: {
    title: "Hoàn thành đơn",
    description: "Đánh dấu đơn đã hoàn thành. Hành động này là cuối cùng.",
    confirm: "Hoàn thành",
  },
  cancelled: {
    title: "Hủy đơn",
    description: "Hủy đơn dịch vụ. Hành động này không thể hoàn tác.",
    confirm: "Xác nhận hủy",
  },
};

export function ConfirmStatusDialog({
  open,
  targetStatus,
  totalPoints,
  willRefund,
  onClose,
  onConfirm,
}: {
  open: boolean;
  targetStatus: StatusActionTarget | null;
  totalPoints: number;
  willRefund: boolean;
  onClose: () => void;
  onConfirm: (note: string) => Promise<void>;
}) {
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const copy = targetStatus ? COPY[targetStatus] : null;
  const destructive = targetStatus === "cancelled";

  async function handleConfirm() {
    setSubmitting(true);
    setError(null);
    try {
      await onConfirm(note);
      setNote("");
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Có lỗi xảy ra.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o && !submitting) {
          setNote("");
          setError(null);
          onClose();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{copy?.title}</DialogTitle>
          <DialogDescription>{copy?.description}</DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          {willRefund && (
            <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm text-amber-600 dark:text-amber-400">
              Sẽ hoàn lại {formatPoints(totalPoints)} cho khách hàng.
            </div>
          )}
          {error && (
            <div className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </div>
          )}
          <Textarea
            placeholder="Ghi chú (tùy chọn)"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            Đóng
          </Button>
          <Button
            variant={destructive ? "destructive" : "default"}
            onClick={handleConfirm}
            disabled={submitting}
          >
            {submitting ? "Đang xử lý..." : copy?.confirm}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
