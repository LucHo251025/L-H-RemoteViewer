# Sửa lỗi JavaFX Robot - SingleMachineTest.java

## Vấn đề
Code ban đầu sử dụng `java.awt.Robot` nhưng bạn đã thay đổi import sang `javafx.scene.robot.Robot`. JavaFX Robot có API khác hoàn toàn so với AWT Robot.

## Các thay đổi đã thực hiện

### 1. Import statements
```java
// Trước (AWT Robot)
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

// Sau (JavaFX Robot)
import javafx.scene.robot.Robot;
import javafx.scene.input.MouseButton;
import javafx.scene.input.KeyCode;
import javafx.geometry.Point2D;
```

### 2. Robot initialization
```java
// Trước (AWT Robot)
try {
    robot = new Robot();
    screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
} catch (AWTException e) {
    e.printStackTrace();
    return;
}

// Sau (JavaFX Robot)
robot = new Robot();
```

### 3. Mouse handling
```java
// Trước (AWT Robot)
int screenX = (int) (x * screenRect.width / 1000);
int screenY = (int) (y * screenRect.height / 700);
int buttonMask = "PRIMARY".equals(button) ? InputEvent.BUTTON1_DOWN_MASK : ...;
robot.mouseMove(screenX, screenY);
robot.mousePress(buttonMask);
robot.mouseRelease(buttonMask);

// Sau (JavaFX Robot)
Point2D screenPoint = new Point2D(x, y);
MouseButton mouseButton = "PRIMARY".equals(button) ? 
    MouseButton.PRIMARY : MouseButton.SECONDARY;
robot.mouseMove(screenPoint);
robot.mousePress(mouseButton);
robot.mouseRelease(mouseButton);
```

### 4. Keyboard handling
```java
// Trước (AWT Robot)
int key = getKeyCode(keyCode); // Returns int
robot.keyPress(key);
robot.keyRelease(key);

// Sau (JavaFX Robot)
KeyCode key = getKeyCode(keyCode); // Returns KeyCode
robot.keyPress(key);
robot.keyRelease(key);
```

### 5. KeyCode mapping
```java
// Trước (AWT Robot)
private int getKeyCode(String keyCode) {
    try {
        return KeyEvent.class.getField("VK_" + keyCode).getInt(null);
    } catch (Exception e) {
        switch (keyCode) {
            case "SPACE": return KeyEvent.VK_SPACE;
            // ...
        }
    }
}

// Sau (JavaFX Robot)
private KeyCode getKeyCode(String keyCode) {
    try {
        return KeyCode.valueOf(keyCode);
    } catch (Exception e) {
        switch (keyCode) {
            case "SPACE": return KeyCode.SPACE;
            // ...
        }
    }
}
```

## Sự khác biệt chính

### AWT Robot vs JavaFX Robot

| Aspect | AWT Robot | JavaFX Robot |
|--------|-----------|--------------|
| Coordinates | `int x, int y` | `Point2D point` |
| Mouse buttons | `InputEvent.BUTTON1_DOWN_MASK` | `MouseButton.PRIMARY` |
| Key codes | `int` (VK_ constants) | `KeyCode` enum |
| Key typed | `KeyEvent.getExtendedKeyCodeForChar()` | Không hỗ trợ trực tiếp |
| Screen size | `Toolkit.getDefaultToolkit().getScreenSize()` | Không cần |

## Lưu ý quan trọng

1. **JavaFX Robot không hỗ trợ keyTyped**: Chỉ có keyPress và keyRelease
2. **Coordinates**: JavaFX Robot sử dụng Point2D thay vì int x, y
3. **Mouse buttons**: Sử dụng MouseButton enum thay vì InputEvent masks
4. **Key codes**: Sử dụng KeyCode enum thay vì int constants

## Cách test

```bash
java -cp target/classes com.example.ultraviewdemo.SingleMachineTest
```

## Kết quả mong đợi

Sau khi sửa đổi:
1. Mouse events sẽ hoạt động với JavaFX Robot
2. Keyboard events sẽ hoạt động với KeyCode enum
3. Console output sẽ hiển thị thông tin debug
4. Robot sẽ thực thi actions thực tế trên màn hình

## Files đã được cập nhật

- `SingleMachineTest.java` - Sửa đổi để sử dụng JavaFX Robot
- `JAVAFX_ROBOT_FIX.md` - File này

Code bây giờ đã tương thích với JavaFX Robot và sẵn sàng để test!
