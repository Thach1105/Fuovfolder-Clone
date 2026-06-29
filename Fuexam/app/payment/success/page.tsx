"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ApiError } from "@/lib/api/client";
import { ErrorBanner } from "@/components/ui/error-banner";
import { getPaymentStatus, type PaymentStatusResponse } from "@/lib/api/payment";
import { requestPointsBalanceRefresh } from "@/lib/api/points";
import { useAuth } from "@/lib/auth/AuthProvider";

function SuccessInner() {
  const searchParams = useSearchParams();
  const orderCode = searchParams.get("orderCode");
  const { refreshUser } = useAuth();
  const refreshedAfterPaid = useRef(false);
  const [status, setStatus] = useState<PaymentStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!orderCode) {
      setError("Thiếu mã đơn hàng (orderCode).");
      setLoading(false);
      return;
    }
    const poll = () => {
      getPaymentStatus(orderCode)
        .then((data) => {
          setStatus(data);
          if (data.status === "pending") {
            setTimeout(poll, 2000);
            return;
          }
          if (data.status === "paid" && !refreshedAfterPaid.current) {
            refreshedAfterPaid.current = true;
            requestPointsBalanceRefresh();
            void refreshUser();
          }
          setLoading(false);
        })
        .catch((err) => {
          setLoading(false);
          setError(err instanceof ApiError ? err.message : "Không kiểm tra được trạng thái thanh toán.");
        });
    };
    poll();
  }, [orderCode, refreshUser]);

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center px-4 py-12">
      {loading && (
        <div className="text-center">
          <div className="mx-auto mb-4 h-10 w-10 animate-spin rounded-full border-4 border-slate-700 border-t-emerald-500" />
          <p className="text-sm text-slate-400">Đang xác nhận thanh toán...</p>
        </div>
      )}

      <ErrorBanner message={error} className="w-full text-center" />

      {!loading && status && status.status === "paid" && (
        <div className="w-full text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-500/10 text-3xl">
            ✓
          </div>
          <h1 className="mb-2 text-2xl font-bold text-slate-100">Thanh toán thành công</h1>
          <p className="mb-1 text-sm text-slate-400">
            Số tiền: {status.amountCents.toLocaleString("vi-VN")} {status.currency}
          </p>
          <p className="mb-6 text-lg font-semibold text-emerald-400">
            +{status.pointsEarned} điểm
          </p>
          <Link
            href="/"
            className="inline-block rounded-lg bg-emerald-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-emerald-500"
          >
            Về trang chủ
          </Link>
        </div>
      )}

      {!loading && status && status.status === "failed" && (
        <div className="w-full text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-500/10 text-3xl">
            ×
          </div>
          <h1 className="mb-2 text-2xl font-bold text-slate-100">Thanh toán thất bại</h1>
          <Link
            href="/deposit"
            className="mt-4 inline-block rounded-lg bg-slate-700 px-6 py-2.5 text-sm font-medium text-white hover:bg-slate-600"
          >
            Thử lại
          </Link>
        </div>
      )}
    </div>
  );
}

export default function PaymentSuccessPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-slate-400">Đang tải...</div>}>
      <SuccessInner />
    </Suspense>
  );
}
