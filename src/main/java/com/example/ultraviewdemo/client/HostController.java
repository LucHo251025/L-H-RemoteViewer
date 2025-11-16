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

public class HostController {
	@FXML private TextArea ipTextArea;
	@FXML private TextField passwordField;
	@FXML private Button regenerateBtn;
	@FXML private TextField hostIdField;
	@FXML private TextField serverField;
	@FXML private Button startBtn;
	@FXML private Button stopBtn;
	@FXML private Label statusLabel;
	@FXML private Button openChatBtn;

	private Thread sharingThread;
	private volatile boolean running = false;

	private javafx.stage.Stage chatStage;
	private ListView<String> chatList;
	private TextField chatInput;

	public void initialize() {
		passwordField.setText(generatePassword());
		serverField.setText("localhost");
		String ips = listLocalIPv4();
		ipTextArea.setText(ips);
		hostIdField.setText(pickPrimaryHostId(ips));
		
		regenerateBtn.setOnAction(e -> passwordField.setText(generatePassword()));
		startBtn.setOnAction(e -> startSharing());
		stopBtn.setOnAction(e -> stopSharing());

		if (openChatBtn != null) openChatBtn.setOnAction(e -> toggleChat());
		HostClient.setChatSink(text -> Platform.runLater(() -> appendChat("Viewer", text)));
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

	private void toggleChat() {
		if (chatStage == null) createChatWindow();
		if (chatStage.isShowing()) chatStage.hide(); else chatStage.show();
	}

	private void createChatWindow() {
		chatList = new ListView<>();
		chatInput = new TextField();
		Button sendBtn = new Button("Send");
		sendBtn.setOnAction(e -> sendChat());
		chatInput.setOnAction(e -> sendChat());
		javafx.scene.layout.HBox inputRow = new javafx.scene.layout.HBox(8, chatInput, sendBtn);
		javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(8, chatList, inputRow);
		root.setPrefSize(320, 260);
		javafx.scene.Scene scene = new javafx.scene.Scene(root);
		chatStage = new javafx.stage.Stage();
		chatStage.setTitle("Host Chat");
		chatStage.setScene(scene);
	}

	private void appendChat(String who, String text) {
		if (chatList == null) return;
		chatList.getItems().add(("You".equals(who) ? "You: " : who + ": ") + text);
		chatList.scrollTo(chatList.getItems().size() - 1);
	}

	private void sendChat() {
		if (chatInput == null) return;
		String msg = chatInput.getText();
		if (msg == null) return;
		msg = msg.trim();
		if (msg.isEmpty()) return;
		HostClient.sendHostChat(msg, hostIdField != null ? hostIdField.getText() : "");
		appendChat("You", msg);
		chatInput.clear();
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
