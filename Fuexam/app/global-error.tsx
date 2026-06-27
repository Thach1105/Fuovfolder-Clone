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
          background: "#ffffff",
          color: "#1a1712",
        }}
      >
        <h1 style={{ fontSize: "2rem", fontWeight: 600, margin: 0 }}>
          Ứng dụng gặp sự cố nghiêm trọng
        </h1>
        <p style={{ maxWidth: "28rem", color: "#6b7280" }}>
          Đã xảy ra lỗi ngoài dự kiến. Vui lòng tải lại trang. Nếu vẫn lỗi, hãy thử lại sau.
        </p>
        <button
          onClick={reset}
          style={{
            padding: "0.625rem 2rem",
            borderRadius: "9999px",
            border: "none",
            background: "#1a1712",
            color: "#ffffff",
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
