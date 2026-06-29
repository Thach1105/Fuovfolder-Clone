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
import { ErrorBanner } from "@/components/ui/error-banner";
import { useSubmit } from "@/hooks/use-submit";
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
  const { submitting, error, submit, clearError } = useSubmit("Cập nhật thất bại");

  if (!targetStatus) return null;

  const copy = ACTION_COPY[targetStatus];

  async function handleConfirm() {
    const result = await submit(async () => {
      await onConfirm(note);
      return true;
    });
    if (result) {
      setNote("");
      onClose();
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen && !submitting) {
      setNote("");
      clearError();
      onClose();
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent showCloseButton={false} className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>
            {targetStatus === "cancelled" ? (
              <>
                Đơn sẽ chuyển sang{" "}
                <strong>{REQUEST_STATUS_LABELS.cancelled}</strong>.
                {willRefund ? (
                  <> User sẽ được hoàn <strong>{formatPoints(totalPoints)}</strong>.</>
                ) : (
                  <> Không hoàn Fuexam Point (đã hoàn trước đó hoặc không có thanh toán).</>
                )}
              </>
            ) : (
              copy.description
            )}
          </DialogDescription>
        </DialogHeader>

        <div>
          <label className="block text-xs text-muted-foreground">Ghi chú nội bộ (tùy chọn)</label>
          <input
            className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            placeholder="Ghi chú cho lịch sử đơn"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            disabled={submitting}
          />
        </div>

        <ErrorBanner message={error} />

        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)} disabled={submitting}>
            Đóng
          </Button>
          <Button
            variant={copy.destructive ? "destructive" : "default"}
            onClick={handleConfirm}
            disabled={submitting}
          >
            {submitting ? "Đang xử lý..." : copy.confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
