import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

type Accent = "default" | "success" | "warning" | "danger";

const ACCENT_CLASS: Record<Accent, string> = {
  default: "text-foreground",
  success: "text-emerald-500",
  warning: "text-amber-500",
  danger: "text-red-500",
};

export function StatCard({
  label,
  value,
  hint,
  accent = "default",
}: {
  label: string;
  value: number | string;
  hint?: string;
  accent?: Accent;
}) {
  return (
    <Card>
      <CardContent className="p-5">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className={cn("mt-2 text-3xl font-bold", ACCENT_CLASS[accent])}>{value}</p>
        {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
      </CardContent>
    </Card>
  );
}
