package com.example.ultraviewdemo.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;

public class HostController {
    @FXML private TextArea ipTextArea;
    @FXML private TextField passwordField;
    @FXML private Button regenerateBtn;
    @FXML private TextField hostIdField;
    @FXML private TextField serverField;
    @FXML private Button startBtn;
    @FXML private Button stopBtn;
    @FXML private Label statusLabel;
    // Chat UI
    @FXML private Button openChatBtn;
    @FXML private VBox chatPane;
    @FXML private ListView<String> hostChatList;
    @FXML private TextField hostChatInput;
    @FXML private Button hostSendBtn;

    private Thread sharingThread;
    private volatile boolean running = false;

    public void initialize() {
        passwordField.setText(generatePassword());
        serverField.setText("localhost");
        String ips = listLocalIPv4();
        ipTextArea.setText(ips);
        hostIdField.setText(pickPrimaryHostId(ips));
        
        regenerateBtn.setOnAction(e -> passwordField.setText(generatePassword()));
        startBtn.setOnAction(e -> startSharing());
        stopBtn.setOnAction(e -> stopSharing());
        if (openChatBtn != null) openChatBtn.setOnAction(e -> showChatPane());
        if (hostSendBtn != null) hostSendBtn.setOnAction(e -> sendHostChat());
        if (hostChatInput != null) hostChatInput.setOnAction(e -> sendHostChat());
    }

    private void startSharing() {
        if (running) return;
        running = true;
        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        statusLabel.setText("Sharing...");
        String server = serverField.getText().isEmpty() ? "localhost" : serverField.getText().trim();
        String password = passwordField.getText();
        String hostId = hostIdField.getText();

        sharingThread = new Thread(() -> {
            try {
                HostClient.shareLoop(server, 5000, hostId, password, () -> running);
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
            } finally {
                Platform.runLater(() -> {
                    startBtn.setDisable(false);
                    stopBtn.setDisable(true);
                    statusLabel.setText("Stopped");
                });
                running = false;
            }
        }, "HostShareThread");
        sharingThread.setDaemon(true);
        sharingThread.start();
    }

    private void stopSharing() { running = false; }

    // Chat controls
    private void showChatPane() {
        if (chatPane != null) { chatPane.setVisible(true); chatPane.setManaged(true); }
    }

    @FXML
    public void hideChatPane() {
        if (chatPane != null) { chatPane.setVisible(false); chatPane.setManaged(false); }
    }

    private void sendHostChat() {
        if (hostChatInput == null) return;
        String text = hostChatInput.getText();
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        // Only send if a viewer control connection exists
        if (!HostClient.isControlConnected()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Chat");
            alert.setHeaderText(null);
            alert.setContentText("Chưa có Viewer kết nối hoặc kết nối đã mất. Không thể gửi chat.");
            alert.showAndWait();
            return;
        }
        hostChatInput.clear();
        addChatMessage("Host", text);
        HostClient.sendChatFromUI(text);
    }

    public void addChatMessage(String sender, String text) {
        if (hostChatList == null) return;
        hostChatList.getItems().add((sender != null ? sender + ": " : "") + text);
        hostChatList.scrollTo(hostChatList.getItems().size() - 1);
    }

    private static String listLocalIPv4() {
        StringBuilder sb = new StringBuilder();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface nif = nets.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        sb.append(nif.getName()).append(" - ").append(addr.getHostAddress()).append('\n');
                    }
                }
            }
        } catch (IOException e) {
            sb.append("(cannot list IPs: ").append(e.getMessage()).append(")");
        }
        return sb.toString().trim();
    }

    private static String pickPrimaryHostId(String ipsText) {
        if (ipsText == null || ipsText.isEmpty()) return "localhost:5000";
        String firstLine = ipsText.split("\n")[0];
        int idx = firstLine.lastIndexOf(' ');
        String ip = idx >= 0 ? firstLine.substring(idx + 1) : firstLine;
        return ip + ":5000";
    }

    private static String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
