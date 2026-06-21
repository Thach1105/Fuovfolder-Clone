"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";

function CancelInner() {
  const searchParams = useSearchParams();
  const orderCode = searchParams.get("orderCode");

  return (
    <div className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center px-4 py-12">
      <div className="w-full text-center">
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-amber-500/10 text-3xl">
          ⚠️
        </div>
        <h1 className="mb-2 text-2xl font-bold text-slate-100">Đã hủy thanh toán</h1>
        <p className="mb-1 text-sm text-slate-400">
          Giao dịch chưa hoàn tất. Đơn hàng của bạn vẫn ở trạng thái chờ.
        </p>
        {orderCode && (
          <p className="mb-6 text-xs text-slate-500">Mã đơn: {orderCode}</p>
        )}
        <div className="flex flex-col gap-2">
          <Link
            href="/"
            className="inline-block rounded-lg bg-slate-700 px-6 py-2.5 text-sm font-medium text-white hover:bg-slate-600"
          >
            Về trang chủ
          </Link>
          <Link
            href="/deposit"
            className="inline-block rounded-lg border border-slate-700 px-6 py-2.5 text-sm font-medium text-slate-300 hover:bg-slate-800"
          >
            Nạp lại
          </Link>
        </div>
      </div>
    </div>
  );
}

export default function PaymentCancelPage() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-slate-400">Đang tải...</div>}>
      <CancelInner />
    </Suspense>
  );
}
