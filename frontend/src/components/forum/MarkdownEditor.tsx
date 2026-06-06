"use client";

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  minHeight?: number;
  placeholder?: string;
}

export function MarkdownEditor({
  value,
  onChange,
  minHeight = 160,
  placeholder,
}: MarkdownEditorProps) {
  return (
    <div className="space-y-2">
      <textarea
        className="input-field font-mono text-sm"
        style={{ minHeight }}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder ?? "Viết bằng Markdown..."}
        required
      />
      <p className="text-xs text-slate-500">
        Hỗ trợ Markdown: **in đậm**, *nghiêng*, `code`, [liên kết](url), danh sách.
      </p>
    </div>
  );
}
