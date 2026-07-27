"use client";

import { useEffect, useMemo, useState } from "react";
import {
  BookOpenCheck,
  CheckCircle2,
  Coins,
  FileUp,
  Loader2,
  Search,
  TicketPercent,
  Trophy,
  UploadCloud,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  checkScore,
  listCheckScoreSubjects,
  type CheckScoreResult,
} from "@/lib/api/check-score";
import { ApiError } from "@/lib/api/client";
import { type VoucherPreviewResponse, previewVoucher } from "@/lib/api/voucher";

const CHECK_SCORE_PRICE_POINTS = 29000;

export default function CheckScorePage() {
  const [file, setFile] = useState<File | null>(null);
  const [voucherCode, setVoucherCode] = useState("");
  const [voucherPreview, setVoucherPreview] = useState<VoucherPreviewResponse | null>(null);
  const [voucherError, setVoucherError] = useState<string | null>(null);
  const [applyingVoucher, setApplyingVoucher] = useState(false);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<CheckScoreResult | null>(null);
  const [subjects, setSubjects] = useState<string[]>([]);
  const [subjectsLoading, setSubjectsLoading] = useState(true);
  const [subjectSearch, setSubjectSearch] = useState("");

  useEffect(() => {
    listCheckScoreSubjects()
      .then((data) => setSubjects(data.items))
      .catch(() => setSubjects([]))
      .finally(() => setSubjectsLoading(false));
  }, []);

  const filteredSubjects = useMemo(() => {
    const keyword = subjectSearch.trim().toLowerCase();
    if (!keyword) return subjects;
    return subjects.filter((subject) => subject.toLowerCase().includes(keyword));
  }, [subjects, subjectSearch]);

  async function handleApplyVoucher() {
    if (!voucherCode.trim()) return;
    setApplyingVoucher(true);
    setVoucherError(null);
    try {
      const preview = await previewVoucher(voucherCode.trim(), "check_score", CHECK_SCORE_PRICE_POINTS);
      if (preview.valid) {
        setVoucherPreview(preview);
      } else {
        setVoucherPreview(null);
        setVoucherError(preview.message);
      }
    } catch (error) {
      setVoucherPreview(null);
      setVoucherError(error instanceof ApiError ? error.message : "Không thể áp dụng voucher.");
    } finally {
      setApplyingVoucher(false);
    }
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) return toast.error("Vui lòng chọn file .dat.");
    setLoading(true);
    setResult(null);
    try {
      setResult(await checkScore(file, voucherPreview ? voucherCode : undefined));
      toast.success("Chấm điểm thành công.");
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : "Chấm điểm thất bại.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
      <div className="space-y-6">
        <section className="rounded-xl border border-border/70 bg-card p-6 shadow-sm">
          <div className="mb-5 flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <FileUp className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">Check điểm</h1>
              <p className="text-sm text-muted-foreground">Upload file .dat để xem điểm bài làm.</p>
            </div>
          </div>

          <form onSubmit={submit} className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_280px_148px] lg:items-stretch">
            <label
              htmlFor="score-file"
              className="group flex min-h-24 cursor-pointer items-center gap-4 rounded-lg border border-dashed border-border bg-background/70 px-4 py-3 transition hover:border-primary/60 hover:bg-primary/5"
            >
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground transition group-hover:bg-primary/10 group-hover:text-primary">
                {file ? <CheckCircle2 className="h-5 w-5" /> : <UploadCloud className="h-5 w-5" />}
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">
                  {file ? file.name : "Chọn file .dat"}
                </span>
                <span className="mt-1 block text-xs text-muted-foreground">
                  {file ? `${(file.size / 1024).toFixed(1)} KB` : "Bấm để tải file bài làm lên"}
                </span>
              </span>
              <input
                id="score-file"
                type="file"
                accept=".dat"
                className="sr-only"
                onChange={(event) => {
                  setFile(event.target.files?.[0] ?? null);
                  setResult(null);
                }}
              />
            </label>

            <div className="space-y-2 rounded-lg border border-border bg-background/80 px-4 py-3 shadow-sm">
              <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
                <TicketPercent className="h-4 w-4" />
                Voucher
              </div>
              <Input
                value={voucherCode}
                onChange={(event) => {
                  setVoucherCode(event.target.value.toUpperCase());
                  setVoucherPreview(null);
                  setVoucherError(null);
                }}
                placeholder="Nhập mã nếu có"
                className="h-10 border border-border bg-background px-3 shadow-none"
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={applyingVoucher || !voucherCode.trim()}
                onClick={handleApplyVoucher}
                className="h-8 w-full"
              >
                {applyingVoucher ? "..." : "Áp dụng"}
              </Button>
            </div>

            <Button type="submit" disabled={!file || loading} className="h-full min-h-12 gap-2">
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileUp className="h-4 w-4" />}
              {loading ? "Đang chấm" : "Chấm điểm"}
            </Button>
          </form>

          <div className="mt-4 flex flex-wrap items-center gap-3">
            <div className="flex w-fit items-center gap-2 rounded-full bg-muted px-3 py-1.5 text-sm text-muted-foreground">
              <Coins className="h-4 w-4" />
              Phí chấm:{" "}
              <span className="font-medium text-foreground">
                {(voucherPreview?.finalPoints ?? CHECK_SCORE_PRICE_POINTS).toLocaleString("vi-VN")} points
              </span>
            </div>
            {voucherPreview && (
              <p className="text-sm text-emerald-600">
                {voucherPreview.message} · giảm {voucherPreview.discountPoints.toLocaleString("vi-VN")} points
              </p>
            )}
            {voucherError && <p className="text-sm text-destructive">{voucherError}</p>}
          </div>
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

      <aside className="rounded-xl border border-border/70 bg-card p-5 shadow-sm">
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <BookOpenCheck className="h-5 w-5" />
          </div>
          <div>
            <h2 className="font-semibold">Môn hỗ trợ</h2>
            <p className="text-xs text-muted-foreground">{subjects.length} môn có thể check</p>
          </div>
        </div>

        <div className="relative mb-4">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={subjectSearch}
            onChange={(event) => setSubjectSearch(event.target.value)}
            placeholder="Tìm mã môn..."
            className="pl-9"
          />
        </div>

        <div className="max-h-[520px] space-y-2 overflow-auto pr-1">
          {subjectsLoading ? (
            <p className="text-sm text-muted-foreground">Đang tải danh sách môn...</p>
          ) : filteredSubjects.length > 0 ? (
            filteredSubjects.map((subject) => (
              <div
                key={subject}
                className="rounded-lg border border-border bg-background/70 px-3 py-2 font-mono text-sm"
              >
                {subject}
              </div>
            ))
          ) : (
            <p className="text-sm text-muted-foreground">Không tìm thấy môn phù hợp.</p>
          )}
        </div>
      </aside>
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
