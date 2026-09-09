"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import {
  type AdminPaper,
  type AdminPaperContent,
  type AdminSubject,
  type AdminWebhookEvent,
  type ExamPaperStatus,
  deleteExamPaper,
  examMediaUrl,
  getExamPaperContent,
  listExamPapers,
  listExamSubjects,
  listExamWebhookEvents,
  publishExamPaper,
} from "@/lib/api/exam";
import { formatDateTime } from "@/lib/format-datetime";
import { ChevronDown } from "lucide-react";

const ALL = "all";

const STATUS_LABELS: Record<ExamPaperStatus, string> = {
  draft: "Nháp",
  published: "Đã phát hành",
};

const EVENT_STATUS_LABELS: Record<AdminWebhookEvent["status"], string> = {
  pending: "Đang chờ",
  processing: "Đang xử lý",
  done: "Xong",
  failed: "Thất bại",
};

type TermGroup = { term: string | null; label: string; papers: AdminPaper[] };

function groupByTerm(papers: AdminPaper[]): TermGroup[] {
  const byTerm = new Map<string | null, AdminPaper[]>();
  for (const paper of papers) {
    const key = paper.term ?? null;
    const bucket = byTerm.get(key);
    if (bucket) {
      bucket.push(paper);
    } else {
      byTerm.set(key, [paper]);
    }
  }

  const terms = [...byTerm.keys()].sort((a, b) => {
    if (a === null) return -1;
    if (b === null) return 1;
    return a.localeCompare(b);
  });

  return terms.map((term) => ({
    term,
    label: term === null ? "Chưa rõ kỳ" : term,
    papers: byTerm.get(term)!,
  }));
}

function formatBytes(bytes: number | null) {
  if (!bytes || bytes <= 0) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export default function AdminExamPapersPage() {
  const { user } = useAuth();
  const canPublish = can(user, "exam.paper.admin:publish");
  const canDelete = can(user, "exam.paper.admin:delete");
  const canReadWebhooks = can(user, "exam.webhook.admin:read");

  const [papers, setPapers] = useState<AdminPaper[]>([]);
  const [subjects, setSubjects] = useState<AdminSubject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>(ALL);
  const [subjectFilter, setSubjectFilter] = useState<string>(ALL);
  const [campusFilter, setCampusFilter] = useState<string>(ALL);

  const [content, setContent] = useState<AdminPaperContent | null>(null);
  const [contentLoading, setContentLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminPaper | null>(null);

  const [events, setEvents] = useState<AdminWebhookEvent[]>([]);
  const [eventStatus, setEventStatus] = useState<string>("failed");
  const [eventsLoading, setEventsLoading] = useState(false);
  const [openEvent, setOpenEvent] = useState<AdminWebhookEvent | null>(null);

  const loadPapers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPapers(
        await listExamPapers({
          subjectId: subjectFilter === ALL ? undefined : subjectFilter,
          status: statusFilter === ALL ? undefined : (statusFilter as ExamPaperStatus),
        }),
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh sách đề.");
    } finally {
      setLoading(false);
    }
  }, [subjectFilter, statusFilter]);

  const loadEvents = useCallback(async () => {
    if (!canReadWebhooks) return;
    setEventsLoading(true);
    try {
      setEvents(
        await listExamWebhookEvents(
          eventStatus === ALL ? undefined : (eventStatus as AdminWebhookEvent["status"]),
        ),
      );
    } catch {
      setEvents([]);
    } finally {
      setEventsLoading(false);
    }
  }, [eventStatus, canReadWebhooks]);

  useEffect(() => {
    loadPapers();
  }, [loadPapers]);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  useEffect(() => {
    listExamSubjects()
      .then(setSubjects)
      .catch(() => setSubjects([]));
  }, []);

  const subjectCode = useMemo(() => {
    const map = new Map<string, string>();
    subjects.forEach((s) => map.set(s.id, s.code));
    return map;
  }, [subjects]);

  const draftCount = useMemo(() => papers.filter((p) => p.status === "draft").length, [papers]);

  const campuses = useMemo(() => {
    const set = new Set<string>();
    papers.forEach((p) => {
      if (p.campus) set.add(p.campus);
    });
    return [...set].sort();
  }, [papers]);

  const filteredPapers = useMemo(() => {
    if (campusFilter === ALL) return papers;
    return papers.filter((p) => p.campus === campusFilter);
  }, [papers, campusFilter]);

  const groups = useMemo(() => groupByTerm(filteredPapers), [filteredPapers]);

  async function openContent(paper: AdminPaper) {
    setContentLoading(true);
    setContent(null);
    try {
      setContent(await getExamPaperContent(paper.id));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Không tải được nội dung đề.");
    } finally {
      setContentLoading(false);
    }
  }

  async function onPublish(paper: AdminPaper) {
    setBusyId(paper.id);
    try {
      await publishExamPaper(paper.id);
      toast.success(`Đã phát hành ${paper.examCode}.`);
      await loadPapers();
      if (content?.paper.id === paper.id) setContent(null);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Phát hành thất bại.");
    } finally {
      setBusyId(null);
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return;
    setBusyId(deleteTarget.id);
    try {
      await deleteExamPaper(deleteTarget.id);
      toast.success(`Đã xóa ${deleteTarget.examCode}.`);
      await loadPapers();
      if (content?.paper.id === deleteTarget.id) setContent(null);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xóa thất bại.");
    } finally {
      setBusyId(null);
      setDeleteTarget(null);
    }
  }

  return (
    <AdminShell
      title="Exam — Đề thi"
      description="Duyệt và phát hành đề FE/PE nhận từ webhook, theo dõi log tiếp nhận"
    >
      <Tabs defaultValue="papers">
        <TabsList>
          <TabsTrigger value="papers">
            Đề thi{draftCount > 0 ? ` (${draftCount} nháp)` : ""}
          </TabsTrigger>
          {canReadWebhooks && <TabsTrigger value="webhooks">Log webhook</TabsTrigger>}
        </TabsList>

        <TabsContent value="papers" className="mt-4">
          <div className="mb-4 flex flex-wrap items-center gap-3">
            <div className="w-48">
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger>
                  <SelectValue placeholder="Trạng thái" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>Tất cả trạng thái</SelectItem>
                  <SelectItem value="draft">Nháp</SelectItem>
                  <SelectItem value="published">Đã phát hành</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="w-56">
              <Select value={subjectFilter} onValueChange={setSubjectFilter}>
                <SelectTrigger>
                  <SelectValue placeholder="Môn" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>Tất cả môn</SelectItem>
                  {subjects.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.code}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="w-40">
              <Select value={campusFilter} onValueChange={setCampusFilter}>
                <SelectTrigger>
                  <SelectValue placeholder="Cơ sở" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>Tất cả cơ sở</SelectItem>
                  {campuses.map((c) => (
                    <SelectItem key={c} value={c}>
                      {c}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button variant="outline" onClick={loadPapers} disabled={loading}>
              Tải lại
            </Button>
          </div>

          {error && <p className="mb-3 text-sm text-destructive">{error}</p>}

          <div className="rounded-xl border border-border">
            {loading ? (
              <p className="p-4 text-sm text-muted-foreground">Đang tải…</p>
            ) : filteredPapers.length === 0 ? (
              <p className="p-4 text-center text-sm text-muted-foreground">
                Chưa có đề nào khớp bộ lọc.
              </p>
            ) : (
              <Collapsible defaultOpen>
                <div className="flex items-center justify-between border-b border-border px-4 py-3">
                  <div>
                    <p className="text-sm font-semibold">Đề thi theo kỳ</p>
                    <p className="text-xs text-muted-foreground">
                      {filteredPapers.length} đề · nhóm theo kỳ học
                    </p>
                  </div>
                  <CollapsibleTrigger asChild>
                    <Button type="button" variant="ghost" size="sm">
                      <ChevronDown className="h-4 w-4" />
                    </Button>
                  </CollapsibleTrigger>
                </div>
                <CollapsibleContent>
                  <div className="divide-y divide-border">
                    {groups.map((group) => (
                      <Collapsible key={group.term ?? "none"} defaultOpen>
                        <div className="flex items-center justify-between px-4 py-2">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-semibold">{group.label}</span>
                            <span className="text-xs text-muted-foreground">
                              {group.papers.length} đề
                            </span>
                          </div>
                          <CollapsibleTrigger asChild>
                            <Button type="button" variant="ghost" size="sm">
                              <ChevronDown className="h-4 w-4" />
                            </Button>
                          </CollapsibleTrigger>
                        </div>
                        <CollapsibleContent>
                          <Table>
                            <TableHeader>
                              <TableRow>
                                <TableHead>Mã đề</TableHead>
                                <TableHead>Môn</TableHead>
                                <TableHead>Loại</TableHead>
                                <TableHead>Cơ sở</TableHead>
                                <TableHead className="text-right">Câu / Ảnh</TableHead>
                                <TableHead className="text-right">File</TableHead>
                                <TableHead>Nguồn</TableHead>
                                <TableHead>Trạng thái</TableHead>
                                <TableHead>Nhận lúc</TableHead>
                                <TableHead className="text-right">Hành động</TableHead>
                              </TableRow>
                            </TableHeader>
                            <TableBody>
                              {group.papers.map((paper) => (
                                <TableRow key={paper.id}>
                                  <TableCell className="font-mono text-xs">{paper.examCode}</TableCell>
                                  <TableCell>{subjectCode.get(paper.subjectId) ?? "—"}</TableCell>
                                  <TableCell>
                                    <Badge variant={paper.paperType === "FE" ? "secondary" : "outline"}>
                                      {paper.paperType}
                                    </Badge>
                                  </TableCell>
                                  <TableCell>
                                    {paper.campus ? (
                                      <span className="rounded bg-muted px-1.5 py-0.5 text-xs font-medium">
                                        {paper.campus}
                                      </span>
                                    ) : (
                                      "—"
                                    )}
                                  </TableCell>
                                  <TableCell className="text-right">{paper.questionCount}</TableCell>
                                  <TableCell className="text-right">{paper.resourceCount}</TableCell>
                                  <TableCell className="text-xs text-muted-foreground">
                                    {paper.ingestSource ?? "—"}
                                  </TableCell>
                                  <TableCell>
                                    <Badge variant={paper.status === "draft" ? "outline" : "default"}>
                                      {STATUS_LABELS[paper.status]}
                                    </Badge>
                                  </TableCell>
                                  <TableCell className="text-xs text-muted-foreground">
                                    {formatDateTime(paper.createdAt)}
                                  </TableCell>
                                  <TableCell className="space-x-2 text-right">
                                    <Button size="sm" variant="outline" onClick={() => openContent(paper)}>
                                      Xem
                                    </Button>
                                    {canPublish && paper.status === "draft" && (
                                      <Button
                                        size="sm"
                                        onClick={() => onPublish(paper)}
                                        disabled={busyId === paper.id}
                                      >
                                        Phát hành
                                      </Button>
                                    )}
                                    {canDelete && (
                                      <Button
                                        size="sm"
                                        variant="destructive"
                                        onClick={() => setDeleteTarget(paper)}
                                        disabled={busyId === paper.id}
                                      >
                                        Xóa
                                      </Button>
                                    )}
                                  </TableCell>
                                </TableRow>
                              ))}
                            </TableBody>
                          </Table>
                        </CollapsibleContent>
                      </Collapsible>
                    ))}
                  </div>
                </CollapsibleContent>
              </Collapsible>
            )}
          </div>
        </TabsContent>

        {canReadWebhooks && (
          <TabsContent value="webhooks" className="mt-4">
            <div className="mb-4 flex flex-wrap items-center gap-3">
              <div className="w-48">
                <Select value={eventStatus} onValueChange={setEventStatus}>
                  <SelectTrigger>
                    <SelectValue placeholder="Trạng thái" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="failed">Thất bại</SelectItem>
                    <SelectItem value="pending">Đang chờ</SelectItem>
                    <SelectItem value="done">Xong</SelectItem>
                    <SelectItem value={ALL}>Tất cả</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <Button variant="outline" onClick={loadEvents} disabled={eventsLoading}>
                Tải lại
              </Button>
            </div>

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Thời điểm</TableHead>
                  <TableHead>Nguồn</TableHead>
                  <TableHead>Event ID</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="text-right">Lần thử</TableHead>
                  <TableHead>Mã lỗi</TableHead>
                  <TableHead className="text-right"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {eventsLoading ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center text-muted-foreground">
                      Đang tải…
                    </TableCell>
                  </TableRow>
                ) : events.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center text-muted-foreground">
                      Không có bản ghi nào.
                    </TableCell>
                  </TableRow>
                ) : (
                  events.map((event) => (
                    <TableRow key={event.id}>
                      <TableCell className="text-xs text-muted-foreground">
                        {formatDateTime(event.createdAt)}
                      </TableCell>
                      <TableCell>{event.clientId}</TableCell>
                      <TableCell className="font-mono text-xs">{event.eventId}</TableCell>
                      <TableCell>
                        <Badge variant={event.status === "failed" ? "destructive" : "outline"}>
                          {EVENT_STATUS_LABELS[event.status]}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">{event.attemptCount}</TableCell>
                      <TableCell className="font-mono text-xs">{event.errorCode ?? "—"}</TableCell>
                      <TableCell className="text-right">
                        {event.errorMessage && (
                          <Button size="sm" variant="outline" onClick={() => setOpenEvent(event)}>
                            Chi tiết
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TabsContent>
        )}
      </Tabs>

      {/* Paper preview */}
      <Sheet
        open={contentLoading || content !== null}
        onOpenChange={(open) => {
          if (!open) setContent(null);
        }}
      >
        <SheetContent className="w-full overflow-y-auto sm:max-w-xl">
          <SheetHeader>
            <SheetTitle>{content?.paper.examCode ?? "Đang tải…"}</SheetTitle>
            <SheetDescription>
              {content
                ? `${content.paper.title} · ${content.paper.paperType} · ${
                    STATUS_LABELS[content.paper.status]
                  }`
                : "Đang tải nội dung đề"}
            </SheetDescription>
          </SheetHeader>

          {content && (
            <div className="mt-4 space-y-4 px-4 pb-8">
              {content.questions.length > 0 && (
                <div className="space-y-4">
                  {content.questions.map((question, index) => (
                    <div key={question.id} className="rounded-lg border p-3">
                      <p className="mb-2 text-xs font-medium text-muted-foreground">
                        Câu {index + 1}
                        {question.questionText ? ` — ${question.questionText}` : ""}
                      </p>
                      {question.imageUrls.map((key) => {
                        const src = examMediaUrl(key);
                        return src ? (
                          /* eslint-disable-next-line @next/next/no-img-element */
                          <img
                            key={key}
                            src={src}
                            alt=""
                            loading="lazy"
                            className="w-full rounded border"
                          />
                        ) : null;
                      })}
                    </div>
                  ))}
                </div>
              )}

              {content.images.length > 0 && (
                <div className="space-y-2">
                  <p className="text-sm font-medium">Ảnh đề</p>
                  {content.images.map((key) => {
                    const src = examMediaUrl(key);
                    return src ? (
                      /* eslint-disable-next-line @next/next/no-img-element */
                      <img key={key} src={src} alt="" loading="lazy" className="w-full rounded border" />
                    ) : null;
                  })}
                </div>
              )}

              {content.resources.length > 0 && (
                <div className="space-y-2">
                  <p className="text-sm font-medium">File tài nguyên</p>
                  <ul className="space-y-1 text-sm">
                    {content.resources.map((resource) => (
                      <li key={resource.id} className="flex justify-between gap-3 rounded border p-2">
                        <span className="truncate">
                          {resource.folderLabel ? `${resource.folderLabel} / ` : ""}
                          {resource.originalFilename}
                        </span>
                        <span className="shrink-0 text-muted-foreground">
                          {formatBytes(resource.sizeBytes)}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {content.questions.length === 0 &&
                content.images.length === 0 &&
                content.resources.length === 0 && (
                  <p className="text-sm text-destructive">
                    Đề này không có nội dung — không thể phát hành.
                  </p>
                )}

              {canPublish && content.paper.status === "draft" && (
                <Button className="w-full" onClick={() => onPublish(content.paper)}>
                  Phát hành đề này
                </Button>
              )}
            </div>
          )}
        </SheetContent>
      </Sheet>

      {/* Webhook error detail */}
      <Sheet open={openEvent !== null} onOpenChange={(open) => !open && setOpenEvent(null)}>
        <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
          <SheetHeader>
            <SheetTitle>{openEvent?.errorCode ?? "Chi tiết"}</SheetTitle>
            <SheetDescription>
              {openEvent ? `${openEvent.clientId} · ${openEvent.eventId}` : ""}
            </SheetDescription>
          </SheetHeader>
          {openEvent && (
            <div className="mt-4 space-y-3 px-4 text-sm">
              <p className="whitespace-pre-wrap break-words">{openEvent.errorMessage}</p>
              <p className="text-muted-foreground">
                Số lần thử: {openEvent.attemptCount} · Xử lý lúc:{" "}
                {openEvent.processedAt ? formatDateTime(openEvent.processedAt) : "—"}
              </p>
            </div>
          )}
        </SheetContent>
      </Sheet>

      <ConfirmDialog
        open={deleteTarget !== null}
        title={`Xóa đề ${deleteTarget?.examCode ?? ""}?`}
        description="Xóa cả câu hỏi/ảnh đề và các file tài nguyên của đề này. Không thể hoàn tác."
        confirmLabel="Xóa đề"
        destructive
        onConfirm={confirmDelete}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      />
    </AdminShell>
  );
}
