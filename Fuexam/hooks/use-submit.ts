import { useState, useCallback } from "react";
import { ApiError } from "@/lib/api/client";

const DEFAULT_FALLBACK = "Có lỗi xảy ra. Vui lòng thử lại.";

export function useSubmit(fallbackError?: string) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = useCallback(
    async <T>(fn: () => Promise<T>): Promise<T | undefined> => {
      setSubmitting(true);
      setError(null);
      try {
        return await fn();
      } catch (err) {
        setError(
          err instanceof ApiError
            ? err.message
            : (fallbackError ?? DEFAULT_FALLBACK),
        );
        return undefined;
      } finally {
        setSubmitting(false);
      }
    },
    [fallbackError],
  );

  const clearError = useCallback(() => setError(null), []);

  return { submitting, error, submit, clearError };
}
