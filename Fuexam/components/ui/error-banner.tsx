import { cn } from "@/lib/utils";

interface ErrorBannerProps {
  message: string | null | undefined;
  className?: string;
}

export function ErrorBanner({ message, className }: ErrorBannerProps) {
  if (!message) return null;
  return (
    <div
      className={cn(
        "rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive",
        className,
      )}
    >
      {message}
    </div>
  );
}
