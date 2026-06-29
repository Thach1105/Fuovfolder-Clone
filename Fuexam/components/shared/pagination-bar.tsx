interface PaginationBarProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}

export function PaginationBar({ page, totalPages, onPageChange, className }: PaginationBarProps) {
  if (totalPages <= 1) return null;

  return (
    <div className={className ?? "flex items-center justify-center gap-3 border-t border-slate-100 py-3"}>
      <button
        type="button"
        className="btn-secondary"
        disabled={page === 0}
        onClick={() => onPageChange(Math.max(0, page - 1))}
      >
        ← Trước
      </button>
      <span className="text-sm text-slate-600">
        Trang {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        className="btn-secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onPageChange(page + 1)}
      >
        Sau →
      </button>
    </div>
  );
}
