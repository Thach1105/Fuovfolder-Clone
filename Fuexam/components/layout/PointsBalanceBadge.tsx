"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/AuthProvider";
import { formatPoints } from "@/lib/format-points";
import { getPointsBalance } from "@/lib/api/points";

export function PointsBalanceBadge() {
  const { user } = useAuth();
  const [balance, setBalance] = useState<number | null>(null);

  useEffect(() => {
    if (!user) {
      setBalance(null);
      return;
    }
    getPointsBalance()
      .then((r) => setBalance(r.balance))
      .catch(() => setBalance(null));
  }, [user]);

  if (!user || balance === null) return null;

  return (
    <span
      className="hidden items-center gap-1 rounded-lg border border-fuo-200 bg-fuo-50 px-2.5 py-1 text-xs font-semibold text-fuo-800 lg:inline-flex"
      title="Số dư Fuexam Point"
    >
      <WalletIcon />
      {formatPoints(balance)}
    </span>
  );
}

function WalletIcon() {
  return (
    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1" />
      <path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4" />
    </svg>
  );
}
