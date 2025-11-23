package com.example.ultraviewdemo.client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.function.Consumer;

public class ConnectHostController {
    // Connect side
    @FXML private TextField connectHostIdField;
    @FXML private PasswordField connectPasswordField;
    @FXML private Button connectBtn;
    @FXML private Label connectErrorLabel;

    // Host side
    @FXML private TextArea ipTextArea;
    @FXML private TextField hostPasswordField;
    @FXML private Button regenerateBtn;
    @FXML private TextField hostIdField;
    @FXML private TextField serverField;
    @FXML private Button startBtn;
    @FXML private Button stopBtn;
    @FXML private Label statusLabel;

    private Thread sharingThread;
    private volatile boolean running = false;

    private Consumer<ConnectController.ConnectParams> onConnect;

    public void setOnConnect(Consumer<ConnectController.ConnectParams> onConnect) {
        this.onConnect = onConnect;
    }

    @FXML
    public void initialize() {
        // Connect wiring
        connectBtn.setOnAction(e -> handleConnect());

        // Host init
        hostPasswordField.setText(generatePassword());
        serverField.setText("localhost");
        String ips = listLocalIPv4();
        ipTextArea.setText(ips);
        hostIdField.setText(pickPrimaryHostId(ips));

        // Host wiring
        regenerateBtn.setOnAction(e -> hostPasswordField.setText(generatePassword()));
        startBtn.setOnAction(e -> startSharing());
        stopBtn.setOnAction(e -> stopSharing());
    }

    private void handleConnect() {
        String server = serverField.getText().isEmpty() ? "localhost" : serverField.getText().trim();
        String hostId = connectHostIdField.getText() == null ? "" : connectHostIdField.getText().trim();
        String password = connectPasswordField.getText() == null ? "" : connectPasswordField.getText();
        if (hostId.isEmpty()) { connectErrorLabel.setText("Please enter Host ID"); return; }
        if (password.isEmpty()) { connectErrorLabel.setText("Please enter password"); return; }
        String host = hostId;
        int port = 5000;
        int idx = hostId.lastIndexOf(':');
        if (idx > 0 && idx < hostId.length() - 1) {
            host = hostId.substring(0, idx);
            String p = hostId.substring(idx + 1);
            try {
                port = Integer.parseInt(p);
                if (port <= 0 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                connectErrorLabel.setText("Invalid port in Host ID");
                return;
            }
        }
        if (onConnect != null) {
            onConnect.accept(new ConnectController.ConnectParams(server, port,hostId, password));
        }
    }

    private void startSharing() {
        if (running) return;
        running = true;
        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        statusLabel.setText("Sharing...");
        String server = serverField.getText().isEmpty() ? "localhost" : serverField.getText().trim();
        String password = hostPasswordField.getText();
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
                        String name = nif.getName();
                        // Căn cột: tên interface rộng cố định, IP nằm cột bên phải
                        sb.append(String.format("%-18s %s%n", name, addr.getHostAddress()));
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
        String[] ips = ipsText.split("\n");
        String firstLine = ips[ips.length - 1];
        int idx = firstLine.lastIndexOf(' ');
        String ip = idx >= 0 ? firstLine.substring(idx + 1) : firstLine;
        return ip.replace(".", "");
    }

    private static String generatePassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}


