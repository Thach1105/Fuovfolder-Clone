export function PromoBanner() {
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-2xl border border-ink-200 bg-gradient-to-r from-fuo-50 to-ink-50 px-4 py-3 text-sm text-ink-700">
      <span className="font-medium text-ink-900">Tham gia cộng đồng Facebook:</span>
      <a
        href="https://www.facebook.com/groups/nvh2FUExam"
        target="_blank"
        rel="noopener noreferrer"
        className="font-medium text-fuo-700 underline-offset-2 hover:underline"
      >
        facebook.com/groups/nvh2FUExam
      </a>
    </div>
  );
}