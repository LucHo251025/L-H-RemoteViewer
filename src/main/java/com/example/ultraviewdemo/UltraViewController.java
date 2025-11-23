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
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.geometry.Pos;
import javafx.stage.Stage;

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
    @FXML
    private HBox topBar;
    @FXML
    private HBox bottomBar;

    // Audio state for toggle button (default OFF for clarity/stability)
    private boolean isAudioOn = false;
    private Consumer<Boolean> onAudioToggle; // callback to Viewer to start/stop audio

    // Stage reference and listener flag for fullscreen toggling
    private Stage stage;
    private boolean fullscreenListenerInitialized = false;

    // Chat UI
    @FXML private ListView<ChatMessage> chatListView;
    @FXML private TextField chatInputField;
    @FXML private Button sendChatBtn;
    private Consumer<String> onChatSend;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupEventHandlers();
        updateConnectionStatus();
        // Initialize audio button appearance
        if (audioButton != null) {
            applyAudioButtonState();
        }
        // Chat wiring
        if (sendChatBtn != null) {
            sendChatBtn.setOnAction(e -> sendChatInternal());
        }
        if (chatInputField != null) {
            chatInputField.setOnAction(e -> sendChatInternal()); // Enter to send
        }
        if (chatListView != null) {
            chatListView.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(ChatMessage item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    Label bubble = new Label(item.text);
                    bubble.setWrapText(true);
                    bubble.setMaxWidth(180);
                    bubble.setStyle(item.self
                            ? "-fx-background-color: #DCFCE7; -fx-text-fill: #065F46; -fx-padding: 8 10; -fx-background-radius: 12;"
                            : "-fx-background-color: #E5E7EB; -fx-text-fill: #111827; -fx-padding: 8 10; -fx-background-radius: 12;");
                    Label name = new Label(item.self ? "You" : (item.sender != null ? item.sender : "Peer"));
                    name.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B7280;");
                    VBox msgBox = new VBox(4, name, bubble);
                    HBox row = new HBox();
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                    if (item.self) {
                        row.getChildren().addAll(spacer, msgBox);
                        row.setAlignment(Pos.CENTER_RIGHT);
                    } else {
                        row.getChildren().addAll(msgBox, spacer);
                        row.setAlignment(Pos.CENTER_LEFT);
                    }
                    setGraphic(row);
                    setText(null);
                }
            });
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
        // Always resolve Stage from the current Scene to avoid relying on external injection
        javafx.stage.Window window = null;
        if (fullscreenButton != null && fullscreenButton.getScene() != null) {
            window = fullscreenButton.getScene().getWindow();
        } else if (rootPane != null && rootPane.getScene() != null) {
            window = rootPane.getScene().getWindow();
        }

        if (!(window instanceof javafx.stage.Stage)) {
            System.err.println("[UltraViewController] Cannot resolve Stage for fullscreen toggle");
            return;
        }

        javafx.stage.Stage localStage = (javafx.stage.Stage) window;
        this.stage = localStage;

        // Install a listener once so that if user exits fullscreen via ESC,
        // the chrome (sidebars, bars) is restored correctly.
        if (!fullscreenListenerInitialized) {
            fullscreenListenerInitialized = true;
            localStage.fullScreenProperty().addListener((obs, wasFull, isFull) -> {
                boolean showChrome = !isFull;
                if (leftPane != null) {
                    leftPane.setVisible(showChrome);
                    leftPane.setManaged(showChrome);
                }
                if (bottomBar != null) {
                    bottomBar.setVisible(showChrome);
                    bottomBar.setManaged(showChrome);
                }
                if (topBar != null) {
                    topBar.setVisible(showChrome);
                    topBar.setManaged(showChrome);
                }
                if (fullscreenButton != null) {
                    fullscreenButton.setText(isFull ? "Exit Fullscreen" : "Fullscreen");
                }
            });
        }

        boolean newState = !localStage.isFullScreen();
        localStage.setFullScreen(newState);

        if (fullscreenButton != null) {
            fullscreenButton.setText(newState ? "Exit Fullscreen" : "Fullscreen");
        }

        boolean showChrome = !newState;
        if (leftPane != null) {
            leftPane.setVisible(showChrome);
            leftPane.setManaged(showChrome);
        }
        if (bottomBar != null) {
            bottomBar.setVisible(showChrome);
            bottomBar.setManaged(showChrome);
        }
        if (topBar != null) {
            topBar.setVisible(showChrome);
            topBar.setManaged(showChrome);
        }
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

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setOnChatSend(Consumer<String> handler) {
        this.onChatSend = handler;
    }

    public void addChatMessage(String sender, String text) {
        if (chatListView != null) {
            boolean self = "You".equalsIgnoreCase(sender);
            chatListView.getItems().add(new ChatMessage(self, sender, text));
            chatListView.scrollTo(chatListView.getItems().size() - 1);
        }
    }

    private void sendChatInternal() {
        if (chatInputField == null) return;
        String msg = chatInputField.getText();
        if (msg == null) return;
        msg = msg.trim();
        if (msg.isEmpty()) return;
        if (onChatSend != null) onChatSend.accept(msg);
        chatInputField.clear();
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

    private static class ChatMessage {
        final boolean self;
        final String sender;
        final String text;
        ChatMessage(boolean self, String sender, String text) {
            this.self = self;
            this.sender = sender;
            this.text = text;
        }
    }
}
