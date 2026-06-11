"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import {
  getThreadBookmarkStatus,
  unwatchThread,
  watchThread,
} from "@/lib/api/forum";

interface ThreadWatchButtonProps {
  threadId: string;
}

export function ThreadWatchButton({ threadId }: ThreadWatchButtonProps) {
  const [watched, setWatched] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    getThreadBookmarkStatus(threadId)
      .then((status) => setWatched(status.watched))
      .catch(() => setWatched(false))
      .finally(() => setLoading(false));
  }, [threadId]);

  async function toggleWatch() {
    setSubmitting(true);
    setError(null);
    try {
      const status = watched
        ? await unwatchThread(threadId)
        : await watchThread(threadId);
      setWatched(status.watched);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không thể cập nhật theo dõi");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <span className="text-xs text-slate-400">...</span>;
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <button
        type="button"
        className={watched ? "btn-secondary text-xs" : "btn-primary text-xs"}
        disabled={submitting}
        onClick={toggleWatch}
      >
        {submitting ? "Đang lưu..." : watched ? "Bỏ theo dõi" : "Theo dõi"}
      </button>
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  );
}
