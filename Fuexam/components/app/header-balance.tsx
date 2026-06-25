"use client";

import Link from "next/link";
import { Wallet } from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { getPointsBalance } from "@/lib/api/points";

/**
 * Hiển thị số dư Fuexam Point ngay trên header, cạnh avatar. Bấm vào để tới
 * trang nạp tiền. Dùng token theme (foreground/background) cho khớp header mới.
 */
export function HeaderBalance() {
  const { user } = useAuth();
  const [balance, setBalance] = useState<number | null>(null);

  useEffect(() => {
    if (!user) {
      setBalance(null);
      return;
    }
    let active = true;
    getPointsBalance()
      .then((r) => {
        if (active) setBalance(r.balance);
      })
      .catch(() => {
        if (active) setBalance(null);
      });
    return () => {
      active = false;
    };
  }, [user]);

  if (!user) return null;

  return (
    <Link
      href="/deposit"
      title="Số dư Fuexam Point — bấm để nạp thêm"
      className="inline-flex items-center gap-1.5 rounded-full border border-foreground/15 px-3 py-1.5 text-sm font-medium text-foreground/80 transition hover:bg-foreground/5 hover:text-foreground"
    >
      <Wallet className="h-3.5 w-3.5" />
      <span className="tabular-nums">
        {balance === null ? "…" : balance.toLocaleString("vi-VN")}
      </span>
    </Link>
  );
}
