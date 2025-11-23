    package com.example.ultraviewdemo.client;

import com.example.ultraviewdemo.helpers.Constant;
import com.example.ultraviewdemo.helpers.SocketMethodHelpers;
import com.example.ultraviewdemo.models.MessageModel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.*;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ViewerClient extends Application {
    private String serverHost = "localhost";
    private int serverPort = 5000; // unused in P2P; kept for compatibility
    private String hostId = "";
    private String password = "";
    private ImageView remoteImageView;
    private Socket controlSocket;
    private MessageModel viewerControlModel;
    private com.example.ultraviewdemo.UltraViewController uiController;
    private final Object controlWriteLock = new Object();

    // Mode: "viewer" (default) or "host"
    private String mode = "viewer";
    
    // Host mode variables
    private static volatile Socket hostControlSocketRef;
    private static final Object hostControlWriteLock = new Object();
    private static volatile String hostIdRef;
    private static volatile BooleanSupplier hostShouldRun;
    private static volatile HostAudioManager hostAudioManager;
    private static volatile HostUplinkManager hostUplinkManager;
    
    // Audio
    private volatile Socket audioSocket;
    private volatile Thread audioThread;
    private volatile boolean audioEnabled = false;
    private volatile Socket uplinkSocket;
    private volatile Thread uplinkThread;
    private volatile TargetDataLine micLine;
    private volatile SourceDataLine speakerLine;

        // P2P target resolved from directory server
    private String hostIp;
    private int hostStreamPort;
    private int hostControlPort;

    @Override
    public void start(Stage stage) throws Exception {
        // Check for --mode=host parameter
        Application.Parameters params = getParameters();
        if (params.getRaw().contains("--mode=host")) {
            mode = "host";
            System.out.println("[Viewer] Running in HOST mode");
        } else {
            System.out.println("[Viewer] Running in VIEWER mode (default)");
        }
        
        if ("host".equals(mode)) {
            startHostMode(stage);
        } else {
            startViewerMode(stage);
        }
    }
    
    private void startViewerMode(Stage stage) throws Exception {
        FXMLLoader connectLoader = new FXMLLoader(getClass().getResource("/com/example/ultraviewdemo/demoView/connect-host.fxml"));
        Scene connectScene = new Scene(connectLoader.load(), 480, 360);
        String cssPath = getClass().getResource("/com/example/ultraviewdemo/demoView/ultraview.css").toExternalForm();
        connectScene.getStylesheets().add(cssPath);
        stage.setTitle("UltraView Remote - Connect");
        stage.setScene(connectScene);
        stage.show();

        ConnectHostController controller = connectLoader.getController();
        controller.setOnConnect(params -> {
            this.serverHost = params.server;
            this.serverPort = params.port;
            this.hostId = params.hostId;
            this.password = params.password;

            // Query directory server for host endpoints, then connect directly
            new Thread(() -> {
                try (Socket dir = new Socket(serverHost, 7000)) {
                    MessageModel q = new MessageModel(Constant.ACTION_VIEWER_QUERY, "viewer");
                    q.setPartner_id(hostId);
                    q.setPartner_password(password);
                    SocketMethodHelpers.sendMessage(dir, q);

                    MessageModel resp = SocketMethodHelpers.readMessage(dir);
                    if (!resp.isSuccess()) {
                        String code = resp.getMessage();
                        if ("HOST_NOT_FOUND".equals(code)) {
                            Platform.runLater(() -> showInfo("Host not found or not registered yet."));
                        } else if ("AUTH_FAILED".equals(code)) {
                            Platform.runLater(() -> showError("Authentication failed. Please check Host ID/password."));
                        } else {
                            Platform.runLater(() -> showError("Directory query failed: " + code));
                        }
                        return;
                    }

                    String[] parts = resp.getMessage().split(":");
                    if (parts.length < 3) {
                        Platform.runLater(() -> showError("Invalid directory payload."));
                        return;
                    }
                    hostIp = parts[0];
                    hostStreamPort = Integer.parseInt(parts[1]);
                    hostControlPort = Integer.parseInt(parts[2]);

                    Platform.runLater(() -> {
                        try {
                            openControlWindow();
                            startNetworkConnection();
                            startControlConnection();
                        } catch (IOException e) {
                            showError("Failed to load control UI: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("Cannot reach directory server: " + e.getMessage()));
                }
            }).start();
        });
    }
    
    private void startHostMode(Stage stage) throws Exception {
        System.out.println("[Host] Starting in HOST mode");
        
        // Set up host ID and password (for now, use defaults)
        this.hostId = "host-" + System.currentTimeMillis();
        this.password = "123456";
        hostIdRef = this.hostId;
        
        // Create shouldRun flag
        AtomicBoolean shouldRunFlag = new AtomicBoolean(true);
        hostShouldRun = shouldRunFlag::get;
        
        // Show host UI with chat window
        Platform.runLater(() -> {
            try {
                System.out.println("[Host] Initializing HostChatWindow...");
                HostChatWindow.initIfNeeded();
                System.out.println("[Host] Setting onSend callback...");
                HostChatWindow.setOnSend(this::sendChatFromHostUI);
                System.out.println("[Host] Showing HostChatWindow...");
                HostChatWindow.show();
                System.out.println("[Host] HostChatWindow initialized successfully");
            } catch (Exception e) {
                System.err.println("[Host] Error initializing chat window: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // Also set callback when control connection is accepted
        Platform.runLater(() -> {
            try {
                HostChatWindow.initIfNeeded();
                HostChatWindow.setOnSend(this::sendChatFromHostUI);
                System.out.println("[Host] onSend callback set again for safety");
            } catch (Exception e) {
                System.err.println("[Host] Error setting callback: " + e.getMessage());
            }
        });
        
        // Start host servers in background thread
        new Thread(() -> {
            try {
                startHostShareLoop(5000, shouldRunFlag);
            } catch (Exception e) {
                System.err.println("[Host] Error in host share loop: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Host error: " + e.getMessage()));
            }
        }, "HostShareLoop").start();
    }
    
    private void startHostShareLoop(int basePort, AtomicBoolean shouldRun) throws Exception {
        System.out.println("[Host] Starting host servers on base port: " + basePort);
        
        // Create server sockets
        ServerSocket streamServer = createServerSocket(basePort);
        ServerSocket controlServer = createServerSocket(basePort + 1);
        ServerSocket audioServer = createServerSocket(basePort + 2);
        ServerSocket uplinkServer = createServerSocket(basePort + 3);
        
        // Create audio and uplink managers (simplified versions)
        HostAudioManager audioManager = new HostAudioManager(audioServer, shouldRun::get);
        HostUplinkManager uplinkManager = new HostUplinkManager(uplinkServer, shouldRun::get);
        hostAudioManager = audioManager;
        hostUplinkManager = uplinkManager;
        
        // Start audio and uplink accept loops
        audioManager.startAcceptLoop();
        uplinkManager.startAcceptLoop();
        
        // Register with directory server
        String localIp = InetAddress.getLocalHost().getHostAddress();
        try (Socket dirSocket = new Socket("localhost", 7000)) {
            MessageModel reg = new MessageModel(Constant.ACTION_HOST_REGISTER, hostId);
            reg.setOwner_password(password);
            SocketMethodHelpers.sendMessage(dirSocket, reg);
            System.out.println("[Host] Registered with directory server as " + hostId);
        }
        
        // Start control accept
        startHostControlAccept(controlServer, hostId, password, shouldRun::get, audioManager, uplinkManager);
        
        // Start screen sharing
        try (Socket streamSocket = streamServer.accept()) {
            System.out.println("[Host] Stream connection accepted from: " + streamSocket.getRemoteSocketAddress());
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            DataOutputStream out = new DataOutputStream(streamSocket.getOutputStream());
            
            try {
                while (shouldRun.get()) {
                    BufferedImage screen = robot.createScreenCapture(screenRect);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(screen, "jpg", baos);
                    byte[] data = baos.toByteArray();
                    MessageModel screenModel = new MessageModel(Constant.ACTION_HOST, hostId);
                    screenModel.setOwner_password(password);
                    screenModel.setData(data);
                    SocketMethodHelpers.sendMessage(streamSocket, screenModel);
                    
                    Thread.sleep(100);
                }
            } finally {
                try { out.close(); } catch (Exception ignore) {}
            }
        } finally {
            // Close all servers
            try { streamServer.close(); } catch (Exception ignore) {}
            try { controlServer.close(); } catch (Exception ignore) {}
            try { audioServer.close(); } catch (Exception ignore) {}
            try { uplinkServer.close(); } catch (Exception ignore) {}
            System.out.println("[Host] Host servers closed");
        }
    }
    
    private ServerSocket createServerSocket(int port) throws IOException {
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));
            return ss;
        } catch (BindException be) {
            throw new IOException("Port " + port + " is already in use. Please close other instances or choose a different base port.", be);
        }
    }
    
    private void startHostControlAccept(ServerSocket controlServer, String hostId, String password, BooleanSupplier shouldRun, HostAudioManager audioManager, HostUplinkManager uplinkManager) {
        new Thread(() -> {
            try (Socket controlSocket = controlServer.accept()) {
                System.out.println("[Host] Control connection accepted from viewer: " + controlSocket.getRemoteSocketAddress());
                hostControlSocketRef = controlSocket;
                
                // Ensure HostChatWindow callback is set when viewer connects
                Platform.runLater(() -> {
                    try {
                        HostChatWindow.initIfNeeded();
                        HostChatWindow.setOnSend(this::sendChatFromHostUI);
                        HostChatWindow.show();
                        HostChatWindow.addMessage("System", "Viewer connected successfully!");
                        System.out.println("[Host] HostChatWindow shown when viewer connected");
                    } catch (Exception e) {
                        System.err.println("[Host] Error setting callback on connect: " + e.getMessage());
                    }
                });
                
                // Start reader thread to handle incoming control messages
                Thread readerThread = new Thread(() -> {
                    try {
                        while (shouldRun.getAsBoolean() && !controlSocket.isClosed()) {
                            try {
                                MessageModel incoming = SocketMethodHelpers.readMessage(controlSocket);
                                if (incoming == null) {
                                    System.out.println("[Host] Incoming control message is null, breaking reader loop");
                                    break;
                                }
                                
                                String msg = incoming.getMessage();
                                System.out.println("[Host] Host received control message: " + msg);
                                
                                if (msg != null) {
                                    handleHostControlCommand(msg, audioManager, uplinkManager, controlSocket, hostId);
                                }
                            } catch (Exception e) {
                                System.err.println("[Host] Error reading control message: " + e.getMessage());
                                e.printStackTrace();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Host] Control reader thread error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }, "HostControlReader");
                readerThread.setDaemon(true);
                readerThread.start();
                
                // Keep this thread alive to maintain connection
                while (shouldRun.getAsBoolean() && !controlSocket.isClosed()) {
                    Thread.sleep(100);
                }
                
            } catch (Exception e) {
                System.err.println("[Host] Control connection error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("[Host] Control connection closed");
                hostControlSocketRef = null;
            }
        }, "HostControlAccept").start();
    }
    
    private void handleHostControlCommand(String command, HostAudioManager audioManager, HostUplinkManager uplinkManager, Socket controlSocket, String hostId) {
        try {
            if (command == null) return;
            String[] parts = command.split(":");
            if (parts.length < 2) return;
            
            String action = parts[0];
            
            switch (action) {
                case "AUDIO":
                    if (parts.length >= 2) {
                        String state = parts[1];
                        boolean enable = "ON".equalsIgnoreCase(state);
                        if (enable) audioManager.enable(); else audioManager.disable();
                    }
                    break;
                case "AUDIO_UP":
                    if (parts.length >= 2) {
                        String state = parts[1];
                        boolean enable = "ON".equalsIgnoreCase(state);
                        if (enable) uplinkManager.enable(); else uplinkManager.disable();
                    }
                    break;
                case "CHAT":
                    String text = command.length() > 5 ? command.substring(5) : "";
                    System.out.println("[Host] Received CHAT from viewer: '" + text + "'");
                    Platform.runLater(() -> {
                        try {
                            HostChatWindow.initIfNeeded();
                            HostChatWindow.setOnSend(this::sendChatFromHostUI);
                            HostChatWindow.show();
                            HostChatWindow.addMessage("Viewer", text);
                        } catch (Exception e) {
                            System.err.println("Error showing chat message: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                    break;
                case "CHAT_ACK":
                    String ackText = command.length() > 9 ? command.substring(9) : "";
                    System.out.println("[Host] Received CHAT_ACK from viewer for: '" + ackText + "'");
                    break;
                // Handle mouse/keyboard commands if needed in the future
                default:
                    System.out.println("[Host] Unknown control command: " + action);
            }
        } catch (Exception e) {
            System.err.println("[Host] Error handling control command: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendChatFromHostUI(String text) {
        System.out.println("[Host] sendChatFromHostUI called with text: '" + text + "'");
        try {
            if (hostIdRef == null || hostIdRef.isEmpty()) {
                System.err.println("[Host] sendChatFromUI aborted: hostIdRef is null/empty");
                showHostChatError("Không gửi được chat: hostId bị rỗng hoặc null.");
                return;
            }
            
            if (hostControlSocketRef == null || hostControlSocketRef.isClosed()) {
                System.err.println("[Host] sendChatFromUI aborted: hostControlSocketRef is null or closed");
                showHostChatError("Không gửi được chat: chưa có Viewer kết nối hoặc kết nối điều khiển đã mất.");
                return;
            }
            
            String trimmed = (text == null) ? "" : text.trim();
            if (trimmed.isEmpty()) return;
            
            System.out.println("[Host] Preparing to send chat message from host to viewer: '" + trimmed + "'");
            
            synchronized (hostControlWriteLock) {
                MessageModel reply = new MessageModel(Constant.ACTION_HOST, hostIdRef);
                reply.setMessage("CHAT:" + trimmed);
                SocketMethodHelpers.sendMessageNoTrack(hostControlSocketRef, reply);
                System.out.println("[Host] Chat message sent to viewer over control socket: '" + trimmed + "'");
            }
            
            // Update UI chat window
            Platform.runLater(() -> {
                HostChatWindow.initIfNeeded();
                HostChatWindow.show();
                HostChatWindow.addMessage("Host", trimmed);
            });
        } catch (Exception e) {
            showHostChatError("Lỗi gửi chat: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void showHostChatError(String message) {
        System.err.println(message);
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Chat Error");
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            } catch (Exception ignored) {}
        });
    }
    
    // Simplified audio manager for host mode
    private static class HostAudioManager {
        private final ServerSocket server;
        private final BooleanSupplier shouldRun;
        private volatile boolean enabled = false;
        private volatile Socket client;
        private volatile Thread acceptThread;
        
        HostAudioManager(ServerSocket server, BooleanSupplier shouldRun) {
            this.server = server;
            this.shouldRun = shouldRun;
        }
        
        void startAcceptLoop() {
            acceptThread = new Thread(() -> {
                try {
                    while (shouldRun.getAsBoolean()) {
                        client = server.accept();
                        System.out.println("[Host] Audio client connected: " + client.getRemoteSocketAddress());
                        if (enabled) startSender();
                    }
                } catch (IOException e) {
                    if (shouldRun.getAsBoolean()) System.err.println("Audio accept stopped: " + e.getMessage());
                }
            }, "AudioAcceptLoop");
            acceptThread.start();
        }
        
        void enable() { enabled = true; }
        void disable() { enabled = false; }
        
        private void startSender() {
            // Simplified - just accept connection
            System.out.println("[Host] Audio sender started");
        }
    }
    
    // Simplified uplink manager for host mode
    private static class HostUplinkManager {
        private final ServerSocket server;
        private final BooleanSupplier shouldRun;
        private volatile boolean enabled = false;
        private volatile Socket client;
        private volatile Thread acceptThread;
        
        HostUplinkManager(ServerSocket server, BooleanSupplier shouldRun) {
            this.server = server;
            this.shouldRun = shouldRun;
        }
        
        void startAcceptLoop() {
            acceptThread = new Thread(() -> {
                try {
                    while (shouldRun.getAsBoolean()) {
                        client = server.accept();
                        System.out.println("[Host] Uplink client connected: " + client.getRemoteSocketAddress());
                        if (enabled) startReceiver();
                    }
                } catch (IOException e) {
                    if (shouldRun.getAsBoolean()) System.err.println("Uplink accept stopped: " + e.getMessage());
                }
            }, "UplinkAcceptLoop");
            acceptThread.start();
        }
        
        void enable() { enabled = true; }
        void disable() { enabled = false; }
        
        private void startReceiver() {
            // Simplified - just accept connection
            System.out.println("[Host] Uplink receiver started");
        }
    }

        private void showError(String msg) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Connection Error");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        }

        private void showInfo(String msg) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        }

        private void openControlWindow() throws IOException {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/com/example/ultraviewdemo/demoView/ultraViewRemote.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
            String cssPath = getClass().getResource("/com/example/ultraviewdemo/demoView/ultraview.css").toExternalForm();
            scene.getStylesheets().add(cssPath);

            // Wire UI audio toggle to network control
            com.example.ultraviewdemo.UltraViewController ctrl = fxmlLoader.getController();
            this.uiController = ctrl;
            if (ctrl != null) {
                ctrl.setOnAudioToggle(this::enableAudio);
                // Gửi tin nhắn từ viewer tới host
                ctrl.setOnChatSend(text -> {
                    String trimmed = (text == null) ? "" : text.trim();
                    if (trimmed.isEmpty()) return;
                    if (controlSocket == null || controlSocket.isClosed()) {
                        System.err.println("[Viewer] Cannot send viewer chat: controlSocket is null or closed");
                        return;
                    }
                    System.out.println("[Viewer] Preparing to send chat to host: '" + trimmed + "'");
                    sendControl("CHAT:" + trimmed);
                    // Hiển thị tin nhắn của chính mình một lần
                    Platform.runLater(() -> ctrl.addChatMessage("You", trimmed));
                });
            }

            StackPane remoteContainer = (StackPane) scene.lookup("#remoteContainer");
            if (remoteContainer != null) {
                remoteContainer.getChildren().clear();
                remoteImageView = new ImageView();
                remoteImageView.setFitWidth(1000);
                remoteImageView.setFitHeight(700);
                remoteImageView.setPreserveRatio(true);
                remoteImageView.getStyleClass().add("remote-image-view");

                // Set minimum size to ensure ImageView is interactive
                // remoteImageView.setMinWidth(1000.0);
                // remoteImageView.setMinHeight(700.0);

                // Enable mouse events on ImageView
                remoteImageView.setMouseTransparent(false);
                remoteImageView.setPickOnBounds(true);
                remoteImageView.setFocusTraversable(true);

                // Set a placeholder image to ensure ImageView is interactive
                remoteImageView.setStyle("-fx-background-color: #1a1a1a;");

                // Create a simple placeholder image
                WritableImage placeholder = new WritableImage(1000, 700);
                remoteImageView.setImage(placeholder);

                System.out.println("ImageView created with size: " + remoteImageView.getFitWidth() + "x" + remoteImageView.getFitHeight());

                remoteContainer.getChildren().add(remoteImageView);

                // Add mouse and keyboard event handlers for remote control
                setupRemoteControlEvents(remoteImageView);

                // Request focus to ensure events are captured
                Platform.runLater(() -> {
                    remoteImageView.requestFocus();
                    System.out.println("ImageView focus requested");
                    System.out.println("ImageView is focused: " + remoteImageView.isFocused());
                });
            }

            Stage controlStage = new Stage();
            controlStage.setTitle("UltraView Remote - Viewer");
            controlStage.setScene(scene);
            controlStage.setMinWidth(1000);
            controlStage.setMinHeight(700);
            controlStage.show();
        }

        private void startNetworkConnection() {
            new Thread(() -> {
                try (Socket socket = new Socket(hostIp, hostStreamPort)) {
                    while (true) {
                        MessageModel viewerModel = SocketMethodHelpers.readMessage(socket);
                        byte[] buffer = viewerModel.getData();
                        if (buffer != null) {
                            Image img = new Image(new ByteArrayInputStream(buffer));
                            Platform.runLater(() -> {
                                if (remoteImageView != null) remoteImageView.setImage(img);
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("Disconnected: " + e.getMessage()));
                }
            }).start();
        }

        private void startControlConnection() {
            new Thread(() -> {
                try {
                    System.out.println("[Viewer] Attempting to connect to control at " + hostIp + ":" + hostControlPort);
                    controlSocket = new Socket(hostIp, hostControlPort);
                    viewerControlModel = new MessageModel(Constant.ACTION_VIEWER_CONTROLLER, hostId);
                    viewerControlModel.setPartner_password(password);
                    viewerControlModel.setPartner_id(hostId);
                    SocketMethodHelpers.sendMessage(controlSocket, viewerControlModel);

                    System.out.println("[Viewer] Control connection established successfully!");

                    // Start background listener for chat messages from host
                    Thread reader = new Thread(() -> {
                        try {
                            while (!controlSocket.isClosed()) {
                                try {
                                    MessageModel incoming = SocketMethodHelpers.readMessage(controlSocket);
                                    if (incoming == null) break;
                                    String msg = incoming.getMessage();
                                    System.out.println("[Viewer] Received control message from host: " + msg); // Debug log
                                    if (msg != null) {
                                        if (msg.startsWith("CHAT:")) {
                                            String text = msg.length() > 5 ? msg.substring(5) : "";
                                            System.out.println("[Viewer] Processing chat message from host: '" + text + "'"); // Debug log
                                            Platform.runLater(() -> {
                                                if (uiController != null) {
                                                    uiController.addChatMessage("Host", text);
                                                } else {
                                                    System.err.println("[Viewer] uiController is null when trying to display chat message");
                                                }
                                            });
                                            // Send ACK back to host so it can log that viewer received the chat
                                            try {
                                                sendControl("CHAT_ACK:" + text);
                                            } catch (Exception e) {
                                                System.err.println("[Viewer] Failed to send CHAT_ACK: " + e.getMessage());
                                                e.printStackTrace();
                                            }
                                        } else if (msg.startsWith("AUDIO:") || msg.startsWith("AUDIO_UP:")) {
                                            // Xử lý các lệnh âm thanh khác nếu cần
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("[Viewer] Error in control message reader: " + e.getMessage());
                                    e.printStackTrace();
                                    break; // Thoát vòng lặp nếu có lỗi
                                }
                            }
                        } catch (Exception e) {
                            // reader ends on error or close
                        }
                    }, "ViewerControlReader");
                    reader.setDaemon(true);
                    reader.start();
                } catch (Exception e) {
                    System.err.println("[Viewer] Failed to establish control connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "ViewerControlConnect").start();
        }

        private void sendControl(String message) {
            try {
                if (viewerControlModel == null || controlSocket == null || !controlSocket.isConnected()) {
                    System.err.println("[Viewer] sendControl aborted: model or socket invalid. model=" + viewerControlModel + ", socket=" + controlSocket);
                    return;
                }
                synchronized (controlWriteLock) {
                    viewerControlModel.setMessage(message);
                    System.out.println("[Viewer] sendControl() sending message over control socket: '" + message + "'");
                    SocketMethodHelpers.sendMessageNoTrack(controlSocket, viewerControlModel);
                }
            } catch (Exception ignore) {}
        }

        // Audio control called from UI
        private synchronized void enableAudio(boolean enable) {
            if (enable == audioEnabled) return;
            audioEnabled = enable;
            sendControl("AUDIO:" + (enable ? "ON" : "OFF"));
            sendControl("AUDIO_UP:" + (enable ? "ON" : "OFF"));
            if (enable) {
                startAudioPlayer();
                startAudioUplink();
            } else {
                stopAudioPlayer();
                stopAudioUplink();
            }
        }

        private void startAudioPlayer() {
            if (audioThread != null && audioThread.isAlive()) return;
            audioThread = new Thread(() -> {
                int audioPort = hostStreamPort + 2;
                while (audioEnabled) {
                    try (Socket s = new Socket(hostIp, audioPort); InputStream in = s.getInputStream()) {
                        audioSocket = s;

                        AudioFormat fmt = pickOutputFormat();
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                        if (!AudioSystem.isLineSupported(info)) {
                            System.err.println("Speaker line not supported for selected format");
                            break; // cannot play at all
                        }
                        speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                        speakerLine.open(fmt);
                        speakerLine.start();
                        byte[] buf = new byte[3200]; // slightly larger buffer for stability
                        while (audioEnabled && !s.isClosed()) {
                            int n = in.read(buf);
                            if (n == -1) break;
                            if (n > 0) speakerLine.write(buf, 0, n);
                        }
                    } catch (Exception e) {
                        if (audioEnabled) System.err.println("Audio player error (will retry): " + e.getMessage());
                    } finally {
                        if (speakerLine != null) {
                            try { speakerLine.drain(); speakerLine.stop(); speakerLine.close(); } catch (Exception ignore) {}
                            speakerLine = null;
                        }
                        if (audioSocket != null) {
                            try { audioSocket.close(); } catch (Exception ignore) {}
                            audioSocket = null;
                        }
                    }
                    if (audioEnabled) {
                        try { Thread.sleep(300); } catch (InterruptedException ignore) {}
                    }
                }
            }, "ViewerAudioPlayer");
            audioThread.start();
        }

        private AudioFormat pickOutputFormat() {
            // Prefer 16k mono 16-bit LE, fallback to 44.1k if not supported
            AudioFormat f16k = new AudioFormat(16000f, 16, 1, true, false);
            if (AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, f16k))) return f16k;
            AudioFormat f44 = new AudioFormat(44100f, 16, 1, true, false);
            return f44;
        }

        private synchronized void stopAudioPlayer() {
            audioEnabled = false;
            try { if (audioSocket != null) audioSocket.close(); } catch (Exception ignore) {}
            try { if (speakerLine != null) { speakerLine.stop(); speakerLine.close(); } } catch (Exception ignore) {}
            speakerLine = null;
            if (audioThread != null) {
                try { audioThread.join(200); } catch (InterruptedException ignore) {}
                audioThread = null;
            }
        }

        private void startAudioUplink() {
            if (uplinkThread != null && uplinkThread.isAlive()) return;
            uplinkThread = new Thread(() -> {
                int uplinkPort = hostStreamPort + 3;
                while (audioEnabled) {
                    try (Socket s = new Socket(hostIp, uplinkPort); OutputStream out = s.getOutputStream()) {
                        uplinkSocket = s;
                        // Use 44.1kHz for better device compatibility
                        AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
                        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                        if (!AudioSystem.isLineSupported(info)) {
                            break;
                        }
                        micLine = (TargetDataLine) AudioSystem.getLine(info);
                        micLine.open(fmt);
                        micLine.start();
                        // ~50ms @ 44.1kHz mono 16-bit ~= 44100 * 2 * 0.05 ≈ 4410 bytes
                        byte[] buf = new byte[4410];
                        while (audioEnabled && !s.isClosed()) {
                            int n = micLine.read(buf, 0, buf.length);
                            if (n > 0) out.write(buf, 0, n);
                        }
                    } catch (Exception e) {
                    } finally {
                        if (micLine != null) {
                            try { micLine.stop(); micLine.close(); } catch (Exception ignore) {}
                            micLine = null;
                        }
                        if (uplinkSocket != null) {
                            try { uplinkSocket.close(); } catch (Exception ignore) {}
                            uplinkSocket = null;
                        }
                    }
                    if (audioEnabled) {
                        try { Thread.sleep(300); } catch (InterruptedException ignore) {}
                    }
                }
            }, "ViewerAudioUplink");
            uplinkThread.start();
        }

        private void runViewerMicTest() {
            try {
                AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                if (!AudioSystem.isLineSupported(info)) return;
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(fmt);
                line.start();
                int seconds = 2;
                byte[] data = new byte[seconds * 16000 * 2];
                int off = 0;
                while (off < data.length) {
                    int n = line.read(data, off, Math.min(1600, data.length - off));
                    if (n <= 0) break;
                    off += n;
                }
                try { line.stop(); line.close(); } catch (Exception ignore) {}
                File f = getAudioSaveFile("viewer_mic_test");
                writeWavPcm16Le(f, data, 16000, 1);
                playBuffer(fmt, data, off);
                System.out.println("Saved viewer mic test: " + f.getAbsolutePath());
            } catch (Exception ignored) {}
        }

        public static void writeWavPcm16Le(File file, byte[] pcm, int sampleRate, int channels) throws IOException {
            int byteRate = sampleRate * channels * 2;
            int dataLen = pcm.length;
            int chunkSize = 36 + dataLen;
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file))) {
                dos.writeBytes("RIFF");
                dos.writeInt(Integer.reverseBytes(chunkSize));
                dos.writeBytes("WAVE");
                dos.writeBytes("fmt ");
                dos.writeInt(Integer.reverseBytes(16));
                dos.writeShort(Short.reverseBytes((short)1));
                dos.writeShort(Short.reverseBytes((short)channels));
                dos.writeInt(Integer.reverseBytes(sampleRate));
                dos.writeInt(Integer.reverseBytes(byteRate));
                dos.writeShort(Short.reverseBytes((short)(channels*2)));
                dos.writeShort(Short.reverseBytes((short)16));
                dos.writeBytes("data");
                dos.writeInt(Integer.reverseBytes(dataLen));
                dos.write(pcm, 0, dataLen);
            }
        }

        private static void playBuffer(AudioFormat fmt, byte[] pcm, int len) throws LineUnavailableException {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) return;
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(fmt);
            line.start();
            line.write(pcm, 0, len);
            try { line.drain(); } catch (Exception ignore) {}
            line.stop();
            line.close();
        }

        public static File getAudioSaveFile(String base) {
            String dirProp = System.getProperty("ultraview.audio.dir");
            File dir = (dirProp != null && !dirProp.isEmpty()) ? new File(dirProp) : new File(System.getProperty("user.home") + File.separator + "UltraView" + File.separator + "audio");
            if (!dir.exists()) dir.mkdirs();
            String name = base + "_" + System.currentTimeMillis() + ".wav";
            return new File(dir, name);
        }

        private synchronized void stopAudioUplink() {
            try { if (uplinkSocket != null) uplinkSocket.close(); } catch (Exception ignore) {}
            try { if (micLine != null) { micLine.stop(); micLine.close(); } } catch (Exception ignore) {}
            micLine = null;
            if (uplinkThread != null) {
                try { uplinkThread.join(200); } catch (InterruptedException ignore) {}
                uplinkThread = null;
            }
        }

        private void setupRemoteControlEvents(ImageView imageView) {
            System.out.println("Setting up remote control events for ImageView");
            System.out.println("ImageView bounds: " + imageView.getBoundsInLocal());

            // Mouse click events
            imageView.setOnMouseClicked(event -> {
                System.out.println("Mouse clicked at: " + event.getX() + ", " + event.getY() + " button: " + event.getButton());
                if (viewerControlModel != null) {
                    double x = event.getX();
                    double y = event.getY();
                    String button = event.getButton().toString();
                    sendControl("MOUSE_CLICK:" + x + ":" + y + ":" + button);
                    System.out.println("Sent control command: MOUSE_CLICK:" + x + ":" + y + ":" + button);
                } else {
                    System.out.println("Control writer is null!");
                }
            });

            // Mouse drag events
            imageView.setOnMouseDragged(event -> {
                System.out.println("Mouse dragged to: " + event.getX() + ", " + event.getY());
                if (viewerControlModel != null) {
                    double x = event.getX();
                    double y = event.getY();
                    sendControl("MOUSE_DRAG:" + x + ":" + y);
                }
            });

            // Mouse scroll events
            imageView.setOnScroll(event -> {
                System.out.println("Mouse scrolled at: " + event.getX() + ", " + event.getY() + " delta: " + event.getDeltaY());
                if (viewerControlModel != null) {
                    double x = event.getX();
                    double y = event.getY();
                    double deltaY = event.getDeltaY();
                    sendControl("MOUSE_SCROLL:" + x + ":" + y + ":" + deltaY);
                }
            });

            // Keyboard events
            imageView.setFocusTraversable(true);
            imageView.setOnKeyPressed(event -> {
                System.out.println("Key pressed: " + event.getCode());
                if (viewerControlModel != null) {
                    String keyCode = event.getCode().toString();
                    sendControl("KEY_PRESSED:" + keyCode);
                }
            });

            imageView.setOnKeyReleased(event -> {
                System.out.println("Key released: " + event.getCode());
                if (viewerControlModel != null) {
                    String keyCode = event.getCode().toString();
                    sendControl("KEY_RELEASED:" + keyCode);
                }
            });

            imageView.setOnKeyTyped(event -> {
                if (viewerControlModel != null) {
                    String character = event.getCharacter();
                    sendControl("KEY_TYPED:" + character);
                }
            });
        }

        public static void main(String[] args) {
            launch(args);
        }
    }
