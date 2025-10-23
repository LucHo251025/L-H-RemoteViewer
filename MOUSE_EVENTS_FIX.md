# Sửa lỗi Mouse Events - UltraView Remote Control

## Vấn đề ban đầu
Mouse events không được bắt trên ImageView trong ViewerClient, khiến viewer không thể điều khiển màn hình host.

## Nguyên nhân có thể
1. ImageView không có image nên không thể nhận mouse events
2. ImageView không có focus
3. ImageView không được setup đúng cách để nhận events
4. Control connection không được thiết lập

## Các sửa đổi đã thực hiện

### 1. ViewerClient.java - Cải thiện ImageView setup

```java
// Thêm placeholder image
WritableImage placeholder = new WritableImage(1000, 700);
remoteImageView.setImage(placeholder);

// Enable mouse events
remoteImageView.setMouseTransparent(false);
remoteImageView.setPickOnBounds(true);
remoteImageView.setFocusTraversable(true);

// Set minimum size
remoteImageView.setMinWidth(1000.0);
remoteImageView.setMinHeight(700.0);

// Request focus
Platform.runLater(() -> {
    remoteImageView.requestFocus();
    System.out.println("ImageView focus requested");
    System.out.println("ImageView is focused: " + remoteImageView.isFocused());
});
```

### 2. Thêm debug logging

```java
// Trong setupRemoteControlEvents()
System.out.println("Setting up remote control events for ImageView");
System.out.println("ImageView bounds: " + imageView.getBoundsInLocal());

// Trong mouse event handlers
System.out.println("Mouse clicked at: " + event.getX() + ", " + event.getY() + " button: " + event.getButton());
System.out.println("Sent control command: MOUSE_CLICK:" + x + ":" + y + ":" + button);

// Trong control connection
System.out.println("Attempting to connect to control server at " + serverHost + ":" + (serverPort + 1));
System.out.println("Control connection established successfully!");
```

### 3. Tạo test class

- `TestMouseEvents.java` - Test mouse events độc lập
- `DEBUG_MOUSE_EVENTS.md` - Hướng dẫn debug

## Cách test

### 1. Test mouse events cơ bản:
```bash
java -cp target/classes com.example.ultraviewdemo.TestMouseEvents
```

### 2. Test full remote control:
```bash
# Terminal 1: Start server
java -cp target/classes com.example.ultraviewdemo.server.RemoteServer

# Terminal 2: Start host  
java -cp target/classes com.example.ultraviewdemo.client.HostClient

# Terminal 3: Start viewer
java -cp target/classes com.example.ultraviewdemo.client.ViewerClient
```

## Debug output mong đợi

### ViewerClient console:
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

## Files đã được cập nhật

- `ViewerClient.java` - Cải thiện ImageView setup và thêm debug logging
- `TestMouseEvents.java` - Test class mới
- `DEBUG_MOUSE_EVENTS.md` - Hướng dẫn debug
- `MOUSE_EVENTS_FIX.md` - File này

## Kết quả mong đợi

Sau khi áp dụng các sửa đổi này:
1. ImageView sẽ có thể nhận mouse events
2. Mouse clicks, drags, scrolls sẽ được gửi đến host
3. Host sẽ thực thi các lệnh điều khiển
4. Viewer có thể điều khiển màn hình host thành công

## Lưu ý

- Đảm bảo server chạy trên cả port 5000 và 5001
- Kiểm tra firewall settings
- Nếu vẫn không hoạt động, kiểm tra console output để debug
