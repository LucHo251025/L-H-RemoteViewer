package com.example.ultraviewdemo;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class UltraViewController implements Initializable {

    @FXML
    private VBox connectionWorkstation01;
    
    @FXML
    private VBox connectionMacBook;
    
    @FXML
    private VBox connectionUbuntu;
    
    @FXML
    private Button connectMacButton;
    
    @FXML
    private Button disconnectWorkstationButton;
    
    @FXML
    private Button addConnectionButton;
    
    @FXML
    private Button lowQualityButton;
    
    @FXML
    private Button mediumQualityButton;
    
    @FXML
    private Button highQualityButton;
    
    @FXML
    private Button fullscreenButton;
    
    @FXML
    private Button audioButton;

    @FXML
    private ComboBox<String> qualityCombo;
    
    @FXML
    private Button securityButton;
    
    @FXML
    private Button reconnectButton;
    
    @FXML
    private Button disconnectActionButton;
    
    @FXML
    private ImageView mouseCursor;
    
    // Sidebar components
    @FXML
    private BorderPane rootPane;
    @FXML
    private VBox leftPane;
    @FXML
    private VBox leftCollapsed; // defined via fx:define in FXML
    @FXML
    private Button collapseSidebarBtn; // button inside leftPane header
    @FXML
    private Button openSidebarBtn; // button inside leftCollapsed

    // Audio state for toggle button
    private boolean isAudioOn = true;
    private Consumer<Boolean> onAudioToggle; // callback to Viewer to start/stop audio
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupEventHandlers();
        updateConnectionStatus();
        // Initialize audio button appearance
        if (audioButton != null) {
            applyAudioButtonState();
        }
        if (qualityCombo != null) {
            if (qualityCombo.getItems() != null && !qualityCombo.getItems().isEmpty()) {
                qualityCombo.setValue("High");
            }
            qualityCombo.setOnAction(e -> {
                String q = qualityCombo.getValue();
                if (q != null) setQuality(q);
            });
        }

        // Sidebar toggle
        if (collapseSidebarBtn != null) {
            collapseSidebarBtn.setOnAction(e -> collapseSidebar());
        }
        if (openSidebarBtn != null) {
            openSidebarBtn.setOnAction(e -> expandSidebar());
        }
    }

    private void setupEventHandlers() {
        // Connection buttons
        if (connectMacButton != null) {
            connectMacButton.setOnAction(event -> connectToMac());
        }
        
        if (disconnectWorkstationButton != null) {
            disconnectWorkstationButton.setOnAction(event -> disconnectWorkstation());
        }
        
        if (addConnectionButton != null) {
            addConnectionButton.setOnAction(event -> addNewConnection());
        }
        
        // Quality buttons (legacy) are kept if present, but dropdown is primary.
        if (lowQualityButton != null) lowQualityButton.setOnAction(event -> setQuality("Low"));
        if (mediumQualityButton != null) mediumQualityButton.setOnAction(event -> setQuality("Medium"));
        if (highQualityButton != null) highQualityButton.setOnAction(event -> setQuality("High"));
        
        // Display and audio buttons
        if (fullscreenButton != null) {
            fullscreenButton.setOnAction(event -> toggleFullscreen());
        }
        
        if (audioButton != null) {
            audioButton.setOnAction(event -> toggleAudio());
        }
        
        // Action buttons
        if (securityButton != null) {
            securityButton.setOnAction(event -> showSecuritySettings());
        }
        
        if (reconnectButton != null) {
            reconnectButton.setOnAction(event -> reconnect());
        }
        
        if (disconnectActionButton != null) {
            disconnectActionButton.setOnAction(event -> disconnectCurrentSession());
        }
    }

    private void connectToMac() {
        System.out.println("Connecting to MacBook Pro...");
        // Update UI to show connecting state
        updateConnectionStatus();
    }

    private void disconnectWorkstation() {
        System.out.println("Disconnecting from Workstation-01...");
        // Update UI to show disconnected state
        updateConnectionStatus();
    }

    private void addNewConnection() {
        System.out.println("Adding new connection...");
        // Open dialog to add new connection
    }

    private void setQuality(String quality) {
        System.out.println("Setting quality to: " + quality);
        // Update quality buttons active state
        updateQualityButtons(quality);
    }

    private void toggleFullscreen() {
        System.out.println("Toggling fullscreen...");
        // Toggle fullscreen mode
    }

    private void toggleAudio() {
        System.out.println("Toggling audio...");
        // Toggle audio on/off (UI state) and notify callback
        isAudioOn = !isAudioOn;
        applyAudioButtonState();
        if (onAudioToggle != null) {
            onAudioToggle.accept(isAudioOn);
        }
    }

    private void showSecuritySettings() {
        System.out.println("Showing security settings...");
        // Open security settings dialog
    }

    private void reconnect() {
        System.out.println("Reconnecting...");
        // Reconnect to current session
    }

    private void disconnectCurrentSession() {
        System.out.println("Disconnecting current session...");
        // Disconnect from current session
    }

    private void updateConnectionStatus() {
        // Update connection status indicators
        // This would be called when connection status changes
    }

    private void updateQualityButtons(String activeQuality) {
        // Update the active state of quality buttons
        if (lowQualityButton != null) {
            lowQualityButton.getStyleClass().remove("active");
            if ("Low".equals(activeQuality)) {
                lowQualityButton.getStyleClass().add("active");
            }
        }
        
        if (mediumQualityButton != null) {
            mediumQualityButton.getStyleClass().remove("active");
            if ("Medium".equals(activeQuality)) {
                mediumQualityButton.getStyleClass().add("active");
            }
        }
        
        if (highQualityButton != null) {
            highQualityButton.getStyleClass().remove("active");
            if ("High".equals(activeQuality)) {
                highQualityButton.getStyleClass().add("active");
            }
        }
    }

    public void setOnAudioToggle(Consumer<Boolean> handler) {
        this.onAudioToggle = handler;
    }

    private void applyAudioButtonState() {
        if (audioButton == null) return;
        if (isAudioOn) {
            audioButton.setText("🔊 Audio On");
            audioButton.setStyle("-fx-background-color: #e5e7eb; -fx-text-fill: #374151; -fx-background-radius: 8; -fx-padding: 6 12; -fx-font-weight: 700;");
        } else {
            audioButton.setText("🔇 Audio Off");
            audioButton.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-background-radius: 8; -fx-padding: 6 12; -fx-font-weight: 700;");
        }
    }

    private void collapseSidebar() {
        if (rootPane == null || leftPane == null || leftCollapsed == null) return;
        rootPane.setLeft(leftCollapsed);
    }

    private void expandSidebar() {
        if (rootPane == null || leftPane == null || leftCollapsed == null) return;
        rootPane.setLeft(leftPane);
    }
}
