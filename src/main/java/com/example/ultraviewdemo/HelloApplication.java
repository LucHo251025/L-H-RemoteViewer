package com.example.ultraviewdemo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("/com/example/ultraviewdemo/demoView/ultraViewRemote.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        
        // Load CSS
        String cssPath = HelloApplication.class.getResource("/com/example/ultraviewdemo/demoView/ultraview.css").toExternalForm();
        scene.getStylesheets().add(cssPath);
        
        stage.setTitle("UltraView Remote");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.show();
    }
}
