# L-H RemoteViewer

L-H RemoteViewer là ứng dụng remote desktop viết bằng JavaFX, mô phỏng luồng hoạt động giống UltraViewer/TeamViewer: một máy chia sẻ màn hình ở chế độ Host, máy còn lại kết nối bằng Host ID và mật khẩu để xem, điều khiển từ xa, chat, gửi file và trao đổi âm thanh.

## Tính năng chính

- Chia sẻ màn hình theo thời gian thực qua socket TCP.
- Kết nối Host/Viewer bằng Host ID và mật khẩu phiên.
- Điều khiển chuột từ xa: click, kéo thả, di chuyển và cuộn.
- Điều khiển bàn phím từ xa.
- Chat giữa Host và Viewer trong phiên kết nối.
- Gửi và nhận file, file nhận được lưu vào thư mục `Downloads/UltraViewFiles`.
- Hỗ trợ âm thanh hai chiều giữa Host và Viewer.
- Cửa sổ điều khiển phiên kết nối với trạng thái live connection, fullscreen, bật/tắt audio và disconnect.
- Giao diện desktop bằng JavaFX/FXML.

## Công nghệ sử dụng

- Java 21
- JavaFX 21
- Maven
- Java Socket
- Java Robot API
- Java Sound API
- FXML/CSS

## Cấu trúc dự án

```text
src/main/java/com/example/ultraviewdemo
├── client/              # Host, Viewer, controller UI, chat, file/audio/control logic
├── server/              # Directory server và remote server
├── helpers/             # Hằng số và helper đọc/ghi socket
├── models/              # Model message truyền qua socket
├── HelloApplication.java # Entry point JavaFX chính
└── Launcher.java

src/main/resources/com/example/ultraviewdemo
├── demoView/            # FXML/CSS cho giao diện chính
└── icons/               # Icon ứng dụng
```

## Yêu cầu cài đặt

- JDK 21 hoặc mới hơn
- Maven hoặc Maven Wrapper có sẵn trong dự án
- Hai máy cùng mạng LAN, hoặc một máy để test local
- Quyền truy cập màn hình, chuột, bàn phím và microphone nếu hệ điều hành yêu cầu

Kiểm tra Java:

```bash
java -version
```

## Cách chạy

### 1. Clone dự án

```bash
git clone https://github.com/<your-username>/L-H-RemoteViewer.git
cd L-H-RemoteViewer
```

### 2. Build dự án

Trên Windows:

```bash
.\mvnw.cmd clean compile
```

Trên Linux/macOS:

```bash
./mvnw clean compile
```

### 3. Chạy Directory Server

Directory Server dùng để Host đăng ký phiên và Viewer tra cứu thông tin kết nối.

Chạy class sau trong IDE:

```text
com.example.ultraviewdemo.server.DirectoryServer
```

Server mặc định lắng nghe tại port:

```text
7000
```

### 4. Chạy ứng dụng JavaFX

Trên Windows:

```bash
.\mvnw.cmd javafx:run
```

Trên Linux/macOS:

```bash
./mvnw javafx:run
```

## Hướng dẫn sử dụng

### Máy Host

1. Mở ứng dụng.
2. Nhập địa chỉ Directory Server ở ô `Server`, ví dụ `localhost` khi test cùng máy hoặc IP của máy chạy server trong mạng LAN.
3. Kiểm tra Host ID và Session Password được tạo trong khung Host.
4. Nhấn `Start Sharing`.
5. Gửi Host ID và password cho Viewer.

### Máy Viewer

1. Mở ứng dụng.
2. Nhập địa chỉ Directory Server.
3. Nhập Host ID và password do Host cung cấp.
4. Nhấn `Connect`.
5. Sau khi kết nối thành công, Viewer có thể xem màn hình Host và thao tác chuột/bàn phím từ xa.

## Port sử dụng

| Port | Mục đích |
| --- | --- |
| `7000` | Directory Server |
| `5000` | Stream màn hình |
| `5001` | Điều khiển chuột/bàn phím, chat, file |
| `5002` | Audio từ Host sang Viewer |
| `5003` | Audio từ Viewer sang Host |

Nếu chạy qua firewall hoặc mạng khác nhau, hãy đảm bảo các port trên được mở.

## Test trên cùng một máy

Bạn có thể test nhanh bằng cách chạy Directory Server và ứng dụng trên cùng máy, sau đó để `Server = localhost`.

Một số script/debug tài liệu có sẵn trong repo:

- `test_mouse_events.bat`
- `test_mouse_events.sh`
- `REMOTE_CONTROL_GUIDE.md`
- `SINGLE_MACHINE_TEST.md`
- `DEBUG_MOUSE_EVENTS.md`

## Lưu ý

- Ứng dụng sử dụng Java Robot API để chụp màn hình và điều khiển input, vì vậy một số hệ điều hành có thể yêu cầu cấp quyền accessibility/screen recording.
- Chất lượng stream phụ thuộc vào tốc độ mạng LAN và cấu hình máy Host.
- Đây là dự án học tập/thực nghiệm về remote desktop, socket, JavaFX và xử lý input/audio thời gian thực.

## Tác giả

L-H RemoteViewer được phát triển như một dự án JavaFX Remote Desktop.
