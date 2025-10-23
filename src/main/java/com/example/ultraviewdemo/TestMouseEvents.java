package com.example.ultraviewdemo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;


public class TestMouseEvents extends Application {
    
    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane();
        
        // Tạo ImageView
        ImageView imageView = new ImageView();
        imageView.setFitWidth(800);
        imageView.setFitHeight(600);
//        imageView.setMinWidth(800);
//        imageView.setMinHeight(600);
        imageView.setMouseTransparent(false);
        imageView.setPickOnBounds(true);
        imageView.setFocusTraversable(true);
        
        // Tạo placeholder image
        WritableImage placeholder = new WritableImage(800, 600);
        imageView.setImage(placeholder);
        
        // Thêm mouse event handlers
        imageView.setOnMouseClicked(event -> {
            System.out.println("Mouse clicked at: " + event.getX() + ", " + event.getY() + " button: " + event.getButton());
        });
        
        imageView.setOnMouseDragged(event -> {
            System.out.println("Mouse dragged to: " + event.getX() + ", " + event.getY());
        });
        
        imageView.setOnMousePressed(event -> {
            System.out.println("Mouse pressed at: " + event.getX() + ", " + event.getY());
        });
        
        imageView.setOnMouseReleased(event -> {
            System.out.println("Mouse released at: " + event.getX() + ", " + event.getY());
        });
        
        imageView.setOnScroll(event -> {
            System.out.println("Mouse scrolled at: " + event.getX() + ", " + event.getY() + " delta: " + event.getDeltaY());
        });
        
        root.getChildren().add(imageView);
        
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Test Mouse Events");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // Request focus
        imageView.requestFocus();
        System.out.println("ImageView focus requested");
        System.out.println("ImageView is focused: " + imageView.isFocused());
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
