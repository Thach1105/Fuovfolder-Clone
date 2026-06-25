"use client";

import Link from "next/link";
import { Plus, Wallet } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useAuth } from "@/lib/auth/AuthProvider";
import { getPointsBalance, POINTS_BALANCE_REFRESH_EVENT } from "@/lib/api/points";

export function HeaderBalance() {
  const { user } = useAuth();
  const [balance, setBalance] = useState<number | null>(null);
  const [open, setOpen] = useState(false);

  const refresh = useCallback(async () => {
    if (!user) {
      setBalance(null);
      return;
    }
    try {
      const result = await getPointsBalance();
      setBalance(result.balance);
    } catch {
      setBalance(null);
    }
  }, [user]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    if (!user) return;
    window.addEventListener(POINTS_BALANCE_REFRESH_EVENT, refresh);
    window.addEventListener("focus", refresh);
    return () => {
      window.removeEventListener(POINTS_BALANCE_REFRESH_EVENT, refresh);
      window.removeEventListener("focus", refresh);
    };
  }, [refresh, user]);

  useEffect(() => {
    if (open) refresh();
  }, [open, refresh]);

  if (!user) return null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button
          type="button"
          className="inline-flex items-center gap-1.5 rounded-full border border-foreground/15 px-3 py-1.5 text-sm font-semibold text-foreground/85 transition hover:bg-foreground/5 hover:text-foreground"
          aria-label="Nạp tiền và xem số dư ví"
        >
          <Plus className="h-3.5 w-3.5" />
          <span>Nạp tiền</span>
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(92vw,320px)] rounded-2xl p-0 shadow-xl">
        <div className="border-b border-foreground/10 px-4 py-4">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Ví Fuexam</p>
          <div className="mt-3 flex items-center justify-between gap-3 rounded-xl bg-foreground/5 px-4 py-3">
            <div>
              <p className="text-xs text-muted-foreground">Số dư hiện tại</p>
              <p className="mt-1 font-display text-2xl">
                {balance === null ? "Đang tải..." : balance.toLocaleString("vi-VN")}
              </p>
            </div>
            <Wallet className="h-6 w-6 text-foreground/60" />
          </div>
        </div>
        <div className="space-y-2 p-4">
          <Button asChild className="w-full rounded-full bg-foreground text-background hover:bg-foreground/90" onClick={() => setOpen(false)}>
            <Link href="/deposit">Nạp tiền ngay</Link>
          </Button>
          <Button asChild variant="outline" className="w-full rounded-full" onClick={() => setOpen(false)}>
            <Link href="/me/points">Xem lịch sử ví</Link>
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
