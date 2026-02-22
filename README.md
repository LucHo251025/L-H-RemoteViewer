# 🖥️ L-H-RemoteViewer

L-H-RemoteViewer là một ứng dụng **Java (Maven-based)** cho phép xử lý và điều khiển sự kiện chuột/bàn phím từ xa thông qua cơ chế Remote Control.  
Dự án tập trung vào việc:

- Ghi nhận sự kiện chuột (Mouse Events)
- Xử lý điều khiển từ xa
- Sử dụng Java Robot API / JavaFX Robot
- Debug và khắc phục lỗi liên quan đến Input Events

---

# 🎯 Mục tiêu dự án

Dự án được xây dựng nhằm:

- Hiểu cách hoạt động của Mouse & Keyboard Events trong Java
- Xây dựng cơ chế điều khiển từ xa
- Test và debug hành vi của Robot API
- Thử nghiệm giải pháp remote trên cùng một máy và nhiều máy

---

# 🧠 Các tính năng chính

## 1️⃣ Mouse Event Handling
- Bắt sự kiện click chuột
- Bắt sự kiện di chuyển chuột
- Ghi nhận tọa độ màn hình
- Debug lỗi event propagation

## 2️⃣ Remote Control Logic
- Gửi sự kiện chuột đến máy khác
- Mô phỏng click bằng Robot API
- Điều khiển từ xa thông qua socket/logic nội bộ

## 3️⃣ Robot API Integration
- Sử dụng `java.awt.Robot`
- Sử dụng JavaFX Robot (nếu có)
- Xử lý lỗi phổ biến:
  - Robot không click đúng vị trí
  - Mouse event không nhận
  - Delay giữa các sự kiện

## 4️⃣ Debug & Fix Documentation
Repo có nhiều file hướng dẫn chi tiết:

- `DEBUG_MOUSE_EVENTS.md`
- `MOUSE_EVENTS_FIX.md`
- `JAVAFX_ROBOT_FIX.md`
- `REMOTE_CONTROL_GUIDE.md`
- `SINGLE_MACHINE_TEST.md`
- `IMPLEMENTATION_SUMMARY.md`
- `FINAL_SOLUTION.md`

Các file này mô tả:
- Cách test từng bước
- Cách fix lỗi cụ thể
- Cách triển khai giải pháp hoàn chỉnh

---

# 🏗️ Cấu trúc dự án

```
L-H-RemoteViewer/
│
├── .mvn/                       # Maven Wrapper
├── src/
│   └── main/
│       └── java/               # Source code chính
│
├── pom.xml                     # Maven configuration
│
├── DEBUG_MOUSE_EVENTS.md
├── MOUSE_EVENTS_FIX.md
├── JAVAFX_ROBOT_FIX.md
├── REMOTE_CONTROL_GUIDE.md
├── SINGLE_MACHINE_TEST.md
├── IMPLEMENTATION_SUMMARY.md
├── FINAL_SOLUTION.md
```

---

# ⚙️ Công nghệ sử dụng

- **Java 11+**
- **Maven**
- **Java AWT Robot**
- **JavaFX (nếu sử dụng)**
- Mouse & Keyboard Event Handling

---

# 🛠️ Yêu cầu hệ thống

- JDK 11 trở lên
- Maven 3.x
- Windows / macOS / Linux

---

# 🚀 Cài đặt & chạy dự án

## 1️⃣ Clone repository

```bash
git clone https://github.com/LucHo251025/L-H-RemoteViewer.git
cd L-H-RemoteViewer
```

---

## 2️⃣ Build bằng Maven

```bash
mvn clean install
```

---

## 3️⃣ Chạy ứng dụng

Nếu có main class:

```bash
mvn exec:java -Dexec.mainClass="your.package.MainClass"
```

Hoặc mở project bằng IntelliJ IDEA / Eclipse và chạy trực tiếp.

---

# 🧪 Test trên một máy

Tham khảo file:

```
SINGLE_MACHINE_TEST.md
```

Hướng dẫn:
- Chạy server và client trên cùng một máy
- Kiểm tra event được gửi và xử lý
- Xác minh Robot thực hiện đúng hành động

---

# 🐞 Debug Mouse Events

Nếu gặp lỗi:

- Chuột không click đúng vị trí
- Robot không phản hồi
- Event không nhận

Xem file:

```
DEBUG_MOUSE_EVENTS.md
MOUSE_EVENTS_FIX.md
JAVAFX_ROBOT_FIX.md
```

Các file này cung cấp:
- Nguyên nhân lỗi
- Cách khắc phục
- Ví dụ code sửa lỗi

---

# 🔐 Lưu ý bảo mật

Ứng dụng remote control có thể ảnh hưởng đến bảo mật hệ thống.  
Chỉ nên sử dụng trong:

- Môi trường test
- Mạng nội bộ
- Mục đích học tập

Không khuyến nghị triển khai trên môi trường public nếu chưa có cơ chế xác thực và mã hóa.

---

# 📘 Tài liệu triển khai

## IMPLEMENTATION_SUMMARY.md
Tóm tắt toàn bộ cách hoạt động của hệ thống.

## FINAL_SOLUTION.md
Giải pháp hoàn chỉnh sau khi fix toàn bộ lỗi.

## REMOTE_CONTROL_GUIDE.md
Hướng dẫn từng bước triển khai remote control.

---

# 👨‍💻 Tác giả

Luc Ho  
GitHub: https://github.com/LucHo251025

---

# 📄 License

Hiện chưa khai báo license.  
Bạn có thể thêm:

- MIT License
- Apache 2.0
- GPL

Tùy mục đích sử dụng.

---

# ⭐ Đóng góp

1. Fork repo
2. Tạo branch mới
3. Commit thay đổi
4. Tạo Pull Request

---

# 📌 Tổng kết

L-H-RemoteViewer là dự án nghiên cứu & thực nghiệm về:

- Java Mouse Event Handling
- Robot API
- Remote Control Logic
- Debug & System Interaction

Phù hợp cho:
- Sinh viên học Java nâng cao
- Người muốn hiểu cơ chế điều khiển từ xa
- Nghiên cứu xử lý input hệ thống

---

⭐ Nếu bạn thấy dự án hữu ích, hãy cho repo một star!
