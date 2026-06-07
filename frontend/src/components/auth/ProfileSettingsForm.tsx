"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { ApiError } from "@/lib/api/client";
import * as usersApi from "@/lib/api/users";
import { ImageUploader } from "@/components/media/ImageUploader";
import { resolveMediaUrl } from "@/lib/api/media";
import { useAuth } from "@/lib/auth/AuthProvider";

export function ProfileSettingsForm() {
  const { user, loading, refreshUser } = useAuth();
  const [form, setForm] = useState({
    displayName: "",
    firstName: "",
    lastName: "",
    avatarUrl: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) {
      setForm({
        displayName: user.displayName ?? "",
        firstName: user.firstName ?? "",
        lastName: user.lastName ?? "",
        avatarUrl: user.avatarUrl ?? "",
      });
    }
  }, [user]);

  if (loading) {
    return <p className="text-sm text-slate-500">Đang tải hồ sơ...</p>;
  }

  if (!user) {
    return (
      <div className="space-y-3 text-sm">
        <p className="text-slate-600">Bạn cần đăng nhập để xem hồ sơ.</p>
        <Link href="/login" className="btn-primary inline-flex">
          Đăng nhập
        </Link>
      </div>
    );
  }

  function updateField(field: keyof typeof form, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setSubmitting(true);
    try {
      await usersApi.updateProfile({
        displayName: form.displayName || undefined,
        firstName: form.firstName || undefined,
        lastName: form.lastName || undefined,
        avatarUrl: form.avatarUrl || undefined,
      });
      await refreshUser();
      setSuccess("Cập nhật hồ sơ thành công.");
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Cập nhật thất bại.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  const avatarPreview = resolveMediaUrl(form.avatarUrl || user.avatarUrl);

  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-slate-100 bg-slate-50 p-4 text-sm">
        <dl className="grid gap-2 sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Email</dt>
            <dd className="font-medium text-slate-800">{user.email}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Username</dt>
            <dd className="font-medium text-slate-800">@{user.username}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Trạng thái</dt>
            <dd className="font-medium text-slate-800">{user.status}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Email verified</dt>
            <dd className="font-medium text-slate-800">
              {user.emailVerified ? "Đã xác minh" : "Chưa xác minh"}
            </dd>
          </div>
        </dl>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </div>
        )}
        {success && (
          <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
            {success}
          </div>
        )}

        <div className="flex items-center gap-4">
          {avatarPreview ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={avatarPreview} alt="" className="h-16 w-16 rounded-full object-cover" />
          ) : (
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-fuo-50 text-lg font-bold text-fuo-700">
              {(form.displayName || user.username).charAt(0).toUpperCase()}
            </div>
          )}
          <ImageUploader
            purpose="avatar"
            value={form.avatarUrl || null}
            onChange={(objectKey) => updateField("avatarUrl", objectKey ?? "")}
            label="Ảnh đại diện"
          />
        </div>

        <div>
          <label htmlFor="displayName" className="mb-1 block text-sm font-medium text-slate-700">
            Tên hiển thị
          </label>
          <input
            id="displayName"
            className="input-field"
            value={form.displayName}
            onChange={(e) => updateField("displayName", e.target.value)}
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="firstName" className="mb-1 block text-sm font-medium text-slate-700">
              Họ
            </label>
            <input
              id="firstName"
              className="input-field"
              value={form.firstName}
              onChange={(e) => updateField("firstName", e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="lastName" className="mb-1 block text-sm font-medium text-slate-700">
              Tên
            </label>
            <input
              id="lastName"
              className="input-field"
              value={form.lastName}
              onChange={(e) => updateField("lastName", e.target.value)}
            />
          </div>
        </div>

        <button type="submit" disabled={submitting} className="btn-primary">
          {submitting ? "Đang lưu..." : "Lưu thay đổi"}
        </button>
      </form>
    </div>
  );
}
