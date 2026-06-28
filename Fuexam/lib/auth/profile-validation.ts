export type ProfileIdentityFormState = {
  username: string;
  campus: string;
  displayName: string;
};

export type ProfileIdentityField = keyof ProfileIdentityFormState;
export type ProfileIdentityErrors = Partial<Record<ProfileIdentityField, string>>;

const USERNAME_PATTERN = /^[a-z0-9_]+$/;

export function validateProfileIdentity(
  form: ProfileIdentityFormState,
): ProfileIdentityErrors {
  const errors: ProfileIdentityErrors = {};
  const username = form.username.trim();
  const displayName = form.displayName.trim();

  if (!username) errors.username = "Nhập tên đăng nhập.";
  else if (username.length < 3) {
    errors.username = "Tên đăng nhập cần ít nhất 3 ký tự.";
  } else if (username.length > 32) {
    errors.username = "Tên đăng nhập tối đa 32 ký tự.";
  } else if (username !== username.toLowerCase()) {
    errors.username =
      "Tên đăng nhập chỉ dùng chữ thường, viết liền, không dấu.";
  } else if (!USERNAME_PATTERN.test(username)) {
    errors.username =
      "Tên đăng nhập viết liền, không dấu; chỉ dùng a-z, 0-9 và dấu gạch dưới (_).";
  }

  if (!form.campus) errors.campus = "Chọn campus của bạn.";

  if (!displayName) errors.displayName = "Nhập tên hiển thị.";
  else if (displayName.length < 2) {
    errors.displayName = "Tên hiển thị cần ít nhất 2 ký tự.";
  } else if (displayName.length > 80) {
    errors.displayName = "Tên hiển thị tối đa 80 ký tự.";
  }

  return errors;
}
