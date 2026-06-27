import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
      <span className="font-mono text-sm uppercase tracking-widest text-muted-foreground">
        Lỗi 404
      </span>
      <h1 className="text-3xl font-semibold tracking-tight">Không tìm thấy trang</h1>
      <p className="max-w-md text-muted-foreground">
        Trang quản trị bạn tìm không tồn tại hoặc đã bị di chuyển.
      </p>
      <Button asChild className="mt-2">
        <Link href="/dashboard">Về bảng điều khiển</Link>
      </Button>
    </main>
  );
}
