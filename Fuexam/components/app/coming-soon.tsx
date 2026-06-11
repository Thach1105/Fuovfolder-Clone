import Link from "next/link";
import { Button } from "@/components/ui/button";

export function ComingSoon({ title, note }: { title: string; note?: string }) {
  return (
    <div className="mx-auto max-w-lg rounded-2xl border border-foreground/10 bg-background/60 p-10 text-center backdrop-blur">
      <p className="app-eyebrow">Sắp ra mắt</p>
      <h1 className="mt-2 font-display text-4xl">{title}</h1>
      <p className="mt-3 text-sm text-muted-foreground">
        {note ?? "Tính năng này đang được hoàn thiện trên giao diện mới."}
      </p>
      <Button asChild className="mt-6 rounded-full bg-foreground text-background hover:bg-foreground/90">
        <Link href="/suoc">Khám phá Source</Link>
      </Button>
    </div>
  );
}