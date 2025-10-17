module com.example.lhremoteviewer {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.lhremoteviewer to javafx.fxml;
    exports com.example.lhremoteviewer;
}