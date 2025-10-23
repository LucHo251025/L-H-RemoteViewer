# Test Mouse Events trên cùng một máy

## Vấn đề
Bạn đang chạy cả viewer và host trên cùng một máy, nên cần phương án test khác để kiểm tra mouse events.

## Giải pháp

### 1. SimpleMouseTest.java - Test cơ bản
Test đơn giản để kiểm tra mouse events trên ImageView:

```bash
java -cp target/classes com.example.ultraviewdemo.SimpleMouseTest
```

**Tính năng:**
- Hiển thị ImageView với placeholder image
- Bắt tất cả mouse events (click, drag, scroll, enter, exit)
- Bắt keyboard events (press, release, typed)
- Hiển thị status trên UI và console
- Không cần network connection

### 2. SingleMachineTest.java - Test với Robot
Test với Java Robot để simulate host behavior:

```bash
java -cp target/classes com.example.ultraviewdemo.SingleMachineTest
```

**Tính năng:**
- Sử dụng Java Robot để thực thi mouse/keyboard actions
- Scale coordinates từ ImageView sang screen coordinates
- Thực thi các actions thực tế trên màn hình
- Test đầy đủ remote control functionality

## Cách sử dụng

### Test 1: SimpleMouseTest
1. Chạy: `java -cp target/classes com.example.ultraviewdemo.SimpleMouseTest`
2. Click, drag, scroll trên ImageView
3. Nhấn phím khi ImageView có focus
4. Kiểm tra console output và status label

### Test 2: SingleMachineTest
1. Chạy: `java -cp target/classes com.example.ultraviewdemo.SingleMachineTest`
2. Click, drag, scroll trên ImageView
3. Nhấn phím khi ImageView có focus
4. Quan sát các actions được thực thi trên màn hình

## Debug output mong đợi

### SimpleMouseTest:
```
ImageView focus requested
ImageView is focused: true
ImageView bounds: [x=0.0, y=0.0, width=800.0, height=600.0]
Mouse clicked at: 100.0, 200.0 button: PRIMARY
Mouse dragged to: 150.0, 250.0
Key pressed: A
```

### SingleMachineTest:
```
Single Machine Test started!
Click and drag on the ImageView to test mouse events
Press keys to test keyboard events
Setting up mouse events for single machine test
ImageView focus requested
Mouse clicked at: 100.0, 200.0 button: PRIMARY
Executed mouse click at screen coordinates: 100, 200
```

## Lưu ý

1. **SimpleMouseTest**: Chỉ test việc bắt events, không thực thi actions
2. **SingleMachineTest**: Test đầy đủ với Robot, thực thi actions thực tế
3. Cả hai đều chạy trên cùng một máy, không cần network
4. Đảm bảo ImageView có focus để nhận keyboard events

## Troubleshooting

### Mouse events không được bắt:
1. Kiểm tra `setMouseTransparent(false)`
2. Kiểm tra `setPickOnBounds(true)`
3. Kiểm tra ImageView có image không
4. Kiểm tra focus status

### Keyboard events không được bắt:
1. Click vào ImageView trước khi nhấn phím
2. Kiểm tra `setFocusTraversable(true)`
3. Kiểm tra `requestFocus()` được gọi

### Robot actions không hoạt động:
1. Kiểm tra Java Robot permissions
2. Kiểm tra screen coordinates scaling
3. Kiểm tra key code mapping

## Files đã tạo

- `SimpleMouseTest.java` - Test cơ bản mouse events
- `SingleMachineTest.java` - Test với Robot simulation
- `SINGLE_MACHINE_TEST.md` - File này

## Kết quả mong đợi

Sau khi chạy các test này:
1. Mouse events sẽ được bắt thành công
2. Console output sẽ hiển thị event information
3. SingleMachineTest sẽ thực thi actions thực tế
4. Bạn có thể xác nhận rằng mouse events hoạt động đúng

Điều này sẽ giúp bạn debug và xác nhận rằng mouse events đang hoạt động trước khi test với network connection.
