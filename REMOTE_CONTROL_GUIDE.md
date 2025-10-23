# UltraView Remote Control - Hướng dẫn sử dụng

## Tính năng mới: Điều khiển màn hình từ xa

Dự án UltraView đã được cập nhật với tính năng điều khiển màn hình từ xa, cho phép viewer có thể điều khiển máy tính host thông qua giao diện JavaFX.

## Cách sử dụng

### 1. Khởi động Server
```bash
java -cp target/classes com.example.ultraviewdemo.server.RemoteServer
```

### 2. Khởi động Host (Máy tính được điều khiển)
```bash
java -cp target/classes com.example.ultraviewdemo.client.HostClient
```
- Nhấn "Start Sharing" để bắt đầu chia sẻ màn hình
- Host sẽ tự động kết nối với server trên port 5000 (screen sharing) và 5001 (control)

### 3. Khởi động Viewer (Máy tính điều khiển)
```bash
java -cp target/classes com.example.ultraviewdemo.client.ViewerClient
```
- Nhập Host ID và password để kết nối
- Sau khi kết nối thành công, viewer có thể:
  - Click chuột trái/phải/giữa
  - Drag chuột
  - Scroll chuột
  - Nhấn phím
  - Gõ text

## Tính năng điều khiển

### Mouse Events
- **Click**: Click chuột trái/phải/giữa tại vị trí bất kỳ trên màn hình
- **Drag**: Kéo chuột để di chuyển con trỏ
- **Scroll**: Cuộn chuột để scroll trang

### Keyboard Events
- **Key Press/Release**: Nhấn và thả phím
- **Key Typed**: Gõ ký tự
- **Special Keys**: Hỗ trợ các phím đặc biệt như Enter, Space, Tab, Arrow keys, etc.

## Kiến trúc hệ thống

```
ViewerClient (Port 5001) -> RemoteServer -> HostClient
     ↓                           ↓              ↓
Mouse/Keyboard Events    Forward Commands   Execute Actions
```

## Lưu ý bảo mật

- Tất cả kết nối đều yêu cầu password
- Control events được gửi qua kênh riêng biệt (port 5001)
- Host có thể dừng chia sẻ bất kỳ lúc nào

## Troubleshooting

1. **Không thể kết nối**: Kiểm tra firewall và đảm bảo ports 5000, 5001 được mở
2. **Điều khiển không hoạt động**: Đảm bảo host đã bắt đầu sharing và viewer đã kết nối thành công
3. **Lag trong điều khiển**: Có thể do mạng chậm, thử giảm chất lượng video

## Cấu trúc code

- `ViewerClient.java`: Xử lý UI viewer và gửi control events
- `HostClient.java`: Nhận và thực thi control commands
- `RemoteServer.java`: Forward control events từ viewer đến host
- `ConnectHostController.java`: UI controller cho kết nối

