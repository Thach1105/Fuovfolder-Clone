import { cn } from "@/lib/utils";

interface LoadingStateProps {
  message?: string;
  className?: string;
}

export function LoadingState({
  message = "Đang tải...",
  className,
}: LoadingStateProps) {
  return (
    <p className={cn("text-sm text-muted-foreground", className)}>{message}</p>
  );
}
