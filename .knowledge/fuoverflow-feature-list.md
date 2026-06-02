# FuOverflow Community — danh sách chức năng

Nguồn khảo sát: https://fuoverflow.com/  
Ngày khảo sát: 2026-06-02  
Ghi chú: WebFetch mặc định bị `403 Forbidden`, nên nội dung được đọc bằng request giả lập trình duyệt. Khảo sát public, chưa đăng nhập, nên chức năng nội bộ sau đăng nhập có thể chưa đầy đủ.

## Tổng quan

FuOverflow Community là web cộng đồng dạng diễn đàn, chạy trên nền XenForo (`data-xf="2.3"`). Trang tập trung vào cộng đồng sinh viên FPT/FUO: thảo luận, hỏi đáp môn học, confession, tài liệu ôn thi, khóa học, membership, danh hiệu, tìm kiếm, hồ sơ thành viên, thông báo hoạt động mới.

## Nhóm chức năng chính

### 1. Diễn đàn cộng đồng

- Trang danh sách diễn đàn tại `/`.
- Hiển thị danh sách chủ đề mới, tiêu đề, forum, số reply/view, ngày cập nhật, tác giả.
- Có khu vực nổi bật trên trang chủ:
  - Chủ đề mới.
  - Thảo luận.
  - Confession mới.
  - Xem nhiều.
- Có forum/category theo chủ đề, ví dụ:
  - `Hỏi Đáp` tại `/forums/hoi-dap/`.
  - `Confession Trường F` tại `/forums/confession-fpt/`.
  - Mã môn như `CAA201`.
  - `Game Hacking`.
- Có trang chi tiết thread, ví dụ `/threads/caa201.6574/`.
- Có link tới bài mới nhất của thread qua `/latest`.
- Có phân trang danh sách bài/thread.

### 2. Bài mới / hoạt động mới

- Mục `Bài mới` tại `/whats-new/posts/`.
- Mục `Có gì mới?` tại `/whats-new/`.
- Các tab/nhánh hoạt động mới:
  - `Bài mới nhất`.
  - `Featured content`.
  - `Ảnh mới`.
  - `Bình luận ảnh mới`.
  - `Hoạt động gần đây`.
- Danh sách bài mới hiển thị thread, tác giả, thời gian, forum liên quan.
- Hỗ trợ phân trang bài mới.

### 3. Tìm kiếm

- Tìm nhanh trên header.
- Trang tìm kiếm tại `/search/`.
- Tìm kiếm bài viết/chủ đề tại `/search/?type=post`.
- Có tùy chọn `Chỉ tìm trong tiêu đề`.
- Có `Tìm nâng cao`.
- Có tìm theo nhiều loại nội dung:
  - Tất cả.
  - Chủ đề/bài viết.
  - Media.
  - Album.
  - Bình luận media.
  - Campaign donate.
  - Bình luận donate.
- Tìm kiếm theo thẻ tại `/tags/`.

### 4. Tài khoản người dùng

- Đăng nhập tại `/login/`.
- Đăng ký tại `/register/`.
- Đăng ký/đăng nhập bằng Google qua `/register/connected-accounts/google/?setup=1`.
- Quên mật khẩu tại `/lost-password/`.
- Có form đăng nhập với tài khoản/email và mật khẩu.
- Có trang hồ sơ thành viên, ví dụ `/members/vu-thanh-binh.54836/`.
- Header có trạng thái chưa đăng nhập và nút `Đăng nhập`, `Tạo tài khoản`.
- Người dùng phải chấp nhận điều khoản và chính sách bảo mật khi đăng ký.

### 5. Hồ sơ thành viên và thành viên cộng đồng

- Trang member profile public.
- Link tác giả trong thread/list bài dẫn tới trang thành viên.
- Hiển thị tên thành viên, avatar/ký hiệu, bài viết liên quan.
- Có hệ thống danh hiệu gắn với thành viên.

### 6. Hệ thống danh hiệu

- Trang `Danh hiệu` tại `/award-system/list`.
- Có bộ lọc danh hiệu theo category:
  - Tất cả danh hiệu.
  - Danh hiệu thành viên.
  - Danh hiệu FUO.
  - Danh hiệu học tập.
  - Chuyên gia.
  - Linh Tinh.
  - Danh hiệu đặc biệt.
- Dùng để gamify hoạt động cộng đồng/học tập.

### 7. Membership / thành viên trả phí

- Trang `Membership` tại `/membership/`.
- Có CTA:
  - `Đăng nhập ngay`.
  - `Xem gói thành viên`.
- Chức năng public thấy được: giới thiệu hoặc bán gói thành viên trả phí.
- Có khả năng gói membership mở quyền lợi/nội dung riêng, nhưng cần đăng nhập để xác nhận chi tiết.

### 8. Suộc — tài liệu ôn thi

- Trang `Suộc` tại `/suoc`.
- Mô tả trang: `Tài liệu ôn thi`.
- Có trang `Suộc của tôi` tại `/suoc/my-purchases/`.
- Danh sách tài liệu theo mã môn, số câu, tỷ lệ/phần trăm, giá bằng `FUO Point`.
- Có item nổi bật và item thường.
- Ví dụ tài liệu public thấy được:
  - `CSI106`.
  - `IAP301`.
  - `SSL101c`.
  - `MAI391`.
  - `CSD203`.
  - `MLN131`, `MLN122`, `MLN111`.
  - `MAS202`, `MAS291`.
  - `DBI202`, `PRO192`, `PRF192`.
  - `FER202`, `PRN212`, `SDN302`, `SEO201c`, v.v.
- Có lọc/sắp xếp qua query `options[sort]`, `options[sortColumn]`, `options[direction]`.
- Có phân trang danh sách tài liệu.
- Chức năng mua/xem tài liệu yêu cầu tài khoản hoặc điểm FUO Point.

### 9. Khóa học online

- Trang `Khoá học` tại `/course`.
- Trang `Khoá học của tôi` tại `/course/my-purchases/`.
- Có CTA `Xem khoá học`.
- Danh sách khóa học hiển thị:
  - Tên khóa học.
  - Tác giả/giảng viên.
  - Rating.
  - Giá bán.
  - Giá gốc/giá giảm.
- Ví dụ khóa học public thấy được:
  - `FER202 - Practical Exam`.
  - `WED201c - FROM ZERO TO HERO`.
  - `WED201c - TOÀN BỘ - PE FE FUO`.
- Chức năng mua/xem khóa học yêu cầu tài khoản.

### 10. Coursera service

- Có nav `Coursera` tại `/coursera-service`.
- Request public trả nội dung không đọc được/không có title trong lần khảo sát.
- Có thể là trang dịch vụ liên quan Coursera; cần kiểm tra bằng trình duyệt hoặc tài khoản nếu cần chi tiết.

### 11. Discord / cộng đồng ngoài web

- Có CTA `Tham gia Discord`.
- Link trong HTML hiện dạng placeholder `https://fuoverflow.com/${inviteURL}` nên có thể được xử lý bằng JavaScript hoặc cấu hình runtime.
- Có thông báo tham gia nhóm Facebook:
  - `https://www.facebook.com/groups/nvh2fuoverflow`.

### 12. Media / ảnh

- Có mục `Ảnh mới` tại `/whats-new/media/`.
- Có mục `Bình luận ảnh mới` tại `/whats-new/media-comments/`.
- Tìm kiếm media qua `/search/?type=xfmg_media`.
- Tìm album qua `/search/?type=xfmg_album`.
- Tìm bình luận media qua `/search/?type=xfmg_comment`.
- Đây có vẻ là module media gallery của XenForo.

### 13. Donate / campaign

- Tìm kiếm campaign donate qua `/search/?type=thdonate_campaign`.
- Tìm kiếm bình luận donate qua `/search/?type=thdonate_comment`.
- Điều này cho thấy web có module donate/campaign, dù trang donate riêng chưa được thấy trong crawl public giới hạn.

### 14. PWA / cài app

- Có manifest tại `/webmanifest.php`.
- Có hướng dẫn `Install the app`.
- Có hướng dẫn cài web app trên iOS.
- Có icon app `apple-touch-icon`.
- Có theme-color cho light/dark.
- Chức năng: cài website như web app/PWA trên thiết bị.

### 15. Giao diện sáng/tối

- Có `Style variation` tại `/misc/style-variation`.
- Tùy chọn:
  - `System`.
  - `Light`.
  - `Dark`.
- Có theme-color riêng cho light và dark.

### 16. RSS

- Có RSS feed cho forum tại `/forums/-/index.rss`.
- Chức năng: theo dõi cập nhật diễn đàn qua RSS.

### 17. Trang pháp lý / hỗ trợ

- `Liên hệ` tại `/misc/contact`.
- `Quy định và Nội quy` tại `/help/terms/`.
- `Chính sách bản quyền` tại `/help/copyright-policy/`.
- `Chính sách bảo mật` tại `/help/privacy-policy/`.
- `Trợ giúp` tại `/help/`.

## Chức năng có thể yêu cầu đăng nhập

Các chức năng sau xuất hiện dưới dạng link/form nhưng chưa xác minh sâu vì khảo sát chưa đăng nhập:

- Tạo thread/chủ đề mới.
- Trả lời thread.
- Like/react bài viết.
- Mua tài liệu `Suộc` bằng FUO Point.
- Xem tài liệu đã mua tại `/suoc/my-purchases/`.
- Mua/xem khóa học tại `/course/my-purchases/`.
- Đăng ký membership.
- Nhận/quản lý danh hiệu cá nhân.
- Quản lý hồ sơ, thông báo, tin nhắn, cài đặt tài khoản.
- Tham gia donate/campaign.

## Danh sách URL chức năng chính

| Chức năng | URL |
|---|---|
| Trang chủ / diễn đàn | https://fuoverflow.com/ |
| Bài mới | https://fuoverflow.com/whats-new/posts/ |
| Có gì mới | https://fuoverflow.com/whats-new/ |
| Tìm kiếm | https://fuoverflow.com/search/ |
| Tìm trong diễn đàn | https://fuoverflow.com/search/?type=post |
| Tìm theo thẻ | https://fuoverflow.com/tags/ |
| Danh hiệu | https://fuoverflow.com/award-system/list |
| Membership | https://fuoverflow.com/membership/ |
| Coursera | https://fuoverflow.com/coursera-service |
| Suộc / tài liệu ôn thi | https://fuoverflow.com/suoc |
| Suộc của tôi | https://fuoverflow.com/suoc/my-purchases/ |
| Khóa học | https://fuoverflow.com/course |
| Khóa học của tôi | https://fuoverflow.com/course/my-purchases/ |
| Đăng nhập | https://fuoverflow.com/login/ |
| Đăng ký | https://fuoverflow.com/register/ |
| Quên mật khẩu | https://fuoverflow.com/lost-password/ |
| Liên hệ | https://fuoverflow.com/misc/contact |
| Điều khoản | https://fuoverflow.com/help/terms/ |
| Bản quyền | https://fuoverflow.com/help/copyright-policy/ |
| Bảo mật | https://fuoverflow.com/help/privacy-policy/ |
| Trợ giúp | https://fuoverflow.com/help/ |
| RSS | https://fuoverflow.com/forums/-/index.rss |

## Kết luận ngắn

FuOverflow là diễn đàn cộng đồng kiêm nền tảng học tập/tài liệu. Chức năng cốt lõi gồm: diễn đàn hỏi đáp, bài mới, tìm kiếm nâng cao, tài khoản thành viên, hồ sơ, danh hiệu, membership, tài liệu ôn thi trả bằng FUO Point, khóa học online trả phí, media gallery, donate/campaign, PWA, giao diện sáng/tối, RSS, và trang pháp lý/hỗ trợ.
