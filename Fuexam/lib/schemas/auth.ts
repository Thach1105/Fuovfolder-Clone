import { z } from "zod";

export const loginSchema = z.object({
  identifier: z.string().min(1, "Không được để trống"),
  password: z.string().min(1, "Không được để trống"),
});
export type LoginFormValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  email: z.string().email("Email không hợp lệ"),
  username: z
    .string()
    .min(3, "Tối thiểu 3 ký tự")
    .max(64, "Tối đa 64 ký tự"),
  password: z.string().min(8, "Tối thiểu 8 ký tự"),
  displayName: z.string().min(1, "Không được để trống"),
  campus: z.string().optional(),
});
export type RegisterFormValues = z.infer<typeof registerSchema>;

export const forgotPasswordSchema = z.object({
  email: z.string().email("Email không hợp lệ"),
});
export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({
    password: z.string().min(8, "Tối thiểu 8 ký tự"),
    confirmPassword: z.string(),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: "Mật khẩu xác nhận không khớp",
    path: ["confirmPassword"],
  });
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Không được để trống"),
    newPassword: z.string().min(8, "Tối thiểu 8 ký tự").max(128, "Tối đa 128 ký tự"),
    confirmNewPassword: z.string(),
  })
  .refine((d) => d.newPassword === d.confirmNewPassword, {
    message: "Mật khẩu xác nhận không khớp",
    path: ["confirmNewPassword"],
  });
export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;

export const setPasswordSchema = z
  .object({
    password: z.string().min(8, "Tối thiểu 8 ký tự").max(128, "Tối đa 128 ký tự"),
    confirmPassword: z.string(),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: "Mật khẩu xác nhận không khớp",
    path: ["confirmPassword"],
  });
export type SetPasswordFormValues = z.infer<typeof setPasswordSchema>;
