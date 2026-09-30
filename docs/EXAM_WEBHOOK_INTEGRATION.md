# Hướng dẫn tích hợp gửi đề FE/PE

API: `POST /api/v1/exam/webhook/papers`, body JSON mã hóa UTF-8. Cần triển khai bản backend mới trước khi gửi JSON PE nguyên bản hoặc lô JSON FE/PE hỗn hợp.

## 1. Xác thực

Cấu hình client trong `fuexam.exam.webhook.clients`, sau đó gửi các header:

```http
Content-Type: application/json; charset=utf-8
X-Exam-Client: <client-id>
X-Exam-Signature: t=<Unix timestamp tính bằng giây>,v1=<chữ ký hex>
```

Chữ ký là HMAC-SHA256 dùng secret của client, tính trên `timestamp + "." + chính xác các byte body sẽ gửi`. Sai lệch thời gian cho phép mặc định: 300 giây. Không tạo lại JSON sau khi ký, kể cả chỉ thay đổi khoảng trắng.

## 2. Định dạng dữ liệu

### Định dạng webhook chuẩn

Cấu trúc `{eventId, paper: {...}}` vẫn được hỗ trợ. `paperType` nhận `FE` hoặc `PE`; không truyền `RE`, `B1`, `B5` hoặc số mã đề vào trường này. Với thi lại, xác định đúng loại FE/PE và lưu nhãn trong `retakeLabel`.

### JSON FE từ EOS

Nhận trực tiếp `ExamCode`, `GrammarQuestions`… Bộ đọc FE giữ hành vi cũ: lấy câu có ảnh từ Grammar/FillBlank/IndicateMistake và Reading/PassageQuestions. Đề chỉ có chữ, phần viết hoặc nối câu cần chuyển sang định dạng chuẩn để giữ đủ nội dung; không mặc định mọi biến thể EOS đều được hỗ trợ.

### JSON PE nguyên bản

```json
{
  "testName": "PE_PRO192_SU26_3_190626",
  "paperNo": 1,
  "numberOfPage": 1,
  "paperImage": ["<ảnh Base64>"],
  "givenMaterials": [{"questionNo": 1, "given": "<ZIP Base64>"}]
}
```

Các chuỗi trong dấu `<...>` phải được thay bằng dữ liệu thật.

- `paperNo` và `questionNo` phải là số nguyên dương.
- Nếu có `numberOfPage`, giá trị phải bằng số ảnh trong `paperImage`.
- PE phải có ít nhất một ảnh hoặc tài nguyên hợp lệ.
- Mã đề được tạo thành `<testName>_PaperNo<paperNo>`, loại luôn là PE.
- Mã môn là phần sau tiền tố PE đầu tên; nếu không có tiền tố thì lấy phần đầu tiên. Kỳ như SU26 được nhận diện theo nội dung thay vì vị trí cố định.
- ZIP được lưu theo câu hỏi để tải xuống; backend không chạy nội dung ZIP.
- Các trường session, mật khẩu và `gui` không được đưa vào payload PE lưu trong hàng đợi.

JSON hoặc UTF-8 bị hỏng sẽ bị từ chối. File PE mẫu đã cung cấp cần xuất lại từ nguồn bằng bộ tạo JSON chuẩn; không xóa/thay byte trong ảnh để ép đọc được.

### Gửi nhiều đề

Cấu trúc lô: `{eventId: "ma-lo-on-dinh", papers: [rawFE, rawPE, canonicalPaper, ...]}`. Đây là ký hiệu minh họa; thay từng phần tử bằng JSON thật. Không gửi mảng trần ở cấp cao nhất.

Khi gửi lại, giữ nguyên mã lô, nội dung và thứ tự. Mã sự kiện từng đề là `<eventId>#<index>`. Khi sửa nội dung lô, dùng mã mới. Đối tượng đề theo cấu trúc chuẩn cũ vẫn được hỗ trợ trong mảng.

## 3. ZIP trực tiếp hoặc tài nguyên qua URL

Tài nguyên PE theo cấu trúc chuẩn chọn một trong hai cách:

- `contentBase64`: ZIP Base64 trực tiếp.
- `sourceUrl` kèm `sha256`: server tải tài nguyên như luồng cũ.

Không gửi cả hai đồng thời. Ví dụ tài nguyên trực tiếp:

```json
{"filename":"question-1.zip","folderLabel":"Câu 1","contentBase64":"<ZIP Base64>"}
```

Server kiểm tra Base64, đuôi file và chữ ký định dạng ZIP, giới hạn dung lượng; tự tính MIME, kích thước và SHA-256. Hash cung cấp thêm phải khớp. Đây không phải cơ chế quét mã độc hoặc kiểm tra toàn bộ nội dung giải nén.

Tài nguyên URL vẫn phải qua kiểm tra HTTPS, domain được phép và hash. Cấu hình `allowed-resource-hosts` phải chứa domain thực tế. ZIP trực tiếp không cần URL MinIO.

## 4. Giới hạn mặc định

| Giới hạn | Giá trị |
|---|---:|
| Số đề/lô | 50 |
| Tổng body JSON | 32 MiB |
| Mỗi ảnh sau giải mã | 5 MiB |
| Mỗi tài nguyên | 50 MiB |

Base64 làm tăng dung lượng và vẫn tính vào body. Vì vậy ZIP inline 50 MiB không thể nằm trong body 32 MiB; có thể dùng URL cho tài nguyên lớn trong giới hạn cho phép. Reverse proxy cũng phải cho phép kích thước body đã cấu hình. Script mẫu chia lô theo cả số lượng lẫn byte; không gom cố định 50 đề bất kể dung lượng.

## 5. Đọc kết quả

| Kết quả | Ý nghĩa |
|---|---|
| HTTP 202 | Đã đưa đề mới vào hàng đợi, chưa có nghĩa đã xuất bản; không ghi FAIL. |
| HTTP 200 | Các đề được nhận đã tồn tại; vẫn kiểm tra trạng thái từng đề. |
| HTTP 400 | Sửa dữ liệu trước khi gửi lại. |
| HTTP 500 | Lưu response và danh tính request để đối chiếu log backend. |

Luôn kiểm tra `accepted`, `duplicate`, `rejected`, từng phần tử `results[]`, kể cả HTTP 2xx. Một kết quả trùng có thể tham chiếu lần xử lý đã thất bại.

Kiểm tra nội dung đề chuẩn hỗ trợ từ chối từng phần tử. JSON sai cú pháp, JSON nguyên bản sai cấu trúc hoặc phần tử không chuyển đổi được sẽ làm cả request bị từ chối trước khi đưa vào hàng đợi.

Lưu response đầy đủ, đặc biệt `receiptId`. Nhân viên tra cứu bằng xác thực admin:

```http
GET /api/v1/admin/exam/webhook-events/{receiptId}
```

Kiểm tra trạng thái cuối `done`/`failed`. Bản này chưa có endpoint tra cứu trạng thái riêng cho bên thứ ba bằng HMAC. Hai lỗi 500 lịch sử chưa thể chẩn đoán từ response bị cắt.

## 6. Script gửi mẫu

`backend/scripts/push_exam_webhook.py` dùng thư viện chuẩn Python, kiểm tra JSON/dung lượng và mặc định không gửi mạng. Script không tải dữ liệu MinIO và không cần script cũ của bên gửi.

Từ thư mục gốc dự án, chạy thử:

```sh
python backend/scripts/push_exam_webhook.py fe.json pe.json
```

Thiết lập `EXAM_WEBHOOK_CLIENT` và `EXAM_WEBHOOK_SECRET` trong biến môi trường, rồi gửi thật:

```sh
python backend/scripts/push_exam_webhook.py fe.json pe.json --url https://YOUR_HOST/api/v1/exam/webhook/papers --send
```

Thay YOUR_HOST bằng domain đã triển khai. Script ký đúng byte body, tạo mã lô theo nội dung/thứ tự và in đầy đủ response. Kiểm tra tại máy gửi chỉ xác nhận JSON và kích thước; backend vẫn kiểm tra nội dung ảnh/ZIP.

Kiểm thử tự động không gửi đề lên server thật. Nghiệm thu production cần triển khai backend, cấu hình client/domain và dùng bản PE nguồn hợp lệ.
