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
    <main className="relative flex min-h-screen flex-col items-center justify-center px-6 text-center noise-overlay">
      <span className="font-mono text-sm uppercase tracking-widest text-muted-foreground">
        Đã có lỗi xảy ra
      </span>
      <h1 className="mt-4 font-display text-[clamp(2.5rem,9vw,5rem)] leading-none tracking-tight">
        Trang gặp sự cố
      </h1>
      <p className="mt-4 max-w-md text-muted-foreground">
        Xin lỗi vì sự bất tiện. Bạn có thể thử lại hoặc quay về trang chủ. Nếu lỗi vẫn tiếp diễn,
        vui lòng báo cho cộng đồng Fuexam.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <Button
          size="lg"
          className="rounded-full bg-foreground px-8 text-background hover:bg-foreground/90"
          onClick={reset}
        >
          Thử lại
        </Button>
        <Button asChild size="lg" variant="outline" className="rounded-full border-foreground/20 px-8 hover:bg-foreground/5">
          <Link href="/">Về trang chủ</Link>
        </Button>
      </div>
    </main>
  );
}
