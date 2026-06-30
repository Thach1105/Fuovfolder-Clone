"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { toast } from "sonner";
import { AdminShell } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import * as voucherApi from "@/lib/api/admin-vouchers";
import type { VoucherResponse } from "@/lib/api/admin-vouchers";
import { useAuth } from "@/lib/auth/AuthProvider";
import { can } from "@/lib/auth/permissions";

export default function VoucherAssignmentsPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const canUpdate = can(user, "voucher.admin:update");

  const [voucher, setVoucher] = useState<VoucherResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [addInput, setAddInput] = useState("");
  const [adding, setAdding] = useState(false);

  const [removeInput, setRemoveInput] = useState("");
  const [removing, setRemoving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const v = await voucherApi.getVoucher(id);
      setVoucher(v);
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : "Không tải được thông tin voucher.");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canUpdate || !addInput.trim()) return;
    const userIds = addInput
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    if (userIds.length === 0) {
      toast.error("Nhập ít nhất một User ID.");
      return;
    }
    setAdding(true);
    try {
      await voucherApi.assignVoucherUsers(id, userIds);
      toast.success(`Đã gán ${userIds.length} user vào voucher.`);
      setAddInput("");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Gán user thất bại.");
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canUpdate || !removeInput.trim()) return;
    const userId = removeInput.trim();
    setRemoving(true);
    try {
      await voucherApi.removeVoucherAssignment(id, userId);
      toast.success("Đã xoá user khỏi voucher.");
      setRemoveInput("");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Xoá user thất bại.");
    } finally {
      setRemoving(false);
    }
  };

  const title = voucher ? `Phân quyền: ${voucher.code}` : "Phân quyền voucher";

  return (
    <AdminShell
      title={title}
      description="Quản lý danh sách user được phép dùng voucher này"
      actions={
        <Link
          href="/vouchers"
          className="text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          ← Quay lại Voucher
        </Link>
      }
    >
      {loading && <p className="text-sm text-muted-foreground">Đang tải...</p>}

      {!loading && loadError && (
        <div className="mb-6 rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          {loadError}
        </div>
      )}

      {!loading && voucher && (
        <div className="space-y-6">
          {/* Voucher info */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base font-mono">{voucher.code}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-1 text-sm text-muted-foreground">
              {voucher.description && <p>{voucher.description}</p>}
              <p>
                Trạng thái:{" "}
                <span className={voucher.active ? "text-emerald-500 font-medium" : "text-amber-500 font-medium"}>
                  {voucher.active ? "Hoạt động" : "Tắt"}
                </span>
              </p>
              <p>
                Áp dụng cho:{" "}
                <span className="text-foreground">{voucher.applicableTypes}</span>
              </p>
              {voucher.requiredMembershipSlugs && (
                <p>
                  Yêu cầu membership:{" "}
                  <span className="text-foreground">{voucher.requiredMembershipSlugs}</span>
                </p>
              )}
              <p className="mt-2 rounded-md border border-blue-500/30 bg-blue-500/10 px-3 py-2 text-blue-500 text-xs">
                Lưu ý: Nếu không có user nào được gán, voucher áp dụng cho tất cả users.
              </p>
            </CardContent>
          </Card>

          {!canUpdate && (
            <p className="text-xs text-amber-500">
              Chế độ chỉ xem (thiếu quyền voucher.admin:update).
            </p>
          )}

          {canUpdate && (
            <div className="grid gap-6 md:grid-cols-2">
              {/* Add users */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Gán users vào voucher</CardTitle>
                </CardHeader>
                <CardContent>
                  <form onSubmit={handleAdd} className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="add-user-ids">
                        User IDs (UUID, cách nhau bởi dấu phẩy)
                      </Label>
                      <Input
                        id="add-user-ids"
                        placeholder="uuid1, uuid2, uuid3"
                        value={addInput}
                        onChange={(e) => setAddInput(e.target.value)}
                        disabled={adding}
                      />
                      <p className="text-xs text-muted-foreground">
                        Nhập một hoặc nhiều UUID, phân cách bằng dấu phẩy.
                      </p>
                    </div>
                    <Button type="submit" disabled={adding || !addInput.trim()}>
                      {adding ? "Đang gán..." : "Gán users"}
                    </Button>
                  </form>
                </CardContent>
              </Card>

              {/* Remove user */}
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">Xoá user khỏi voucher</CardTitle>
                </CardHeader>
                <CardContent>
                  <form onSubmit={handleRemove} className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="remove-user-id">User ID (UUID)</Label>
                      <Input
                        id="remove-user-id"
                        placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                        value={removeInput}
                        onChange={(e) => setRemoveInput(e.target.value)}
                        disabled={removing}
                      />
                    </div>
                    <Button
                      type="submit"
                      variant="destructive"
                      disabled={removing || !removeInput.trim()}
                    >
                      {removing ? "Đang xoá..." : "Xoá user"}
                    </Button>
                  </form>
                </CardContent>
              </Card>
            </div>
          )}
        </div>
      )}
    </AdminShell>
  );
}
