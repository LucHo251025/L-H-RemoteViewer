package com.example.ultraviewdemo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.geometry.Point2D;



/**
 * Test class để chạy cả viewer và host trên cùng một máy
 * Sử dụng localhost để test mouse events
 */
public class SingleMachineTest extends Application {
    
    private ImageView remoteImageView;
    private Robot robot;
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Khởi tạo JavaFX Robot để simulate host
        robot = new Robot();
        
        // Load FXML
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/ultraviewdemo/demoView/ultraViewRemote.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        
        // Setup remote container
        StackPane remoteContainer = (StackPane) scene.lookup("#remoteContainer");
        if (remoteContainer != null) {
            remoteContainer.getChildren().clear();
            
            // Tạo ImageView
            remoteImageView = new ImageView();
            remoteImageView.setFitWidth(1000);
            remoteImageView.setFitHeight(700);
            remoteImageView.setPreserveRatio(true);
            remoteImageView.getStyleClass().add("remote-image-view");
            
            // Enable mouse events
            remoteImageView.setMouseTransparent(false);
            remoteImageView.setPickOnBounds(true);
            remoteImageView.setFocusTraversable(true);
            
            // Tạo placeholder image
            WritableImage placeholder = new WritableImage(1000, 700);
            remoteImageView.setImage(placeholder);
            
            remoteContainer.getChildren().add(remoteImageView);
            
            // Setup mouse events
            setupMouseEvents();
            
            // Request focus
            Platform.runLater(() -> {
                remoteImageView.requestFocus();
                System.out.println("ImageView focus requested");
            });
        }
        
        primaryStage.setTitle("Single Machine Test - UltraView Remote Control");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        primaryStage.show();
        
        System.out.println("Single Machine Test started!");
        System.out.println("Click and drag on the ImageView to test mouse events");
        System.out.println("Press keys to test keyboard events");
    }
    
    private void setupMouseEvents() {
        System.out.println("Setting up mouse events for single machine test");
        
        // Mouse click events
        remoteImageView.setOnMouseClicked(event -> {
            System.out.println("Mouse clicked at: " + event.getX() + ", " + event.getY() + " button: " + event.getButton());
            handleMouseClick(event.getX(), event.getY(), event.getButton().toString());
        });
        
        // Mouse drag events
        remoteImageView.setOnMouseDragged(event -> {
            System.out.println("Mouse dragged to: " + event.getX() + ", " + event.getY());
            handleMouseDrag(event.getX(), event.getY());
        });
        
        // Mouse scroll events
        remoteImageView.setOnScroll(event -> {
            System.out.println("Mouse scrolled at: " + event.getX() + ", " + event.getY() + " delta: " + event.getDeltaY());
            handleMouseScroll(event.getX(), event.getY(), event.getDeltaY());
        });
        
        // Keyboard events
        remoteImageView.setOnKeyPressed(event -> {
            System.out.println("Key pressed: " + event.getCode());
            handleKeyPress(event.getCode().toString());
        });
        
        remoteImageView.setOnKeyReleased(event -> {
            System.out.println("Key released: " + event.getCode());
            handleKeyRelease(event.getCode().toString());
        });
        
        remoteImageView.setOnKeyTyped(event -> {
            System.out.println("Key typed: " + event.getCharacter());
            handleKeyTyped(event.getCharacter());
        });
    }
    
    private void handleMouseClick(double x, double y, String button) {
        try {
            // JavaFX Robot sử dụng Point2D cho coordinates
            Point2D screenPoint = new Point2D(x, y);
            
            // JavaFX Robot sử dụng MouseButton enum
            MouseButton mouseButton = "PRIMARY".equals(button) ? 
                MouseButton.PRIMARY :
                "SECONDARY".equals(button) ? MouseButton.SECONDARY :
                MouseButton.MIDDLE;
            
            robot.mouseMove(screenPoint);
            robot.mousePress(mouseButton);
            robot.mouseRelease(mouseButton);
            
            System.out.println("Executed mouse click at coordinates: " + x + ", " + y + " button: " + button);
        } catch (Exception e) {
            System.err.println("Error handling mouse click: " + e.getMessage());
        }
    }
    
    private void handleMouseDrag(double x, double y) {
        try {
            Point2D screenPoint = new Point2D(x, y);
            robot.mouseMove(screenPoint);
            System.out.println("Executed mouse drag to coordinates: " + x + ", " + y);
        } catch (Exception e) {
            System.err.println("Error handling mouse drag: " + e.getMessage());
        }
    }
    
    private void handleMouseScroll(double x, double y, double deltaY) {
        try {
            Point2D screenPoint = new Point2D(x, y);
            robot.mouseMove(screenPoint);
            robot.mouseWheel((int) (deltaY / 40));
            
            System.out.println("Executed mouse scroll at coordinates: " + x + ", " + y + " delta: " + deltaY);
        } catch (Exception e) {
            System.err.println("Error handling mouse scroll: " + e.getMessage());
        }
    }
    
    private void handleKeyPress(String keyCode) {
        try {
            KeyCode key = getKeyCode(keyCode);
            if (key != null) {
                robot.keyPress(key);
                System.out.println("Executed key press: " + keyCode);
            }
        } catch (Exception e) {
            System.err.println("Error handling key press: " + e.getMessage());
        }
    }
    
    private void handleKeyRelease(String keyCode) {
        try {
            KeyCode key = getKeyCode(keyCode);
            if (key != null) {
                robot.keyRelease(key);
                System.out.println("Executed key release: " + keyCode);
            }
        } catch (Exception e) {
            System.err.println("Error handling key release: " + e.getMessage());
        }
    }
    
    private void handleKeyTyped(String character) {
        try {
            if (character.length() == 1) {
                // JavaFX Robot không có keyTyped, chỉ có keyPress và keyRelease
                // Có thể sử dụng KeyCode cho các ký tự đặc biệt
                System.out.println("Key typed: " + character + " (JavaFX Robot doesn't support keyTyped directly)");
            }
        } catch (Exception e) {
            System.err.println("Error handling key typed: " + e.getMessage());
        }
    }
    
    private KeyCode getKeyCode(String keyCode) {
        try {
            return KeyCode.valueOf(keyCode);
        } catch (Exception e) {
            // Handle special cases
            switch (keyCode) {
                case "SPACE": return KeyCode.SPACE;
                case "ENTER": return KeyCode.ENTER;
                case "TAB": return KeyCode.TAB;
                case "ESCAPE": return KeyCode.ESCAPE;
                case "BACK_SPACE": return KeyCode.BACK_SPACE;
                case "DELETE": return KeyCode.DELETE;
                case "UP": return KeyCode.UP;
                case "DOWN": return KeyCode.DOWN;
                case "LEFT": return KeyCode.LEFT;
                case "RIGHT": return KeyCode.RIGHT;
                default: return null;
            }
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
