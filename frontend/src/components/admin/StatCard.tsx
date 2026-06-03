interface StatCardProps {
  label: string;
  value: number | string;
  hint?: string;
  accent?: "default" | "success" | "warning" | "danger";
}

const ACCENT_STYLES = {
  default: "border-slate-700 bg-slate-950 text-white",
  success: "border-emerald-800/60 bg-emerald-950/40 text-emerald-100",
  warning: "border-amber-800/60 bg-amber-950/40 text-amber-100",
  danger: "border-red-800/60 bg-red-950/40 text-red-100",
};

export function StatCard({ label, value, hint, accent = "default" }: StatCardProps) {
  return (
    <div className={`rounded-xl border p-5 ${ACCENT_STYLES[accent]}`}>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-2 text-3xl font-bold tabular-nums">{value}</p>
      {hint && <p className="mt-2 text-xs text-slate-500">{hint}</p>}
    </div>
  );
}
