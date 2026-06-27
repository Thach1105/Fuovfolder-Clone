"use client";

import { useEffect } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
      <span className="font-mono text-sm uppercase tracking-widest text-muted-foreground">
        Đã có lỗi xảy ra
      </span>
      <h1 className="text-3xl font-semibold tracking-tight">Trang gặp sự cố</h1>
      <p className="max-w-md text-muted-foreground">
        Đã xảy ra lỗi ngoài dự kiến trong trang quản trị. Bạn có thể thử lại hoặc về bảng điều khiển.
      </p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        <Button onClick={reset}>Thử lại</Button>
        <Button asChild variant="outline">
          <Link href="/dashboard">Về bảng điều khiển</Link>
        </Button>
      </div>
    </main>
  );
}
