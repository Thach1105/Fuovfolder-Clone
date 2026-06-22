"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import { addPostReaction, getPostReactionStatus, removePostReaction } from "@/lib/api/reactions";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

interface PostReactionsProps {
  postId: string;
}

export function PostReactions({ postId }: PostReactionsProps) {
  const { user } = useAuth();
  const [count, setCount] = useState(0);
  const [reacted, setReacted] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const canReact = can(user, "forum.reaction:create");

  useEffect(() => {
    getPostReactionStatus(postId)
      .then((status) => {
        setCount(status.count);
        setReacted(status.reacted);
      })
      .catch(() => {
        setCount(0);
        setReacted(false);
      })
      .finally(() => setLoading(false));
  }, [postId]);

  async function toggleReaction() {
    if (!canReact || submitting) return;
    setSubmitting(true);
    try {
      const result = reacted
        ? await removePostReaction(postId)
        : await addPostReaction(postId);
      setCount(result.count);
      setReacted(result.reacted);
    } catch (err) {
      console.error(err instanceof ApiError ? err.message : err);
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <span className="text-xs text-slate-400">...</span>;
  }

  return (
    <button
      type="button"
      disabled={!canReact || submitting}
      onClick={toggleReaction}
      className={`inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-medium transition ${
        reacted
          ? "bg-rose-50 text-rose-700"
          : "bg-slate-100 text-slate-600 hover:bg-slate-200 disabled:opacity-50"
      }`}
      title={canReact ? "Thích bài viết" : "Cần gói Fuexam Member để thích bài viết"}
    >
      ♥ {count}
    </button>
  );
}
