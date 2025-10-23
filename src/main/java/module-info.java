module com.example.ultraviewdemo {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;
    requires java.desktop;
    requires javafx.graphics;
    requires org.json;


    exports com.example.ultraviewdemo.client;
    opens com.example.ultraviewdemo.client to javafx.fxml;
    opens com.example.ultraviewdemo to javafx.fxml;
    exports com.example.ultraviewdemo;
}