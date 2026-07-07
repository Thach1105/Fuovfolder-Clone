"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import {
  type AdminExamComment,
  type AdminSubject,
  deleteAdminExamComment,
  listAdminExamComments,
  listExamSubjects,
} from "@/lib/api/exam";
import { formatDateTime } from "@/lib/format-datetime";

const ALL = "all";
const PAGE_SIZE = 20;

const SUBJECT_TYPE_LABELS: Record<string, string> = {
  fe_question: "Câu hỏi FE",
  pe_item: "Đề PE",
};

export default function AdminExamCommentsPage() {
  const { user } = useAuth();
  const canModerate = can(user, "exam.comment.admin:delete");

  const [comments, setComments] = useState<AdminExamComment[]>([]);
  const [subjects, setSubjects] = useState<AdminSubject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [typeFilter, setTypeFilter] = useState<string>(ALL);
  const [subjectFilter, setSubjectFilter] = useState<string>(ALL);
  const [deleteId, setDeleteId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listAdminExamComments({
        subjectType: typeFilter === ALL ? undefined : (typeFilter as "fe_question" | "pe_item"),
        examSubjectId: subjectFilter === ALL ? undefined : subjectFilter,
        page,
        size: PAGE_SIZE,
      });
      setComments(res.items);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được bình luận.");
    } finally {
      setLoading(false);
    }
  }, [typeFilter, subjectFilter, page]);

  useEffect(() => {
    load();
  }, [load]);

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

  async function confirmDelete() {
    if (!deleteId) return;
    try {
      await deleteAdminExamComment(deleteId);
      toast.success("Đã gỡ bình luận.");
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Gỡ thất bại.");
    } finally {
      setDeleteId(null);
    }
  }

  function onFilterChange(setter: (v: string) => void, value: string) {
    setter(value);
    setPage(0);
  }

  return (
    <AdminShell
      title="Exam — Bình luận"
      description="Kiểm duyệt bình luận của thành viên trên câu hỏi FE và đề PE"
    >
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="w-48">
          <Select value={typeFilter} onValueChange={(v) => onFilterChange(setTypeFilter, v)}>
            <SelectTrigger>
              <SelectValue placeholder="Loại nội dung" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Tất cả loại</SelectItem>
              <SelectItem value="fe_question">Câu hỏi FE</SelectItem>
              <SelectItem value="pe_item">Đề PE</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="w-56">
          <Select value={subjectFilter} onValueChange={(v) => onFilterChange(setSubjectFilter, v)}>
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
        <span className="text-sm text-muted-foreground">{totalElements} bình luận</span>
      </div>

      <div className="rounded-xl border border-border">
        {loading ? (
          <p className="p-4 text-sm text-muted-foreground">Đang tải...</p>
        ) : error ? (
          <p className="p-4 text-sm text-destructive">{error}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[140px]">Tác giả</TableHead>
                <TableHead>Nội dung</TableHead>
                <TableHead className="w-[120px]">Loại</TableHead>
                <TableHead className="w-[90px]">Môn</TableHead>
                <TableHead className="w-[150px]">Thời gian</TableHead>
                <TableHead className="w-[90px] text-right">Hành động</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {comments.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>
                    <p className="font-medium">{c.authorDisplayName ?? c.authorUsername ?? "—"}</p>
                    {c.authorUsername && (
                      <p className="text-xs text-muted-foreground">@{c.authorUsername}</p>
                    )}
                  </TableCell>
                  <TableCell>
                    <div
                      className="prose prose-sm max-w-[420px] break-words text-foreground [&_p]:my-0"
                      // Backend sanitizes bodyHtml before persisting.
                      dangerouslySetInnerHTML={{ __html: c.bodyHtml }}
                    />
                    {c.parentCommentId && (
                      <span className="text-xs text-muted-foreground">↳ trả lời</span>
                    )}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {SUBJECT_TYPE_LABELS[c.subjectType] ?? c.subjectType}
                  </TableCell>
                  <TableCell className="font-mono text-xs text-primary">
                    {c.examSubjectCode ?? subjectCode.get(c.examSubjectId) ?? "—"}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {formatDateTime(c.createdAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    {canModerate && (
                      <button
                        type="button"
                        className="text-sm text-destructive hover:underline"
                        onClick={() => setDeleteId(c.id)}
                      >
                        Gỡ
                      </button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {comments.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-sm text-muted-foreground">
                    Không có bình luận nào.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        )}
      </div>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between">
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Trước
          </Button>
          <span className="text-sm text-muted-foreground">
            Trang {page + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
          >
            Sau
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={deleteId != null}
        title="Gỡ bình luận?"
        description="Bình luận sẽ bị ẩn khỏi người dùng. Hành động này không thể hoàn tác."
        destructive
        confirmLabel="Gỡ"
        onConfirm={confirmDelete}
        onOpenChange={(o) => !o && setDeleteId(null)}
      />
    </AdminShell>
  );
}
