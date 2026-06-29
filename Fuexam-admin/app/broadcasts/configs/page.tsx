"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { ApiError } from "@/lib/api/client";
import * as broadcastApi from "@/lib/api/admin-broadcast";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";
import type { BroadcastConfigResponse } from "@/types/api";

const EVENT_TYPE_LABELS: Record<string, string> = {
  "deposit.completed": "Nạp tiền thành công",
};

const ENABLED_LABEL: Record<string, string> = {
  true: "Đang bật",
  false: "Đã tắt",
};

const ENABLED_COLOR: Record<string, string> = {
  true: "bg-emerald-500/15 text-emerald-500",
  false: "bg-muted text-muted-foreground",
};

const DEFAULT_TEMPLATE = "%s vừa nạp %,d VND vào tài khoản!";

const EMPTY_FORM = {
  thresholdVnd: "500000",
  messageTemplate: DEFAULT_TEMPLATE,
  enabled: true,
};

const formatVnd = (n: number) => `${n.toLocaleString("vi-VN")} ₫`;

export default function BroadcastConfigsPage() {
  const { user } = useAuth();
  const canUpdate = can(user, "broadcast.admin:update");

  const [configs, setConfigs] = useState<BroadcastConfigResponse[]>([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingType, setEditingType] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [togglingType, setTogglingType] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await broadcastApi.listBroadcastConfigs();
      setConfigs(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được cấu hình.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setEditingType(null);
  };

  const startEdit = (cfg: BroadcastConfigResponse) => {
    setEditingType(cfg.eventType);
    const config = cfg.config as Record<string, unknown>;
    setForm({
      thresholdVnd: String(config.thresholdVnd ?? "500000"),
      messageTemplate: String(config.messageTemplate ?? DEFAULT_TEMPLATE),
      enabled: cfg.enabled,
    });
  };

  const parsedThreshold = parseInt(form.thresholdVnd, 10);
  const formValid =
    Number.isFinite(parsedThreshold) &&
    parsedThreshold >= 0 &&
    form.messageTemplate.trim().length > 0;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!canUpdate || !formValid) return;

    const eventType = editingType ?? "deposit.completed";

    setSubmitting(true);
    setError(null);
    try {
      await broadcastApi.upsertBroadcastConfig(eventType, {
        config: {
          thresholdVnd: parsedThreshold,
          messageTemplate: form.messageTemplate.trim(),
        },
        enabled: form.enabled,
      });
      toast.success(editingType ? "Đã cập nhật cấu hình." : "Đã tạo cấu hình.");
      resetForm();
      await load();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Lưu thất bại.";
      setError(message);
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggle = async (cfg: BroadcastConfigResponse) => {
    if (!canUpdate) return;
    setTogglingType(cfg.eventType);
    setError(null);
    try {
      await broadcastApi.toggleBroadcastConfig(cfg.eventType);
      toast.success(cfg.enabled ? "Đã tắt thông báo." : "Đã bật thông báo.");
      await load();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Không đổi được trạng thái.";
      setError(message);
      toast.error(message);
    } finally {
      setTogglingType(null);
    }
  };

  const previewMessage = formValid
    ? form.messageTemplate
        .replace("%s", "Nguyen Van A")
        .replace("%,d", parsedThreshold.toLocaleString("vi-VN"))
    : null;

  return (
    <AdminShell
      title="Thông báo toàn server"
      description="Cấu hình dòng chữ thông báo lướt qua trên giao diện cho tất cả người dùng"
    >
      {!canUpdate && (
        <p className="mb-4 text-xs text-amber-500">
          Chế độ chỉ xem (thiếu broadcast.admin:update)
        </p>
      )}

      {error && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && (
        <div className="grid gap-6 lg:grid-cols-2">
          {canUpdate && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">
                  {editingType
                    ? `Sửa: ${EVENT_TYPE_LABELS[editingType] ?? editingType}`
                    : "Thêm cấu hình — Nạp tiền thành công"}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="thresholdVnd">Mệnh giá tối thiểu (VND)</Label>
                    <Input
                      id="thresholdVnd"
                      type="number"
                      min={0}
                      step={10000}
                      placeholder="500000"
                      value={form.thresholdVnd}
                      onChange={(e) => setForm({ ...form, thresholdVnd: e.target.value })}
                      required
                    />
                    <p className="text-xs text-muted-foreground">
                      Nạp từ {formValid ? formatVnd(parsedThreshold) : "—"} trở lên sẽ hiện thông
                      báo trên toàn server.
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="messageTemplate">Mẫu thông báo</Label>
                    <Input
                      id="messageTemplate"
                      placeholder="%s vừa nạp %,d VND vào tài khoản!"
                      value={form.messageTemplate}
                      onChange={(e) => setForm({ ...form, messageTemplate: e.target.value })}
                      required
                    />
                    <p className="text-xs text-muted-foreground">
                      <code className="rounded bg-muted px-1">%s</code> = tên người dùng,{" "}
                      <code className="rounded bg-muted px-1">%,d</code> = số tiền VND
                    </p>
                  </div>

                  {previewMessage && (
                    <div className="rounded-md border border-border bg-muted/40 px-3 py-2">
                      <p className="text-xs font-medium text-muted-foreground">Xem trước:</p>
                      <p className="mt-1 text-sm text-foreground">{previewMessage}</p>
                    </div>
                  )}

                  <div className="flex items-center justify-between rounded-md border border-border bg-muted/40 px-3 py-2">
                    <div>
                      <p className="text-sm font-medium text-foreground">Bật thông báo</p>
                      <p className="text-xs text-muted-foreground">
                        Khi tắt, sự kiện nạp tiền sẽ không phát thông báo.
                      </p>
                    </div>
                    <Switch
                      checked={form.enabled}
                      onCheckedChange={(v) => setForm({ ...form, enabled: v })}
                    />
                  </div>

                  <div className="flex gap-2">
                    <Button type="submit" disabled={submitting || !formValid}>
                      {submitting ? "Đang lưu..." : editingType ? "Cập nhật" : "Tạo cấu hình"}
                    </Button>
                    {editingType && (
                      <Button type="button" variant="outline" onClick={resetForm}>
                        Hủy
                      </Button>
                    )}
                  </div>
                </form>
              </CardContent>
            </Card>
          )}

          <div className="space-y-3">
            <h2 className="text-sm font-semibold text-foreground">
              Cấu hình hiện tại ({configs.length})
            </h2>
            {configs.length === 0 && (
              <p className="text-sm text-muted-foreground">Chưa có cấu hình nào.</p>
            )}
            {configs.map((cfg) => {
              const config = cfg.config as Record<string, unknown>;
              return (
                <Card key={cfg.id}>
                  <CardContent className="p-4">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div>
                        <p className="font-semibold text-foreground">
                          {EVENT_TYPE_LABELS[cfg.eventType] ?? cfg.eventType}
                        </p>
                        <p className="text-xs text-muted-foreground">{cfg.eventType}</p>
                      </div>
                      <span
                        className={`rounded px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${
                          ENABLED_COLOR[String(cfg.enabled)] ?? "bg-muted text-muted-foreground"
                        }`}
                      >
                        {ENABLED_LABEL[String(cfg.enabled)] ?? String(cfg.enabled)}
                      </span>
                    </div>
                    <dl className="mt-3 grid grid-cols-1 gap-y-2 text-xs text-muted-foreground">
                      <div>
                        <dt>Mệnh giá tối thiểu</dt>
                        <dd className="text-primary">
                          {config.thresholdVnd
                            ? formatVnd(Number(config.thresholdVnd))
                            : "Chưa cấu hình"}
                        </dd>
                      </div>
                      <div>
                        <dt>Mẫu thông báo</dt>
                        <dd className="text-foreground">
                          {String(config.messageTemplate ?? DEFAULT_TEMPLATE)}
                        </dd>
                      </div>
                    </dl>
                    {canUpdate && (
                      <div className="mt-3 flex items-center gap-3 text-sm">
                        <button
                          type="button"
                          className="text-primary hover:underline"
                          onClick={() => startEdit(cfg)}
                        >
                          Sửa
                        </button>
                        <button
                          type="button"
                          className="text-muted-foreground hover:underline"
                          onClick={() => handleToggle(cfg)}
                          disabled={togglingType === cfg.eventType}
                        >
                          {togglingType === cfg.eventType
                            ? "Đang đổi..."
                            : cfg.enabled
                              ? "Tắt thông báo"
                              : "Bật thông báo"}
                        </button>
                      </div>
                    )}
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </div>
      )}
    </AdminShell>
  );
}
