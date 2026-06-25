/**
 * Lớp dịch lỗi backend sang tiếng Việt.
 *
 * Backend trả về lỗi dạng { code, message } với `code` ổn định (UPPER_SNAKE).
 * Ta dịch theo `code` để không phụ thuộc ngôn ngữ của `message` gốc. Nếu không
 * có bản dịch theo code, ta thử dịch các thông báo validation mặc định của
 * jakarta (tiếng Anh), cuối cùng mới fallback về message gốc.
 */

/** Bản dịch theo mã lỗi backend. */
const CODE_MESSAGES: Record<string, string> = {
  // Auth & tài khoản
  AUTH_REQUIRED: "Bạn cần đăng nhập để tiếp tục.",
  AUTH_INVALID: "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.",
  INVALID_CREDENTIALS: "Email/tên đăng nhập hoặc mật khẩu không đúng.",
  EMAIL_NOT_VERIFIED: "Email chưa được xác minh. Vui lòng kiểm tra hộp thư.",
  EMAIL_TAKEN: "Email này đã được sử dụng.",
  USERNAME_TAKEN: "Tên đăng nhập này đã được sử dụng.",
  USER_ALREADY_EXISTS: "Email hoặc tên đăng nhập đã được sử dụng.",
  INVALID_USERNAME: "Tên đăng nhập không hợp lệ.",
  WEAK_PASSWORD: "Mật khẩu quá yếu. Vui lòng dùng mật khẩu mạnh hơn.",
  USER_NOT_FOUND: "Không tìm thấy người dùng.",
  USER_BLOCKED: "Tài khoản đã bị chặn.",
  USER_DISABLED: "Tài khoản đã bị vô hiệu hoá.",
  USER_NOT_ALLOWED: "Bạn không được phép thực hiện thao tác này.",
  USER_NOT_ELIGIBLE: "Tài khoản của bạn chưa đủ điều kiện.",
  TOKEN_EXPIRED: "Liên kết hoặc mã đã hết hạn. Vui lòng thử lại.",
  TOKEN_INVALID: "Mã không hợp lệ hoặc đã hết hạn.",
  SESSION_REVOKED: "Phiên đăng nhập đã bị thu hồi. Vui lòng đăng nhập lại.",
  REFRESH_REUSE_DETECTED: "Phát hiện phiên đăng nhập bất thường. Vui lòng đăng nhập lại.",

  // Phân quyền
  PERMISSION_DENIED: "Bạn không có quyền thực hiện thao tác này.",
  PERMISSION_INVALID: "Quyền không hợp lệ.",
  ROLE_EXISTS: "Vai trò này đã tồn tại.",
  ROLE_INVALID: "Vai trò không hợp lệ.",
  ROLE_NOT_EDITABLE: "Vai trò này không thể chỉnh sửa.",
  ROLE_NOT_FOUND: "Không tìm thấy vai trò.",
  ROLE_NOT_MEMBERSHIP: "Vai trò này không phải gói membership.",

  // Điểm & thanh toán
  INSUFFICIENT_POINTS: "Số dư Fuexam Point không đủ.",
  INVALID_AMOUNT: "Số tiền không hợp lệ.",
  BALANCE_NOT_FOUND: "Không tìm thấy số dư.",
  ORDER_NOT_FOUND: "Không tìm thấy đơn hàng.",
  DEPOSIT_TIER_NOT_FOUND: "Không tìm thấy mệnh giá nạp.",
  ALREADY_REFUNDED: "Đơn này đã được hoàn tiền.",
  NOT_REFUNDABLE: "Đơn này không thể hoàn tiền.",
  REFUND_DISABLED: "Chức năng hoàn tiền đang tắt.",

  // Membership
  MEMBERSHIP_ACTIVE: "Bạn đang có gói membership còn hiệu lực.",
  PLAN_EXISTS: "Gói này đã tồn tại.",
  PLAN_INVALID: "Thông tin gói không hợp lệ.",
  PLAN_NOT_FOUND: "Không tìm thấy gói.",
  PLAN_STATUS_INVALID: "Trạng thái gói không hợp lệ.",
  BILLING_INTERVAL_INVALID: "Chu kỳ thanh toán không hợp lệ.",

  // Source / câu hỏi
  CATALOG_CODE_EXISTS: "Mã tài liệu này đã tồn tại.",
  CATALOG_NOT_FOUND: "Không tìm thấy tài liệu.",
  QUESTION_NOT_FOUND: "Không tìm thấy câu hỏi.",
  QUESTION_EMPTY: "Câu hỏi phải có nội dung hoặc hình ảnh.",
  OPTIONS_TOO_FEW: "Câu hỏi cần ít nhất 2 đáp án.",
  OPTION_EMPTY: "Mỗi đáp án phải có nội dung hoặc hình ảnh.",
  NO_CORRECT_OPTION: "Cần ít nhất một đáp án đúng.",
  INVALID_REORDER: "Thứ tự sắp xếp không hợp lệ.",
  INVALID_RELATED: "Tài liệu liên quan không hợp lệ.",
  RELATED_NOT_FOUND: "Không tìm thấy tài liệu liên quan.",
  PURCHASE_NOT_FOUND: "Không tìm thấy lượt mua.",
  NO_ACTIVE_ACCESS: "Bạn chưa có quyền truy cập tài liệu này.",

  // Coursera
  CREDENTIAL_NOT_FOUND: "Không tìm thấy thông tin đăng nhập.",
  REQUEST_NOT_FOUND: "Không tìm thấy yêu cầu.",
  INVALID_STATUS: "Trạng thái không hợp lệ.",

  // Diễn đàn / bài viết
  FORUM_NOT_FOUND: "Không tìm thấy diễn đàn.",
  CATEGORY_NOT_FOUND: "Không tìm thấy chuyên mục.",
  CATEGORY_NOT_LEAF: "Chỉ có thể đăng bài trong mục môn học.",
  THREAD_NOT_FOUND: "Không tìm thấy chủ đề.",
  THREAD_LOCKED: "Chủ đề đã bị khoá.",
  THREAD_TYPE_INVALID: "Loại chủ đề không hợp lệ.",
  POST_NOT_FOUND: "Không tìm thấy bài viết.",
  POST_FORBIDDEN: "Bạn không có quyền với bài viết này.",
  POST_NOT_PENDING: "Bài viết không ở trạng thái chờ duyệt.",
  PARENT_POST_INVALID: "Bài viết gốc không hợp lệ.",
  PARENT_ROLE_INVALID: "Vai trò bài viết gốc không hợp lệ.",
  NESTED_REPLY_TOO_DEEP: "Không thể trả lời lồng quá sâu.",
  REACTION_TYPE_INVALID: "Loại tương tác không hợp lệ.",
  POLL_OPTIONS_REQUIRED: "Bình chọn cần ít nhất 2 lựa chọn.",
  TARGET_INVALID: "Đối tượng không hợp lệ.",
  TARGET_TYPE_INVALID: "Loại đối tượng không hợp lệ.",

  // Kiểm duyệt
  FLAG_ACTION_INVALID: "Hành động xử lý không hợp lệ.",
  FLAG_ALREADY_OPEN: "Báo cáo này đang được xử lý.",
  FLAG_ALREADY_RESOLVED: "Báo cáo này đã được xử lý.",
  FLAG_NOT_FOUND: "Không tìm thấy báo cáo.",

  // Tệp & tải lên
  FILE_NOT_FOUND: "Không tìm thấy tệp.",
  FILE_EMPTY: "Tệp rỗng.",
  FILE_TOO_LARGE: "Tệp vượt quá dung lượng cho phép.",
  FILE_TYPE_INVALID: "Định dạng tệp không được hỗ trợ.",
  FILE_NOT_ACTIVE: "Tệp không khả dụng.",
  FILE_FORBIDDEN: "Bạn không có quyền với tệp này.",
  FILE_ALREADY_LINKED: "Tệp đã được đính kèm.",
  FILE_PURPOSE_MISMATCH: "Mục đích tệp không khớp.",
  FILE_READ_FAILED: "Đọc tệp thất bại.",
  FILE_STORE_FAILED: "Lưu tệp thất bại.",
  UPLOAD_FORBIDDEN: "Bạn không có quyền tải lên.",
  UPLOAD_RATE_LIMIT: "Bạn tải lên quá nhanh. Vui lòng thử lại sau.",
  TOO_MANY_ATTACHMENTS: "Quá nhiều tệp đính kèm.",
  STORAGE_NOT_CONFIGURED: "Lưu trữ chưa được cấu hình.",
  STORAGE_BUCKET_UNAVAILABLE: "Dịch vụ lưu trữ tạm thời không khả dụng.",

  // Thông báo & khác
  NOTIFICATION_NOT_FOUND: "Không tìm thấy thông báo.",
  NOTIFICATION_PREF_INVALID: "Cài đặt thông báo không hợp lệ.",
  AWARD_NOT_FOUND: "Không tìm thấy huy hiệu.",

  // Lỗi hệ thống chung
  VALIDATION_ERROR: "Dữ liệu nhập không hợp lệ. Vui lòng kiểm tra lại.",
  INTERNAL_ERROR: "Có lỗi xảy ra phía máy chủ. Vui lòng thử lại sau.",
};

/**
 * Dịch các thông báo validation mặc định (tiếng Anh) của Bean Validation.
 * Trả về null nếu không khớp mẫu nào.
 */
function translateValidationMessage(message: string): string | null {
  const m = message.trim();
  if (/^must not be blank$/i.test(m) || /^must not be empty$/i.test(m)) {
    return "Không được để trống.";
  }
  if (/^must not be null$/i.test(m)) {
    return "Không được để trống.";
  }
  if (/^must be a well-formed email address$/i.test(m)) {
    return "Email không hợp lệ.";
  }
  let match = /^size must be between (\d+) and (\d+)$/i.exec(m);
  if (match) {
    return `Độ dài phải từ ${match[1]} đến ${match[2]} ký tự.`;
  }
  match = /^must be greater than or equal to (\d+)$/i.exec(m);
  if (match) {
    return `Giá trị phải lớn hơn hoặc bằng ${match[1]}.`;
  }
  match = /^must be less than or equal to (\d+)$/i.exec(m);
  if (match) {
    return `Giá trị phải nhỏ hơn hoặc bằng ${match[1]}.`;
  }
  if (/^must be a valid URL$/i.test(m)) {
    return "Đường dẫn không hợp lệ.";
  }
  return null;
}

/**
 * Trả về thông báo lỗi tiếng Việt phù hợp nhất cho một lỗi backend.
 * @param code  Mã lỗi backend (UPPER_SNAKE) — ưu tiên cao nhất.
 * @param fallbackMessage  Message gốc từ backend (có thể tiếng Anh).
 */
export function translateApiError(
  code: string | null | undefined,
  fallbackMessage: string | null | undefined,
): string {
  if (code && CODE_MESSAGES[code]) {
    return CODE_MESSAGES[code];
  }
  if (fallbackMessage) {
    const translated = translateValidationMessage(fallbackMessage);
    if (translated) return translated;
  }
  return fallbackMessage || "Có lỗi xảy ra. Vui lòng thử lại.";
}

/** Dịch một thông báo lỗi field (dùng cho danh sách fieldErrors). */
export function translateFieldMessage(message: string): string {
  return translateValidationMessage(message) ?? message;
}
