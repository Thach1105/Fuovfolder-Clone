import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="relative flex min-h-screen flex-col items-center justify-center px-6 text-center noise-overlay">
      <span className="font-mono text-sm uppercase tracking-widest text-muted-foreground">
        Lỗi 404
      </span>
      <h1 className="mt-4 font-display text-[clamp(3rem,12vw,7rem)] leading-none tracking-tight">
        Không tìm thấy trang
      </h1>
      <p className="mt-4 max-w-md text-muted-foreground">
        Trang bạn tìm không tồn tại hoặc đã bị di chuyển. Kiểm tra lại đường dẫn hoặc quay về
        trang chủ.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <Button asChild size="lg" className="rounded-full bg-foreground px-8 text-background hover:bg-foreground/90">
          <Link href="/">Về trang chủ</Link>
        </Button>
        <Button asChild size="lg" variant="outline" className="rounded-full border-foreground/20 px-8 hover:bg-foreground/5">
          <Link href="/forums">Vào diễn đàn</Link>
        </Button>
      </div>
    </main>
  );
}
