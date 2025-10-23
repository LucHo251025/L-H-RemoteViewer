package com.example.ultraviewdemo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Test đơn giản để kiểm tra mouse events trên ImageView
 * Chạy trên cùng một máy, không cần network
 */
public class SimpleMouseTest extends Application {
    
    private Label statusLabel;
    
    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(10);
        
        // Status label
        statusLabel = new Label("Click and drag on the ImageView below to test mouse events");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-padding: 10px;");
        
        // Tạo ImageView
        ImageView imageView = new ImageView();
        imageView.setFitWidth(800);
        imageView.setFitHeight(600);
        imageView.setMouseTransparent(false);
        imageView.setPickOnBounds(true);
        imageView.setFocusTraversable(true);
        
        // Tạo placeholder image
        WritableImage placeholder = new WritableImage(800, 600);
        imageView.setImage(placeholder);
        
        // Thêm mouse event handlers
        imageView.setOnMouseClicked(event -> {
            String message = String.format("Mouse clicked at: %.1f, %.1f button: %s", 
                event.getX(), event.getY(), event.getButton());
            System.out.println(message);
            statusLabel.setText(message);
        });
        
        imageView.setOnMouseDragged(event -> {
            String message = String.format("Mouse dragged to: %.1f, %.1f", 
                event.getX(), event.getY());
            System.out.println(message);
            statusLabel.setText(message);
        });
        
        imageView.setOnMousePressed(event -> {
            String message = String.format("Mouse pressed at: %.1f, %.1f", 
                event.getX(), event.getY());
            System.out.println(message);
            statusLabel.setText(message);
        });
        
        imageView.setOnMouseReleased(event -> {
            String message = String.format("Mouse released at: %.1f, %.1f", 
                event.getX(), event.getY());
            System.out.println(message);
            statusLabel.setText(message);
        });
        
        imageView.setOnScroll(event -> {
            String message = String.format("Mouse scrolled at: %.1f, %.1f delta: %.1f", 
                event.getX(), event.getY(), event.getDeltaY());
            System.out.println(message);
            statusLabel.setText(message);
        });
        
        imageView.setOnMouseEntered(event -> {
            statusLabel.setText("Mouse entered ImageView");
        });
        
        imageView.setOnMouseExited(event -> {
            statusLabel.setText("Mouse exited ImageView");
        });
        
        // Keyboard events
        imageView.setOnKeyPressed(event -> {
            String message = "Key pressed: " + event.getCode();
            System.out.println(message);
            statusLabel.setText(message);
        });
        
        imageView.setOnKeyReleased(event -> {
            String message = "Key released: " + event.getCode();
            System.out.println(message);
            statusLabel.setText(message);
        });
        
        imageView.setOnKeyTyped(event -> {
            String message = "Key typed: " + event.getCharacter();
            System.out.println(message);
            statusLabel.setText(message);
        });
        
        root.getChildren().addAll(statusLabel, imageView);
        
        Scene scene = new Scene(root, 800, 700);
        primaryStage.setTitle("Simple Mouse Test - UltraView");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Request focus
        imageView.requestFocus();
        System.out.println("ImageView focus requested");
        System.out.println("ImageView is focused: " + imageView.isFocused());
        System.out.println("ImageView bounds: " + imageView.getBoundsInLocal());
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
