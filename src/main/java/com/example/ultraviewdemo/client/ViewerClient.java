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
import javafx.scene.input.MouseEvent;
import javafx.geometry.Pos;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private volatile Socket streamSocket;
    private volatile boolean disconnecting = false;
    private MessageModel viewerControlModel;
    private volatile int hostScreenWidth = 1920;
    private volatile int hostScreenHeight = 1080;
    private com.example.ultraviewdemo.UltraViewController uiController;
    private final Object controlWriteLock = new Object();

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

    // Keep reference to the primary stage (connect-host window)
    private Stage primaryStage;

    private Stage controlStage;

    private void disconnectAndExit() {
        disconnecting = true;
        try {
            enableAudio(false);
        } catch (Exception ignore) {
        }

        try {
            if (streamSocket != null) streamSocket.close();
        } catch (Exception ignore) {
        } finally {
            streamSocket = null;
        }

        try {
            if (audioSocket != null) audioSocket.close();
        } catch (Exception ignore) {
        } finally {
            audioSocket = null;
        }

        try {
            if (uplinkSocket != null) uplinkSocket.close();
        } catch (Exception ignore) {
        } finally {
            uplinkSocket = null;
        }

        try {
            if (controlSocket != null) controlSocket.close();
        } catch (Exception ignore) {
        } finally {
            controlSocket = null;
        }

        Platform.runLater(Platform::exit);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
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
        Scene connectScene = new Scene(connectLoader.load(), 900, 650);

        String cssPath = getClass().getResource("/com/example/ultraviewdemo/demoView/ultraview.css").toExternalForm();
        connectScene.getStylesheets().add(cssPath);
        stage.setTitle("UltraView Remote - Connect");
        stage.setScene(connectScene);
        stage.setMinWidth(700);
        stage.setMinHeight(500);
        stage.centerOnScreen();

        stage.setOnCloseRequest(event -> {
            if (disconnecting) return;
            boolean connected = (controlSocket != null && !controlSocket.isClosed())
                    || (streamSocket != null && !streamSocket.isClosed());
            if (connected) {
                event.consume();
                disconnectAndExit();
            }
        });

        stage.show();

        ConnectHostController controller = connectLoader.getController();
        controller.setOnConnect(params -> {
            this.disconnecting = false;
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
                            // Hide the connect-host window once connection is successful
                            stage.hide();
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
        System.out.println("[Host] About to call startHostControlAccept...");
        startHostControlAccept(controlServer, hostId, password, shouldRun::get, audioManager, uplinkManager);

        // Start screen sharing
        System.out.println("[Host] Waiting for stream connection...");
        try (Socket streamSocket = streamServer.accept()) {
            System.out.println("[Host] Stream connection accepted from: " + streamSocket.getRemoteSocketAddress());
            System.out.println("[Host] Both control and stream connections established!");
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
                try {
                    out.close();
                } catch (Exception ignore) {
                }
            }
        } finally {
            // Close all servers
            try {
                streamServer.close();
            } catch (Exception ignore) {
            }
            try {
                controlServer.close();
            } catch (Exception ignore) {
            }
            try {
                audioServer.close();
            } catch (Exception ignore) {
            }
            try {
                uplinkServer.close();
            } catch (Exception ignore) {
            }
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
        System.out.println("[Host] startHostControlAccept() called - waiting for viewer connection...");
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
                        if (enable) audioManager.enable();
                        else audioManager.disable();
                    }
                    break;
                case "AUDIO_UP":
                    if (parts.length >= 2) {
                        String state = parts[1];
                        boolean enable = "ON".equalsIgnoreCase(state);
                        if (enable) uplinkManager.enable();
                        else uplinkManager.disable();
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
            } catch (Exception ignored) {
            }
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

        void enable() {
            enabled = true;
        }

        void disable() {
            enabled = false;
        }

        private void startSender() {
            new Thread(() -> {
                try {
                    AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

                    if (!AudioSystem.isLineSupported(info)) {
                        System.err.println("[Host] Microphone not supported for audio format");
                        return;
                    }

                    TargetDataLine micLine = (TargetDataLine) AudioSystem.getLine(info);
                    micLine.open(fmt);
                    micLine.start();

                    System.out.println("[Host] Audio sender started - capturing microphone");

                    OutputStream out = client.getOutputStream();
                    byte[] buf = new byte[4096];

                    while (enabled && !client.isClosed() && shouldRun.getAsBoolean()) {
                        int n = micLine.read(buf, 0, buf.length);
                        if (n > 0) {
                            out.write(buf, 0, n);
                            out.flush();
                        }
                    }

                    micLine.stop();
                    micLine.close();
                    System.out.println("[Host] Audio sender stopped");

                } catch (Exception e) {
                    System.err.println("[Host] Error in audio sender: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "HostAudioSender").start();
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

        void enable() {
            enabled = true;
        }

        void disable() {
            enabled = false;
        }

        private void startReceiver() {
            new Thread(() -> {
                try {
                    AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);

                    if (!AudioSystem.isLineSupported(info)) {
                        System.err.println("[Host] Speakers not supported for audio format");
                        return;
                    }

                    SourceDataLine speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                    speakerLine.open(fmt);
                    speakerLine.start();

                    System.out.println("[Host] Uplink receiver started - playing viewer audio");

                    InputStream in = client.getInputStream();
                    byte[] buf = new byte[4096];

                    while (enabled && !client.isClosed() && shouldRun.getAsBoolean()) {
                        int n = in.read(buf);
                        if (n > 0) {
                            speakerLine.write(buf, 0, n);
                        }
                    }

                    speakerLine.drain();
                    speakerLine.stop();
                    speakerLine.close();
                    System.out.println("[Host] Uplink receiver stopped");

                } catch (Exception e) {
                    System.err.println("[Host] Error in uplink receiver: " + e.getMessage());
                    e.printStackTrace();
                }
            }, "HostUplinkReceiver").start();
        }
    }

    private void sendFileFromViewer(File file) {
        if (file == null) return;
        if (controlSocket == null || controlSocket.isClosed()) {
            System.err.println("[Viewer] Cannot send file: controlSocket is null or closed");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String header = "FILE:" + file.getName() + ":" + bytes.length;

            synchronized (controlWriteLock) {
                if (viewerControlModel == null) {
                    viewerControlModel = new MessageModel(Constant.ACTION_VIEWER_CONTROLLER, hostId);
                    viewerControlModel.setPartner_id(hostId);
                    viewerControlModel.setPartner_password(password);
                }
                viewerControlModel.setMessage(header);
                viewerControlModel.setData(bytes);
                SocketMethodHelpers.sendMessageNoTrack(controlSocket, viewerControlModel);
            }

            if (uiController != null) {
                Platform.runLater(() -> uiController.addChatMessage("You", "Sent file: " + file.getName()));
            }
        } catch (IOException e) {
            e.printStackTrace();
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

    private void disconnectToConnectScreen() {
        disconnecting = true;
        try {
            enableAudio(false);
        } catch (Exception ignore) {
        }

        try {
            if (streamSocket != null) streamSocket.close();
        } catch (Exception ignore) {
        } finally {
            streamSocket = null;
        }

        try {
            if (audioSocket != null) audioSocket.close();
        } catch (Exception ignore) {
        } finally {
            audioSocket = null;
        }

        try {
            if (uplinkSocket != null) uplinkSocket.close();
        } catch (Exception ignore) {
        } finally {
            uplinkSocket = null;
        }

        try {
            if (controlSocket != null) controlSocket.close();
        } catch (Exception ignore) {
        } finally {
            controlSocket = null;
        }

        Platform.runLater(() -> {
            try {
                if (controlStage != null) {
                    try {
                        controlStage.close();
                    } catch (Exception ignore) {
                    }
                    controlStage = null;
                }
                if (primaryStage != null) {
                    primaryStage.show();
                    primaryStage.toFront();
                }
            } catch (Exception ignore) {
            }
        });
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
            ctrl.setOnDisconnect(this::disconnectToConnectScreen);
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
            ctrl.setOnFileSend(this::sendFileFromViewer);
        }

        // Tạo cửa sổ control chính cho Viewer
        controlStage = new Stage();
        controlStage.setTitle("UltraView Remote - Viewer");
        controlStage.setScene(scene);
        controlStage.setMinWidth(1000);
        controlStage.setMinHeight(700);
        controlStage.setMaximized(true);

        controlStage.setOnCloseRequest(event -> {
            if (disconnecting) return;
            event.consume();
            disconnectToConnectScreen();
        });

        // Cung cấp Stage cho UltraViewController để dùng cho FileChooser, fullscreen, v.v.
        if (ctrl != null) {
            ctrl.setStage(controlStage);
        }

        StackPane remoteContainer = (StackPane) scene.lookup("#remoteContainer");
        if (remoteContainer != null) {
            // ================================================================
            // [FIX] QUAN TRỌNG: Cho phép container thu nhỏ về 0
            // Giúp StackPane không bị kẹt ở kích thước lớn sau khi thoát fullscreen
            remoteContainer.setMinSize(0, 0);
            // ================================================================

            remoteContainer.getChildren().clear();
            remoteImageView = new ImageView();
            remoteImageView.setFitWidth(1000);
            remoteImageView.setFitHeight(700);
            remoteImageView.setPreserveRatio(true);
            remoteImageView.getStyleClass().add("remote-image-view");

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

            // Bind to container to always fit available space and center the image
            remoteImageView.fitWidthProperty().bind(remoteContainer.widthProperty());
            remoteImageView.fitHeightProperty().bind(remoteContainer.heightProperty());
            StackPane.setAlignment(remoteImageView, Pos.CENTER);

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

        controlStage.show();
    }

    private void startNetworkConnection() {
        new Thread(() -> {
            try (Socket socket = new Socket(hostIp, hostStreamPort)) {
                streamSocket = socket;
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
                boolean expected = disconnecting;
                if (!expected && e instanceof java.net.SocketException) {
                    String m = e.getMessage();
                    if (m != null && (m.toLowerCase().contains("aborted") || m.toLowerCase().contains("closed") || m.toLowerCase().contains("reset"))) {
                        expected = true;
                    }
                }
                if (!expected) {
                    e.printStackTrace();
                    Platform.runLater(() -> showError("Disconnected: " + e.getMessage()));
                }
            } finally {
                streamSocket = null;
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
                                byte[] data = incoming.getData();

                                System.out.println("[Viewer] Received control message from host: " + msg);
                                if (msg != null) {
                                    if ("HOST_STOP".equalsIgnoreCase(msg.trim())) {
                                        System.out.println("[Viewer] Host stopped sharing -> disconnecting viewer session");
                                        Platform.runLater(this::disconnectToConnectScreen);
                                        break;
                                    }

                                    if (msg.startsWith("HOST_SCREEN:")) {
                                        String[] p = msg.split(":");
                                        if (p.length >= 3) {
                                            try {
                                                int w = Integer.parseInt(p[1]);
                                                int h = Integer.parseInt(p[2]);
                                                hostScreenWidth = Math.max(1, w);
                                                hostScreenHeight = Math.max(1, h);
                                                System.out.println("Host screen size received: " + hostScreenWidth + "x" + hostScreenHeight);
                                            } catch (NumberFormatException ignore) {
                                            }
                                        }
                                    }

                                    if (msg.startsWith("CHAT:")) {
                                        String text = msg.length() > 5 ? msg.substring(5) : "";
                                        System.out.println("[Viewer] Processing chat message from host: '" + text + "'");
                                        Platform.runLater(() -> {
                                            if (uiController != null) {
                                                uiController.addChatMessage("Host", text);
                                            } else {
                                                System.err.println("[Viewer] uiController is null when trying to display chat message");
                                            }
                                        });
                                        try {
                                            sendControl("CHAT_ACK:" + text);
                                        } catch (Exception e) {
                                            System.err.println("[Viewer] Failed to send CHAT_ACK: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    } else if (msg.startsWith("FILE:")) {
                                        String[] parts = msg.split(":", 3);
                                        if (parts.length >= 2 && data != null) {
                                            String fileName = parts[1];
                                            try {
                                                Path saveDir = Paths.get(System.getProperty("user.home"), "Downloads", "UltraViewFiles");
                                                Files.createDirectories(saveDir);
                                                Path out = saveDir.resolve(fileName);
                                                Files.write(out, data);

                                                Platform.runLater(() -> {
                                                    if (uiController != null) {
                                                        uiController.addChatMessage("Host", "Sent file: " + fileName + " -> " + out.toString());
                                                    }
                                                });
                                            } catch (IOException ioe) {
                                                ioe.printStackTrace();
                                            }
                                        }
                                    } else if (msg.startsWith("AUDIO:") || msg.startsWith("AUDIO_UP:")) {
                                        // ignore
                                    }
                                }
                            } catch (Exception e) {
                                boolean expected = disconnecting;
                                if (!expected && e instanceof java.net.SocketException) {
                                    String m = e.getMessage();
                                    if (m != null && (m.toLowerCase().contains("aborted") || m.toLowerCase().contains("closed") || m.toLowerCase().contains("reset"))) {
                                        expected = true;
                                    }
                                }
                                if (!expected) {
                                    System.err.println("[Viewer] Error in control message reader: " + e.getMessage());
                                    e.printStackTrace();
                                }
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
                boolean expected = disconnecting;
                if (!expected && e instanceof java.net.SocketException) {
                    String m = e.getMessage();
                    if (m != null && (m.toLowerCase().contains("aborted") || m.toLowerCase().contains("closed") || m.toLowerCase().contains("reset"))) {
                        expected = true;
                    }
                }
                if (!expected) {
                    System.err.println("[Viewer] Failed to establish control connection: " + e.getMessage());
                    e.printStackTrace();
                }
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
        } catch (Exception ignore) {
        }
    }


    private synchronized void enableAudio(boolean enable) {
        if (enable == audioEnabled) return;

        System.out.println("[Viewer] enableAudio called: " + enable);

        // Tắt audio trước nếu đang bật
        if (enable) {
            stopAudioPlayer();
            stopAudioUplink();
            try {
                Thread.sleep(200); // Đợi dọn dẹp hoàn tất
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        audioEnabled = enable;

        // Gửi lệnh cho Host biết để bật/tắt Mic/Loa của họ
        sendControl("AUDIO:" + (enable ? "ON" : "OFF"));
        sendControl("AUDIO_UP:" + (enable ? "ON" : "OFF"));

        if (enable) {
            startAudioPlayer();  // Viewer nghe Host
            startAudioUplink();  // Viewer nói cho Host
        } else {
            stopAudioPlayer();
            stopAudioUplink();
        }
    }

    private void startAudioPlayer() {
        // Dừng luồng cũ nếu còn chạy
        if (audioThread != null && audioThread.isAlive()) {
            System.out.println("[Viewer] Stopping existing audio player thread");
            stopAudioPlayer();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[Viewer] Starting new audio player");

        audioThread = new Thread(() -> {
            int audioPort = hostStreamPort + 2;
            System.out.println("[Viewer] Connecting to Host Audio Server at port: " + audioPort);

            Socket localSocket = null;
            SourceDataLine localSpeakerLine = null;

            try {
                localSocket = new Socket(hostIp, audioPort);
                this.audioSocket = localSocket;

                InputStream in = localSocket.getInputStream();

                AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);

                if (!AudioSystem.isLineSupported(info)) {
                    System.err.println("[Viewer] Speaker audio format not supported");
                    return;
                }

                localSpeakerLine = (SourceDataLine) AudioSystem.getLine(info);
                this.speakerLine = localSpeakerLine;

                localSpeakerLine.open(fmt);
                localSpeakerLine.start();

                System.out.println("[Viewer] Audio player started successfully");

                byte[] buf = new byte[4096];
                int n;

                while (audioEnabled && !localSocket.isClosed() && (n = in.read(buf)) != -1) {
                    if (n > 0 && audioEnabled) {
                        localSpeakerLine.write(buf, 0, n);
                    }
                }

                System.out.println("[Viewer] Audio player loop ended normally");

            } catch (Exception e) {
                if (audioEnabled) {
                    System.err.println("[Viewer] Audio player error: " + e.getMessage());
                }
            } finally {
                // Cleanup
                if (localSpeakerLine != null) {
                    try {
                        localSpeakerLine.drain();
                        localSpeakerLine.stop();
                        localSpeakerLine.close();
                    } catch (Exception e) {
                        System.err.println("[Viewer] Error closing speaker line: " + e.getMessage());
                    }
                }

                if (localSocket != null) {
                    try {
                        localSocket.close();
                    } catch (Exception e) {
                        System.err.println("[Viewer] Error closing audio socket: " + e.getMessage());
                    }
                }

                this.speakerLine = null;
                this.audioSocket = null;

                System.out.println("[Viewer] Audio player cleaned up");
            }
        }, "ViewerAudioPlayer");

        audioThread.setDaemon(true);
        audioThread.start();
    }

    private synchronized void stopAudioPlayer() {
        System.out.println("[Viewer] Stopping audio player");
        audioEnabled = false;

        // Close socket first to break the read loop
        if (audioSocket != null) {
            try {
                audioSocket.close();
            } catch (Exception e) {
                System.err.println("[Viewer] Error closing audio socket: " + e.getMessage());
            }
            audioSocket = null;
        }

        // Stop and close speaker line
        if (speakerLine != null) {
            try {
                speakerLine.stop();
                speakerLine.close();
            } catch (Exception e) {
                System.err.println("[Viewer] Error closing speaker line: " + e.getMessage());
            }
            speakerLine = null;
        }

        // Wait for thread to finish
        if (audioThread != null) {
            try {
                audioThread.join(500);
                if (audioThread.isAlive()) {
                    System.err.println("[Viewer] Audio thread did not stop in time");
                    audioThread.interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            audioThread = null;
        }

        System.out.println("[Viewer] Audio player stopped");
    }

    private void startAudioUplink() {
        if (uplinkThread != null && uplinkThread.isAlive()) {
            stopAudioUplink();
            try { Thread.sleep(200); } catch (InterruptedException e) {}
        }

        uplinkThread = new Thread(() -> {
            int uplinkPort = hostStreamPort + 3;
            Socket localSocket = null;
            TargetDataLine localMicLine = null;

            try {
                localSocket = new Socket(hostIp, uplinkPort);
                this.uplinkSocket = localSocket;
                OutputStream out = localSocket.getOutputStream();

                // Thống nhất định dạng 16kHz, 16-bit, Mono
                AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

                if (!AudioSystem.isLineSupported(info)) {
                    System.err.println("[Viewer] Microphone không hỗ trợ định dạng này");
                    return;
                }

                localMicLine = (TargetDataLine) AudioSystem.getLine(info);
                // Tăng Buffer phần cứng lên 100ms để tránh mất dữ liệu khi mạng lag
                localMicLine.open(fmt, (int)(fmt.getSampleRate() * fmt.getFrameSize() * 0.1));
                this.micLine = localMicLine;

                localMicLine.start();
                System.out.println("[Viewer] Audio Uplink started (16kHz)");

                byte[] buf = new byte[1024]; // Gửi từng khối 1KB để tối ưu packet mạng
                int n;

                while (audioEnabled && !localSocket.isClosed()) {
                    n = localMicLine.read(buf, 0, buf.length);
                    if (n > 0 && audioEnabled) {
                        out.write(buf, 0, n);
                        // Để OS tự quản lý việc flush để tránh quá tải CPU
                    }
                }
            } catch (Exception e) {
                System.err.println("[Viewer] Audio uplink error: " + e.getMessage());
            } finally {
                if (localMicLine != null) {
                    localMicLine.stop();
                    localMicLine.close();
                }
                try { if (localSocket != null) localSocket.close(); } catch (IOException e) {}
                this.micLine = null;
                this.uplinkSocket = null;
            }
        }, "ViewerAudioUplink");

        uplinkThread.setDaemon(true);
        uplinkThread.start();
    }

    private synchronized void stopAudioUplink() {
        System.out.println("[Viewer] Stopping audio uplink");

        // Stop mic line first để ngừng capture
        if (micLine != null) {
            try {
                micLine.stop();
                micLine.flush(); // QUAN TRỌNG: Xóa buffer để tránh rè
                Thread.sleep(50); // Đợi buffer clear
                micLine.close();
                System.out.println("[Viewer] Mic line stopped and closed");
            } catch (Exception e) {
                System.err.println("[Viewer] Error closing mic line: " + e.getMessage());
            }
            micLine = null;
        }

        // Close socket sau
        if (uplinkSocket != null) {
            try {
                uplinkSocket.close();
                System.out.println("[Viewer] Uplink socket closed");
            } catch (Exception e) {
                System.err.println("[Viewer] Error closing uplink socket: " + e.getMessage());
            }
            uplinkSocket = null;
        }

        // Wait for thread to finish
        if (uplinkThread != null) {
            try {
                uplinkThread.join(1000); // Tăng timeout lên 1 giây
                if (uplinkThread.isAlive()) {
                    System.err.println("[Viewer] Uplink thread did not stop in time, interrupting");
                    uplinkThread.interrupt();
                    uplinkThread.join(500); // Đợi thêm sau interrupt
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            uplinkThread = null;
        }

        System.out.println("[Viewer] Audio uplink stopped completely");
    }

    private void setupRemoteControlEvents(ImageView imageView) {
        System.out.println("Setting up remote control events for ImageView");
        System.out.println("ImageView bounds: " + imageView.getBoundsInLocal());

        // Mouse click events
        imageView.setOnMouseClicked(event -> {
            System.out.println("Mouse clicked at: " + event.getX() + ", " + event.getY() + " button: " + event.getButton());
            if (viewerControlModel != null) {
                double[] mapped = mapToVirtual(imageView, event.getX(), event.getY());
                double x = mapped[0];
                double y = mapped[1];
                String button = event.getButton().toString();
                viewerControlModel.setMessage("MOUSE_CLICK:" + x + ":" + y + ":" + button);
                SocketMethodHelpers.sendMessageNoTrack(controlSocket, viewerControlModel);
                System.out.println("Sent control command: MOUSE_CLICK:" + x + ":" + y + ":" + button);
            } else {
                System.out.println("Control writer is null!");
            }
            event.consume();
        });

        // Ensure focus on press and consume to avoid parent scroll/pan
        imageView.setOnMousePressed(event -> {
            imageView.requestFocus();
            event.consume();
        });
        imageView.setOnMouseReleased(event -> {
            event.consume();
        });

        // Mouse drag events
        imageView.setOnMouseDragged(event -> {
            System.out.println("Mouse dragged to: " + event.getX() + ", " + event.getY());
            if (viewerControlModel != null) {
                double[] mapped = mapToVirtual(imageView, event.getX(), event.getY());
                double x = mapped[0];
                double y = mapped[1];
                viewerControlModel.setMessage("MOUSE_DRAG:" + x + ":" + y);
                SocketMethodHelpers.sendMessageNoTrack(controlSocket, viewerControlModel);
            }
            event.consume();
        });

        // Mouse scroll events
        imageView.setOnScroll(event -> {
            System.out.println("Mouse scrolled at: " + event.getX() + ", " + event.getY() + " delta: " + event.getDeltaY());
            if (viewerControlModel != null) {
                double[] mapped = mapToVirtual(imageView, event.getX(), event.getY());
                double x = mapped[0];
                double y = mapped[1];
                double deltaY = event.getDeltaY();
                viewerControlModel.setMessage("MOUSE_SCROLL:" + x + ":" + y + ":" + deltaY);
                SocketMethodHelpers.sendMessageNoTrack(controlSocket, viewerControlModel);
            }
            event.consume();
        });
        imageView.setOnScrollStarted(event -> event.consume());
        imageView.setOnScrollFinished(event -> event.consume());

        // Keyboard events
        imageView.setFocusTraversable(true);
        imageView.setOnKeyPressed(event -> {
            System.out.println("Key pressed: " + event.getCode());
            if (viewerControlModel != null) {
                String keyCode = event.getCode().toString();
                viewerControlModel.setMessage("KEY_PRESSED:" + keyCode);
                SocketMethodHelpers.sendMessageNoTrack(controlSocket, viewerControlModel);
            }
        });

        imageView.setOnKeyReleased(event -> {
            System.out.println("Key released: " + event.getCode());
            if (viewerControlModel != null) {
                String keyCode = event.getCode().toString();
                viewerControlModel.setMessage("KEY_RELEASED:" + keyCode);
                SocketMethodHelpers.sendMessageNoTrack(controlSocket, viewerControlModel);
            }
        });

        imageView.setOnKeyTyped(event -> {
            if (viewerControlModel != null) {
                String character = event.getCharacter();
                viewerControlModel.setMessage("KEY_TYPED:" + character);
                SocketMethodHelpers.sendMessageNoTrack(controlSocket, viewerControlModel);
            }
        });
    }

    private double[] mapToVirtual(ImageView iv, double eventX, double eventY) {
        try {
            Image img = iv.getImage();
            if (img == null) return new double[]{eventX, eventY};

            double imgW = img.getWidth();
            double imgH = img.getHeight();
            if (imgW <= 0 || imgH <= 0) return new double[]{eventX, eventY};

            double localX = eventX;
            double localY = eventY;
            double boundW = iv.getBoundsInLocal().getWidth();
            double boundH = iv.getBoundsInLocal().getHeight();

            if (eventX < 0 || eventY < 0 || eventX > boundW || eventY > boundH) {
                try {
                    javafx.geometry.Point2D p = iv.sceneToLocal(eventX, eventY);
                    localX = p.getX();
                    localY = p.getY();
                } catch (Exception ex) {
                }
            }

            double displayW = boundW;
            double displayH = boundH;

            if (displayW <= 0 || displayH <= 0) {
                displayW = iv.getFitWidth() > 0 ? iv.getFitWidth() : imgW;
                displayH = iv.getFitHeight() > 0 ? iv.getFitHeight() : imgH;
            }

            javafx.geometry.Rectangle2D viewport = iv.getViewport();
            double vpX = 0, vpY = 0, vpW = imgW, vpH = imgH;
            boolean hasViewport = viewport != null;
            if (hasViewport) {
                vpX = viewport.getMinX();
                vpY = viewport.getMinY();
                vpW = viewport.getWidth();
                vpH = viewport.getHeight();
                if (vpW <= 0 || vpH <= 0) {
                    hasViewport = false;
                    vpX = vpY = 0;
                    vpW = imgW;
                    vpH = imgH;
                }
            }

            double renderW = displayW;
            double renderH = displayH;
            double offsetX = 0;
            double offsetY = 0;

            if (iv.isPreserveRatio()) {
                double scale = Math.min(displayW / vpW, displayH / vpH);
                renderW = vpW * scale;
                renderH = vpH * scale;
                offsetX = (displayW - renderW) / 2.0;
                offsetY = (displayH - renderH) / 2.0;
            } else {
                renderW = displayW;
                renderH = displayH;
                offsetX = 0;
                offsetY = 0;
            }

            double nx = (localX - offsetX) / renderW;
            double ny = (localY - offsetY) / renderH;

            if (Double.isNaN(nx) || Double.isInfinite(nx)) nx = -1;
            if (Double.isNaN(ny) || Double.isInfinite(ny)) ny = -1;

            nx = Math.max(0, Math.min(1, nx));
            ny = Math.max(0, Math.min(1, ny));

            double vx = vpX + nx * vpW;
            double vy = vpY + ny * vpH;

            return new double[]{nx * hostScreenWidth, ny * hostScreenHeight};
        } catch (Exception e) {
            return new double[]{eventX, eventY};
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}