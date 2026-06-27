"use client";

import { useEffect } from "react";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <html lang="vi">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: "1rem",
          padding: "1.5rem",
          textAlign: "center",
          fontFamily:
            "system-ui, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
          background: "#0a0a0a",
          color: "#fafafa",
        }}
      >
        <h1 style={{ fontSize: "1.75rem", fontWeight: 600, margin: 0 }}>
          Bảng quản trị gặp sự cố nghiêm trọng
        </h1>
        <p style={{ maxWidth: "28rem", color: "#a1a1aa" }}>
          Đã xảy ra lỗi ngoài dự kiến. Vui lòng tải lại trang.
        </p>
        <button
          onClick={reset}
          style={{
            padding: "0.625rem 2rem",
            borderRadius: "0.5rem",
            border: "none",
            background: "#fafafa",
            color: "#0a0a0a",
            fontSize: "1rem",
            cursor: "pointer",
          }}
        >
          Tải lại
        </button>
      </body>
    </html>
  );
}
