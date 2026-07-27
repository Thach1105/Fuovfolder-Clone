"use client";

import { useState } from "react";
import { FileUp, Trophy } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { checkScore, type CheckScoreResult } from "@/lib/api/check-score";
import { ApiError } from "@/lib/api/client";

export default function CheckScorePage() {
  const [file, setFile] = useState<File | null>(null);
  const [voucherCode, setVoucherCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<CheckScoreResult | null>(null);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) return toast.error("Vui lòng chọn file .dat.");
    setLoading(true);
    setResult(null);
    try {
      setResult(await checkScore(file, voucherCode));
      toast.success("Chấm điểm thành công.");
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : "Chấm điểm thất bại.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      <section className="rounded-xl border border-border/70 bg-card p-6">
        <div className="mb-5 flex items-center gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <FileUp className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Check điểm</h1>
            <p className="text-sm text-muted-foreground">Upload file .dat để xem điểm bài làm.</p>
          </div>
        </div>

        <form onSubmit={submit} className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_220px_144px]">
          <Input
            type="file"
            accept=".dat"
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null);
              setResult(null);
            }}
          />
          <Input
            value={voucherCode}
            onChange={(event) => setVoucherCode(event.target.value)}
            placeholder="Voucher"
          />
          <Button type="submit" disabled={!file || loading} className="sm:w-36">
            {loading ? "Đang chấm..." : "Chấm điểm"}
          </Button>
        </form>
        <p className="mt-3 text-sm text-muted-foreground">Phí chấm: 29,000 points.</p>
      </section>

      {result && (
        <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <div className="rounded-xl border border-border bg-card p-5 sm:col-span-2">
            <div className="mb-2 flex items-center gap-2 text-primary">
              <Trophy className="h-5 w-5" />
              <span className="text-sm font-medium">Điểm</span>
            </div>
            <p className="text-5xl font-semibold">{result.score}</p>
          </div>
          <ResultBox label="Số câu đúng" value={`${result.correctAnswers}/${result.totalQuestions}`} />
          <ResultBox label="Môn" value={result.subject} mono />
          <ResultBox
            label="Tính phí"
            value={`${result.chargedPoints.toLocaleString("vi-VN")} points`}
          />
          <ResultBox label="Giảm" value={`${result.discountPoints.toLocaleString("vi-VN")} points`} />
        </section>
      )}
    </div>
  );
}

function ResultBox({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="rounded-xl border border-border bg-card p-5">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className={mono ? "mt-2 break-all font-mono text-sm" : "mt-2 text-2xl font-semibold"}>{value}</p>
    </div>
  );
}
