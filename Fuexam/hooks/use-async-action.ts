import { useState, useCallback } from "react";
import { ApiError } from "@/lib/api/client";

const DEFAULT_FALLBACK = "Có lỗi xảy ra. Vui lòng thử lại.";

export function useAsyncAction(fallbackError?: string) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(
    async <T>(fn: () => Promise<T>): Promise<T | undefined> => {
      setLoading(true);
      setError(null);
      try {
        const result = await fn();
        return result;
      } catch (err) {
        setError(
          err instanceof ApiError
            ? err.message
            : (fallbackError ?? DEFAULT_FALLBACK),
        );
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [fallbackError],
  );

  const clearError = useCallback(() => setError(null), []);

  return { loading, error, run, clearError };
}
