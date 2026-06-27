"use client";

import { useEffect, useState } from "react";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { AdminUserSummary } from "@/types/api";

export function DeleteUserDialog({
  user,
  onConfirm,
  onOpenChange,
}: {
  user: AdminUserSummary | null;
  onConfirm: (user: AdminUserSummary) => Promise<void> | void;
  onOpenChange: (open: boolean) => void;
}) {
  const [confirmText, setConfirmText] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // Reset the typed confirmation whenever a different user (or none) is targeted.
  useEffect(() => {
    setConfirmText("");
  }, [user?.id]);

  const matches = user != null && confirmText.trim() === user.username;

  async function handleConfirm() {
    if (!user || !matches || submitting) return;
    setSubmitting(true);
    try {
      await onConfirm(user);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AlertDialog open={user != null} onOpenChange={(o) => !submitting && onOpenChange(o)}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Xóa tài khoản @{user?.username}?</AlertDialogTitle>
          <AlertDialogDescription>
            Tài khoản sẽ bị vô hiệu hóa (soft delete): không đăng nhập được, mọi phiên bị thu
            hồi. Bài viết và dữ liệu liên quan vẫn được giữ lại. Hành động này dùng cho tài
            khoản rác và rất khó hoàn tác.
          </AlertDialogDescription>
        </AlertDialogHeader>

        <div className="space-y-2">
          <Label htmlFor="confirm-username" className="text-sm">
            Gõ lại{" "}
            <span className="font-mono font-semibold text-foreground">{user?.username}</span>{" "}
            để xác nhận
          </Label>
          <Input
            id="confirm-username"
            autoComplete="off"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder={user?.username}
          />
        </div>

        <AlertDialogFooter>
          <AlertDialogCancel disabled={submitting}>Hủy</AlertDialogCancel>
          <Button variant="destructive" disabled={!matches || submitting} onClick={handleConfirm}>
            {submitting ? "Đang xóa..." : "Xóa tài khoản"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
