"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Monitor, Smartphone, Tablet, LogOut } from "lucide-react";
import { SettingsNav } from "@/components/settings/SettingsNav";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { ApiError } from "@/lib/api/client";
import {
  getActiveSessions,
  revokeSession,
  revokeOtherSessions,
} from "@/lib/api/auth";
import type { SessionListResponse, SessionResponse } from "@/types/api";

function deviceIcon(userAgent: string | null) {
  if (!userAgent) return Monitor;
  const ua = userAgent.toLowerCase();
  if (ua.includes("iphone") || (ua.includes("android") && ua.includes("mobile")))
    return Smartphone;
  if (ua.includes("ipad") || ua.includes("tablet")) return Tablet;
  return Monitor;
}

function formatTime(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function limitLabel(data: SessionListResponse) {
  if (data.deviceLimitSource === "UNLIMITED") return "Không giới hạn";
  return `${data.sessions.length}/${data.maxDevices} thiết bị`;
}

export default function DevicesPage() {
  const [data, setData] = useState<SessionListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [revoking, setRevoking] = useState<string | null>(null);
  const [revokingAll, setRevokingAll] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getActiveSessions());
    } catch (err) {
      toast.error(
        err instanceof ApiError ? err.message : "Không tải được danh sách thiết bị."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleRevoke = async (session: SessionResponse) => {
    setRevoking(session.id);
    try {
      await revokeSession(session.id);
      toast.success(`Đã đăng xuất ${session.deviceLabel}`);
      load();
    } catch (err) {
      toast.error(
        err instanceof ApiError ? err.message : "Không đăng xuất được thiết bị."
      );
    } finally {
      setRevoking(null);
    }
  };

  const handleRevokeAll = async () => {
    setRevokingAll(true);
    try {
      await revokeOtherSessions();
      toast.success("Đã đăng xuất tất cả thiết bị khác.");
      load();
    } catch (err) {
      toast.error(
        err instanceof ApiError
          ? err.message
          : "Không đăng xuất được các thiết bị khác."
      );
    } finally {
      setRevokingAll(false);
    }
  };

  const otherSessions = data?.sessions.filter((s) => !s.current) ?? [];

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <SettingsNav />
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-slate-900">
          Thiết bị đang đăng nhập
        </h1>
        {data && (
          <Badge variant="secondary">{limitLabel(data)}</Badge>
        )}
      </div>

      {loading && (
        <p className="text-sm text-slate-500">Đang tải...</p>
      )}

      {!loading && data && (
        <>
          <div className="space-y-3">
            {data.sessions.map((session) => {
              const Icon = deviceIcon(session.userAgent);
              return (
                <div
                  key={session.id}
                  className="card flex items-center gap-4 p-4"
                >
                  <Icon className="h-8 w-8 shrink-0 text-slate-400" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-900">
                        {session.deviceLabel}
                      </span>
                      {session.current && (
                        <Badge variant="default" className="text-xs">
                          Đang dùng
                        </Badge>
                      )}
                    </div>
                    <p className="text-xs text-slate-500">
                      IP: {session.ipAddress ?? "Không rõ"} · Đăng nhập:{" "}
                      {formatTime(session.issuedAt)} · Hoạt động:{" "}
                      {formatTime(session.lastUsedAt)}
                    </p>
                  </div>
                  {!session.current && (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={revoking === session.id}
                      onClick={() => handleRevoke(session)}
                    >
                      <LogOut className="mr-1 h-3.5 w-3.5" />
                      {revoking === session.id ? "Đang xử lý..." : "Đăng xuất"}
                    </Button>
                  )}
                </div>
              );
            })}
          </div>

          {otherSessions.length > 0 && (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button
                  variant="destructive"
                  className="w-full"
                  disabled={revokingAll}
                >
                  {revokingAll
                    ? "Đang xử lý..."
                    : "Đăng xuất tất cả thiết bị khác"}
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Xác nhận đăng xuất</AlertDialogTitle>
                  <AlertDialogDescription>
                    Tất cả {otherSessions.length} thiết bị khác sẽ bị đăng xuất.
                    Hành động này không thể hoàn tác.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Hủy</AlertDialogCancel>
                  <AlertDialogAction onClick={handleRevokeAll}>
                    Đăng xuất tất cả
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </>
      )}
    </div>
  );
}
