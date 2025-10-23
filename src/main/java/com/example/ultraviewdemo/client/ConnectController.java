package com.example.ultraviewdemo.client;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.function.Consumer;

public class ConnectController {
    @FXML private TextField serverField;
	@FXML private TextField hostIdField;
	@FXML private PasswordField passwordField;
	@FXML private Button connectButton;
	@FXML private Label errorLabel;

	public static class ConnectParams {
		public final String server;
		public final int port;
        public final String hostId;
		public final String password;
		public ConnectParams(String host, int port, String hostId, String password) {
			this.server = host;
			this.port = port;
            this.hostId = hostId;
			this.password = password;
		}

	}

	private Consumer<ConnectParams> onConnect;

	public void initialize() {
		connectButton.setOnAction(e -> handleConnect());
	}

	public void setOnConnect(Consumer<ConnectParams> onConnect) {
		this.onConnect = onConnect;
	}

	private void handleConnect() {
        String server = serverField.getText() == null ? "" : serverField.getText().trim();
		String hostId = hostIdField.getText() == null ? "" : hostIdField.getText().trim();
		String password = passwordField.getText() == null ? "" : passwordField.getText();
		if (hostId.isEmpty()) {
			errorLabel.setText("Please enter Host ID");
			return;
		}
		if (password.isEmpty()) {
			errorLabel.setText("Please enter password");
			return;
		}
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
				errorLabel.setText("Invalid port in Host ID");
				return;
			}
		}

		if (onConnect != null) {
			onConnect.accept(new ConnectParams(server, port, hostId, password));
		}
	}
}
