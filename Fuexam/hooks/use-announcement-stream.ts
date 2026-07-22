"use client";

import { useEffect, useRef, useState } from "react";

interface AnnouncementItem {
  id: string;
  contentHtml: string;
  backgroundColor: string;
  linkUrl: string | null;
  linkLabel: string | null;
  priority: number;
  scrollSpeed: number;
  stepSeconds: number;
}

const SSE_URL = `${process.env.NEXT_PUBLIC_API_URL ?? ""}/api/v1/broadcasts/stream`;
const FALLBACK_URL = `${process.env.NEXT_PUBLIC_API_URL ?? ""}/api/v1/announcements/active`;

export function useAnnouncementStream(): AnnouncementItem[] {
  const [announcements, setAnnouncements] = useState<AnnouncementItem[]>([]);
  const retryTimeout = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    let es: EventSource | null = null;
    let mounted = true;

    function connect() {
      es = new EventSource(SSE_URL, { withCredentials: true });

      es.addEventListener("announcement.sync", (event: MessageEvent) => {
        try {
          const parsed = JSON.parse(event.data);
          const inner = typeof parsed.message === "string" ? JSON.parse(parsed.message) : parsed;
          if (Array.isArray(inner.announcements)) {
            setAnnouncements(inner.announcements);
          }
        } catch {
          // ignore malformed events
        }
      });

      es.onerror = () => {
        es?.close();
        if (mounted) {
          retryTimeout.current = setTimeout(connect, 5000);
        }
      };
    }

    // Fetch initial state via REST fallback, then connect SSE
    fetch(FALLBACK_URL, { credentials: "include" })
      .then((res) => res.json())
      .then((json) => {
        if (mounted && Array.isArray(json.data)) {
          setAnnouncements(json.data);
        }
      })
      .catch(() => {})
      .finally(() => {
        if (mounted) connect();
      });

    return () => {
      mounted = false;
      es?.close();
      if (retryTimeout.current) clearTimeout(retryTimeout.current);
    };
  }, []);

  return announcements;
}
