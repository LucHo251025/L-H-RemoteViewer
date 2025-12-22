package com.example.ultraviewdemo.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Optional;

public class SmallHostControlController {

    @FXML private VBox rootBox;
    @FXML private Label statusLabel;
    @FXML private Button stopBtn;
    @FXML private Button audioBtn;
    @FXML private Button chatBtn;
    @FXML private VBox menuPane;
    @FXML private Button toggleMenuBtn;

    private boolean menuVisible = true;
    private boolean audioActive = false;
    private boolean waitingForAudioPermission = false;

    @FXML
    public void initialize() {
        // Gắn sự kiện cho các nút
        if (stopBtn != null) stopBtn.setOnAction(e -> handleStopSharing());
        if (chatBtn != null) chatBtn.setOnAction(e -> openChat());
        if (toggleMenuBtn != null) toggleMenuBtn.setOnAction(e -> toggleMenu());
        if (audioBtn != null) audioBtn.setOnAction(e -> requestToggleAudio());

        // Đợi UI render xong thì tính toán vị trí để dính vào cạnh phải
        Platform.runLater(this::dockToRightSide);
    }

    /**
     * Đưa cửa sổ về phía bên phải màn hình (Right Drawer)
     */
    private void dockToRightSide() {
        try {
            Stage stage = (Stage) rootBox.getScene().getWindow();
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();

            // X = Width màn hình - Width cửa sổ
            stage.setX(bounds.getWidth() - stage.getWidth());
            // Y = Cách top 150px (tùy chỉnh)
            stage.setY(150);
        } catch (Exception ignored) {}
    }

    // --- LOGIC AUDIO (Request / Accept / Deny) ---

    private void requestToggleAudio() {
        if (audioActive) {
            // Nếu đang bật -> Tắt luôn (không cần xin phép)
            HostClient.sendAudioCommand("OFF");
            updateAudioUI(false);
            HostClient.enableAudioSystem(false);
        } else {
            if (waitingForAudioPermission) return; // Đang chờ thì ko spam

            // Gửi yêu cầu xin bật
            waitingForAudioPermission = true;
            audioBtn.setText("Waiting...");
            audioBtn.setDisable(true);
            HostClient.sendAudioCommand("REQUEST");
        }
    }

    // Được gọi từ HostClient khi nhận phản hồi từ Viewer
    public void onAudioResponse(boolean accepted) {
        Platform.runLater(() -> {
            waitingForAudioPermission = false;
            audioBtn.setDisable(false);

            if (accepted) {
                updateAudioUI(true);
                HostClient.enableAudioSystem(true); // Bật phần cứng Mic/Loa
            } else {
                updateAudioUI(false);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Phía Viewer đã từ chối bật Audio.");
                alert.setHeaderText(null);
                alert.show();
            }
        });
    }

    // Được gọi từ HostClient khi Viewer chủ động xin bật
    public void onAudioRequestFromViewer() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Yêu cầu Audio");
            alert.setHeaderText("Viewer muốn bật hội thoại âm thanh.");
            alert.setContentText("Bạn có đồng ý bật Mic và Loa không?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                // Đồng ý
                HostClient.sendAudioCommand("ACCEPT");
                updateAudioUI(true);
                HostClient.enableAudioSystem(true);
            } else {
                // Từ chối
                HostClient.sendAudioCommand("DENY");
            }
        });
    }

    public void updateAudioUI(boolean isOn) {
        audioActive = isOn;
        if (audioBtn != null) {
            if (isOn) {
                audioBtn.setText("🔊 ON");
                audioBtn.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-background-radius: 8; -fx-font-weight: bold;");
            } else {
                audioBtn.setText("🎤 Audio");
                audioBtn.setStyle("-fx-background-color: #e5e7eb; -fx-text-fill: #374151; -fx-background-radius: 8;");
            }
        }
    }

    // --- CÁC LOGIC KHÁC ---

    private void handleStopSharing() {
        try {
            HostClient.requestViewerDisconnectBecauseHostStopping();
        } catch (Exception ignore) {
        }
        Stage stage = (Stage) stopBtn.getScene().getWindow();
        if (stage != null) stage.close();
        Platform.exit();
        System.exit(0);
    }

    private void openChat() {
        try {
            HostChatWindow.initIfNeeded();
            HostChatWindow.show();
        } catch (Exception ignored) {}
    }

    private void toggleMenu() {
        if (menuPane == null || toggleMenuBtn == null) return;
        menuVisible = !menuVisible;
        menuPane.setVisible(menuVisible);
        menuPane.setManaged(menuVisible);
        toggleMenuBtn.setText(menuVisible ? "⟩" : "⟨");

        // Cập nhật lại vị trí X sau khi resize chiều ngang
        Platform.runLater(() -> {
            Stage stage = (Stage) rootBox.getScene().getWindow();
            stage.sizeToScene();
            dockToRightSide();
        });
    }
}