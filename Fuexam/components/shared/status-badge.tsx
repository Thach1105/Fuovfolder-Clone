import { cn } from "@/lib/utils";

interface StatusBadgeProps {
  status: string;
  config: Record<string, { label: string; className: string }>;
  fallbackLabel?: string;
  className?: string;
}

export function StatusBadge({ status, config, fallbackLabel, className }: StatusBadgeProps) {
  const entry = config[status];
  const label = entry?.label ?? fallbackLabel ?? status;
  const colorClass = entry?.className ?? "bg-muted text-muted-foreground";
  return (
    <span className={cn("inline-block rounded-full px-2 py-0.5 text-xs font-medium", colorClass, className)}>
      {label}
    </span>
  );
}
