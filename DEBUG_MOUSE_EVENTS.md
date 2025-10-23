# Debug Mouse Events - UltraView Remote Control

## Vấn đề: Mouse events không được bắt

### Các bước debug đã thực hiện:

1. **Thêm debug logging** vào ViewerClient.java:
   - Log khi setup events
   - Log khi mouse events xảy ra
   - Log khi control connection được thiết lập

2. **Cải thiện ImageView setup**:
   - `setMouseTransparent(false)` - Đảm bảo ImageView có thể nhận mouse events
   - `setPickOnBounds(true)` - Đảm bảo events được bắt trong bounds
   - `setFocusTraversable(true)` - Đảm bảo ImageView có thể nhận focus
   - `requestFocus()` - Yêu cầu focus cho ImageView

3. **Thêm placeholder image**:
   - Tạo WritableImage để đảm bảo ImageView có content
   - Set background color để ImageView visible

4. **Tạo test class**:
   - `TestMouseEvents.java` để test mouse events độc lập

## Cách test:

### 1. Test mouse events cơ bản:
```bash
java -cp target/classes com.example.ultraviewdemo.TestMouseEvents
```
- Click, drag, scroll trên ImageView
- Kiểm tra console output

### 2. Test full remote control:
```bash
# Terminal 1: Start server
java -cp target/classes com.example.ultraviewdemo.server.RemoteServer

# Terminal 2: Start host
java -cp target/classes com.example.ultraviewdemo.client.HostClient

# Terminal 3: Start viewer
java -cp target/classes com.example.ultraviewdemo.client.ViewerClient
```

## Debug output cần kiểm tra:

### ViewerClient console output:
```
Setting up remote control events for ImageView
ImageView bounds: [x=0.0, y=0.0, width=1000.0, height=700.0]
ImageView created with size: 1000.0x700.0
ImageView focus requested
ImageView is focused: true
Attempting to connect to control server at localhost:5001
Control connection established successfully!
Mouse clicked at: 100.0, 200.0 button: PRIMARY
Sent control command: MOUSE_CLICK:100.0:200.0:PRIMARY
```

### HostClient console output:
```
Host control connected
Received control command: MOUSE_CLICK:100.0:200.0:PRIMARY
Executing mouse click at screen coordinates: 100, 200
```

## Các vấn đề có thể xảy ra:

1. **ImageView không nhận events**:
   - Kiểm tra `setMouseTransparent(false)`
   - Kiểm tra `setPickOnBounds(true)`
   - Kiểm tra ImageView có image không

2. **Control connection không được thiết lập**:
   - Kiểm tra server có chạy trên port 5001 không
   - Kiểm tra firewall settings
   - Kiểm tra console output cho connection errors

3. **Events không được forward**:
   - Kiểm tra RemoteServer có forward commands không
   - Kiểm tra HostClient có nhận commands không

## Files đã được cập nhật:

- `ViewerClient.java` - Thêm debug logging và cải thiện ImageView setup
- `TestMouseEvents.java` - Test class để kiểm tra mouse events
- `DEBUG_MOUSE_EVENTS.md` - File này

## Next steps:

1. Chạy TestMouseEvents để đảm bảo mouse events hoạt động
2. Chạy full demo và kiểm tra console output
3. Nếu vẫn không hoạt động, kiểm tra network connections
4. Có thể cần thêm more debug logging trong RemoteServer và HostClient
