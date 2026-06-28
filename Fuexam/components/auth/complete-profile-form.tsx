"use client";

import { type FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import * as authApi from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  type ProfileIdentityErrors,
  type ProfileIdentityField,
  type ProfileIdentityFormState,
  validateProfileIdentity,
} from "@/lib/auth/profile-validation";
import { FPT_CAMPUSES } from "@/lib/fpt-campuses";
import { cn } from "@/lib/utils";

export type CompleteProfileFormState = ProfileIdentityFormState;
export type CompleteProfileField = ProfileIdentityField;

export function validateCompleteProfileForm(
  form: CompleteProfileFormState,
): Partial<Record<CompleteProfileField, string>> {
  return validateProfileIdentity(form);
}

function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return <p className="text-xs font-medium text-destructive">{message}</p>;
}

export function CompleteProfileForm(): JSX.Element {
  const router = useRouter();
  const { user, refreshUser } = useAuth();
  const [form, setForm] = useState<CompleteProfileFormState>({
    username: user?.username ?? "",
    campus: "",
    displayName: user?.displayName ?? "",
  });
  const [touched, setTouched] = useState<
    Partial<Record<CompleteProfileField, boolean>>
  >({});
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<
    Partial<Record<CompleteProfileField, string>>
  >({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setForm((prev) => ({
      ...prev,
      username: user?.username ?? prev.username,
      displayName: user?.displayName ?? prev.displayName,
    }));
  }, [user?.displayName, user?.username]);

  const errors = useMemo(() => validateCompleteProfileForm(form), [form]);

  function visibleError(field: CompleteProfileField) {
    return fieldErrors[field] ?? (touched[field] || submitted ? errors[field] : undefined);
  }

  function update(field: CompleteProfileField, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setTouched((prev) => ({ ...prev, [field]: true }));
    setFieldErrors((prev) => {
      if (!prev[field]) return prev;
      return { ...prev, [field]: undefined } satisfies ProfileIdentityErrors;
    });
  }

  function markTouched(field: CompleteProfileField) {
    setTouched((prev) => ({ ...prev, [field]: true }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSubmitted(true);
    setError(null);
    setFieldErrors({});

    const nextErrors = validateCompleteProfileForm(form);
    if (Object.keys(nextErrors).length > 0) return;

    setSubmitting(true);
    try {
      await authApi.completePendingProfile({
        username: form.username.trim(),
        campus: form.campus,
        displayName: form.displayName.trim(),
      });
      await refreshUser();
      router.push("/suoc");
      router.refresh();
    } catch (err) {
      if (err instanceof ApiError) {
        const usernameError = err.fieldErrors.find(
          (field) => field.field === "username",
        );
        if (usernameError) {
          setFieldErrors({ username: usernameError.message });
        } else {
          setError(err.message);
        }
      } else {
        setError("Hoàn tất hồ sơ thất bại. Vui lòng thử lại.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  const fieldClass = (field: CompleteProfileField) =>
    cn(visibleError(field) && "border-destructive focus-visible:ring-destructive/20");

  return (
    <div className="rounded-2xl border border-foreground/10 bg-background/70 p-6 shadow-lg backdrop-blur-xl sm:p-8">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        {error && (
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </div>
        )}

        {user?.email && (
          <div className="rounded-lg border border-foreground/10 bg-foreground/5 px-3 py-2 text-sm text-muted-foreground">
            Email đăng nhập: <span className="font-medium text-foreground">{user.email}</span>
          </div>
        )}

        <div className="space-y-1.5">
          <Label htmlFor="username">Tên đăng nhập</Label>
          <Input
            id="username"
            autoComplete="username"
            value={form.username}
            onBlur={() => markTouched("username")}
            onChange={(e) => update("username", e.target.value)}
            aria-invalid={Boolean(visibleError("username"))}
            className={fieldClass("username")}
            placeholder="nguyen_van_a"
          />
          <p className="text-xs text-muted-foreground">
            Viết liền, không dấu. Chỉ dùng chữ thường a-z, số 0-9 và dấu gạch dưới.
          </p>
          <FieldError message={visibleError("username")} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="campus">Campus</Label>
          <select
            id="campus"
            value={form.campus}
            onBlur={() => markTouched("campus")}
            onChange={(e) => update("campus", e.target.value)}
            aria-invalid={Boolean(visibleError("campus"))}
            className={cn(
              "file:text-foreground placeholder:text-muted-foreground selection:bg-primary selection:text-primary-foreground dark:bg-input/30 border-input h-9 w-full min-w-0 rounded-md border bg-transparent px-3 py-1 text-base shadow-xs outline-none transition-[color,box-shadow] focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
              fieldClass("campus"),
            )}
          >
            <option value="">Chọn campus</option>
            {FPT_CAMPUSES.map((campus) => (
              <option key={campus} value={campus}>
                {campus}
              </option>
            ))}
          </select>
          <FieldError message={visibleError("campus")} />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="displayName">Tên hiển thị</Label>
          <Input
            id="displayName"
            value={form.displayName}
            onBlur={() => markTouched("displayName")}
            onChange={(e) => update("displayName", e.target.value)}
            aria-invalid={Boolean(visibleError("displayName"))}
            className={fieldClass("displayName")}
            placeholder="Nguyễn Văn A"
          />
          <p className="text-xs text-muted-foreground">
            Tên này hiển thị trên diễn đàn và hồ sơ.
          </p>
          <FieldError message={visibleError("displayName")} />
        </div>

        <Button
          type="submit"
          disabled={submitting}
          className="h-11 w-full rounded-full bg-foreground text-background hover:bg-foreground/90"
        >
          {submitting ? "Đang hoàn tất..." : "Hoàn tất hồ sơ"}
        </Button>
      </form>
    </div>
  );
}
