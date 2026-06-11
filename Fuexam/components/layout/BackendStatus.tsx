"use client";

import { useEffect, useState } from "react";
import { API_BASE, checkHealth } from "@/lib/api/client";

export function BackendStatus() {
  const [status, setStatus] = useState<"checking" | "ok" | "error">("checking");

  useEffect(() => {
    checkHealth()
      .then(() => setStatus("ok"))
      .catch(() => setStatus("error"));
  }, []);

  const colors = {
    checking: "bg-amber-50 text-amber-800 border-amber-200",
    ok: "bg-emerald-50 text-emerald-800 border-emerald-200",
    error: "bg-red-50 text-red-800 border-red-200",
  };

  const labels = {
    checking: "Đang kiểm tra backend...",
    ok: `Backend OK (${API_BASE})`,
    error: `Backend không phản hồi (${API_BASE})`,
  };

  return (
    <div className={`rounded-lg border px-3 py-2 text-xs ${colors[status]}`}>
      {labels[status]}
    </div>
  );
}
