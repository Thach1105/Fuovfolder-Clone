"use client";

import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { ErrorBanner } from "@/components/ui/error-banner";
import { createContentFlag } from "@/lib/api/moderation";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import { useSubmit } from "@/hooks/use-submit";

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
  const { submitting, error, submit } = useSubmit("Không gửi được báo cáo");
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  if (!user || !can(user, "forum.flag:create")) {
    return null;
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSuccessMessage(null);
    const result = await submit(async () => {
      await createContentFlag({ targetType, targetId, reason, note: note || undefined });
      return true;
    });
    if (result) {
      setSuccessMessage("Đã gửi báo cáo. Cảm ơn bạn!");
      setNote("");
      setTimeout(() => setOpen(false), 1200);
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
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-md">
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>Báo cáo nội dung</DialogTitle>
            </DialogHeader>
            <div className="mt-4 space-y-4">
              <label className="block text-sm">
                <span className="mb-1 block text-muted-foreground">Lý do</span>
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
                <span className="mb-1 block text-muted-foreground">Ghi chú (tuỳ chọn)</span>
                <textarea
                  className="input-field min-h-[80px]"
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                />
              </label>
              <ErrorBanner message={error} />
              {successMessage && <p className="text-sm text-muted-foreground">{successMessage}</p>}
            </div>
            <DialogFooter className="mt-4">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? "Đang gửi..." : "Gửi báo cáo"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  );
}
