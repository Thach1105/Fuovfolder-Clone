import { useCallback, useState } from "react";

interface PaginatedResponse {
  totalPages: number;
  totalElements?: number;
}

export function usePagination() {
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const updateFromResponse = useCallback((res: PaginatedResponse) => {
    setTotalPages(res.totalPages);
    if (res.totalElements !== undefined) {
      setTotalElements(res.totalElements);
    }
  }, []);

  const reset = useCallback(() => {
    setPage(0);
    setTotalPages(0);
    setTotalElements(0);
  }, []);

  return { page, setPage, totalPages, totalElements, updateFromResponse, reset };
}
