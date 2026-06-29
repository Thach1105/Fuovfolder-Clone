"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { ImageUploader } from "@/components/admin/ImageUploader";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  type AwardDefinition,
  listAwardDefinitions,
  updateAwardDefinition,
} from "@/lib/api/awards";
import { ApiError } from "@/lib/api/client";
import { resolveMediaUrl } from "@/lib/api/media";

export default function AdminAwardsPage() {
  const [items, setItems] = useState<AwardDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [editing, setEditing] = useState<AwardDefinition | null>(null);
  const [form, setForm] = useState({ name: "", description: "", iconUrl: "", active: true });
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listAwardDefinitions());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không tải được danh hiệu.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function startEdit(item: AwardDefinition) {
    setEditing(item);
    setForm({
      name: item.name,
      description: item.description ?? "",
      iconUrl: item.iconUrl ?? "",
      active: item.active,
    });
  }

  async function submit() {
    if (!editing) return;
    setSaving(true);
    try {
      await updateAwardDefinition(editing.id, {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        iconUrl: form.iconUrl.trim() || undefined,
        active: form.active,
      });
      toast.success("Đã cập nhật danh hiệu.");
      setEditing(null);
      await load();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Lưu thất bại.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <AdminShell title="Danh hiệu" description="Quản lý định nghĩa danh hiệu cộng đồng">
      {error && (
        <div className="mb-4 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-sm text-muted-foreground">Đang tải...</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Chưa có danh hiệu nào.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((item) => {
            const icon = resolveMediaUrl(item.iconUrl);
            return (
              <Card key={item.id}>
                <CardContent className="p-4">
                  <div className="flex items-start gap-3">
                    {icon ? (
                      /* eslint-disable-next-line @next/next/no-img-element */
                      <img
                        src={icon}
                        alt=""
                        loading="lazy"
                        className="h-12 w-12 rounded-lg border border-border object-cover"
                      />
                    ) : (
                      <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-muted text-xs text-muted-foreground">
                        N/A
                      </div>
                    )}
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-foreground">{item.name}</p>
                      <p className="font-mono text-xs text-muted-foreground">{item.slug}</p>
                      <span
                        className={`mt-1 inline-block rounded px-2 py-0.5 text-[10px] font-semibold uppercase ${
                          item.active
                            ? "bg-emerald-500/15 text-emerald-500"
                            : "bg-muted text-muted-foreground"
                        }`}
                      >
                        {item.active ? "Đang bật" : "Tắt"}
                      </span>
                    </div>
                  </div>
                  {item.description && (
                    <p className="mt-3 text-sm text-muted-foreground">{item.description}</p>
                  )}
                  <Button
                    variant="outline"
                    size="sm"
                    className="mt-3"
                    onClick={() => startEdit(item)}
                  >
                    Sửa
                  </Button>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      <Dialog open={editing != null} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Sửa danh hiệu</DialogTitle>
            <DialogDescription>{editing?.slug}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="award-name">Tên</Label>
              <Input
                id="award-name"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="award-desc">Mô tả</Label>
              <Textarea
                id="award-desc"
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </div>
            <ImageUploader
              label="Icon danh hiệu"
              purpose="award_icon"
              value={form.iconUrl || null}
              onChange={(url) => setForm({ ...form, iconUrl: url ?? "" })}
            />
            <div className="flex items-center justify-between rounded-lg border border-border px-3 py-2">
              <Label htmlFor="award-active" className="cursor-pointer">
                Đang bật
              </Label>
              <Switch
                id="award-active"
                checked={form.active}
                onCheckedChange={(v) => setForm({ ...form, active: v })}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)} disabled={saving}>
              Hủy
            </Button>
            <Button onClick={submit} disabled={saving}>
              {saving ? "Đang lưu..." : "Lưu"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminShell>
  );
}
