package com.example.ultraviewdemo.client;

import com.example.ultraviewdemo.helpers.Constant;
import com.example.ultraviewdemo.helpers.SocketMethodHelpers;
import com.example.ultraviewdemo.models.MessageModel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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

import static com.example.ultraviewdemo.client.ViewerClient.getAudioSaveFile;
import static com.example.ultraviewdemo.client.ViewerClient.writeWavPcm16Le;

public class HostClient extends Application {
    // Chat/control references for Host UI integration
    private static volatile Socket controlSocketRef;
    private static final Object controlWriteLock = new Object();
    private static volatile HostController hostControllerRef;
    private static volatile String hostIdRef;
    private static volatile Socket currentControlSocket;
    private static volatile java.util.function.Consumer<String> chatSink;

    public static void setChatSink(java.util.function.Consumer<String> sink) { chatSink = sink; }

    public static void shareLoop(String server, int port, String hostId, String password, BooleanSupplier shouldRun) throws Exception {
        hostIdRef = hostId;
        // Start local stream/control servers on provided ports
        ServerSocket streamServer = createServerSocket(port);
        ServerSocket controlServer = createServerSocket(port + 1);
        ServerSocket audioServer = createServerSocket(port + 2);
        ServerSocket uplinkServer = createServerSocket(port + 3);

        // Register with directory server for signaling so viewer can discover us
        String localIp = InetAddress.getLocalHost().getHostAddress();
        try (Socket dirSocket = new Socket(server, 7000)) { // DirectoryServer listens on 7000
            MessageModel reg = new MessageModel(Constant.ACTION_HOST_REGISTER, hostId);
            reg.setOwner_password(password);
            reg.setMessage(localIp + ":" + port + ":" + (port + 1));
            SocketMethodHelpers.sendMessage(dirSocket, reg);
            MessageModel resp = SocketMethodHelpers.readMessage(dirSocket);
            if (!resp.isSuccess()) {
                throw new IOException("Directory register failed: " + resp.getMessage());
            }
        }

        // Set up audio accept and sender (controlled by AUDIO:ON/OFF)
        AudioManager audioManager = new AudioManager(audioServer, shouldRun);
        audioManager.startAcceptLoop();

        // Set up uplink (viewer -> host) playback manager
        UplinkManager uplinkManager = new UplinkManager(uplinkServer, shouldRun);
        uplinkManager.startAcceptLoop();

        // Accept one control connection to handle input
        startControlAccept(controlServer, hostId, password, shouldRun, audioManager, uplinkManager);

        // Accept one stream connection and start sending frames
        try (Socket streamSocket = streamServer.accept()) {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            DataOutputStream out = new DataOutputStream(streamSocket.getOutputStream());
            try {
                while (shouldRun.getAsBoolean()) {
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
            try { streamServer.close(); } catch (Exception ignore) {}
            try { controlServer.close(); } catch (Exception ignore) {}
            try { audioServer.close(); } catch (Exception ignore) {}
            try { uplinkServer.close(); } catch (Exception ignore) {}
        }
    }

    private static ServerSocket createServerSocket(int port) throws IOException {
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));
            return ss;
        } catch (BindException be) {
            throw new IOException("Port " + port + " is already in use. Please close other instances or choose a different base port.", be);
        }
    }

    private static void startControlAccept(ServerSocket controlServer, String hostId, String password, BooleanSupplier shouldRun, AudioManager audioManager, UplinkManager uplinkManager) {
        new Thread(() -> {
            try (Socket controlSocket = controlServer.accept()) {
                System.out.println("[Host] Control connection accepted from viewer: " + controlSocket.getRemoteSocketAddress());
                controlSocketRef = controlSocket;
                currentControlSocket = controlSocket;


                Platform.runLater(() -> {
                    try {
                        System.out.println("[Host] Connection established - Auto opening Chat Window");
                        HostChatWindow.initIfNeeded();
                        // Đảm bảo nút gửi hoạt động
                        HostChatWindow.setOnSend(HostClient::sendChatFromUI);
                        HostChatWindow.show();
                        // Thêm thông báo hệ thống
                        HostChatWindow.addMessage("System", "Viewer connected successfully!");
                    } catch (Exception e) {
                        System.err.println("[Host] Error auto-opening chat: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                // =======================================================================

                Robot robot = new Robot();
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                try {
                    MessageModel info = new MessageModel(Constant.ACTION_HOST, hostId);
                    info.setMessage("HOST_SCREEN:" + screenRect.width + ":" + screenRect.height);
                    SocketMethodHelpers.sendMessage(controlSocket, info);
                } catch (Exception ignore) {}

                // ... (Phần code đọc tin nhắn cũ của bạn giữ nguyên) ...
                try {
                    while (shouldRun.getAsBoolean() && !controlSocket.isClosed()) {
                        // ... logic đọc tin nhắn ...
                        try {
                            MessageModel incoming = SocketMethodHelpers.readMessage(controlSocket);
                            if (incoming == null) break;
                            String msg = incoming.getMessage();
                            if (msg != null) {
                                handleControlCommand(robot, msg, screenRect, audioManager, uplinkManager, controlSocket, hostId);
                            }
                        } catch (Exception e) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } catch (Exception e) {
                System.err.println("[Host] Control connection error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("[Host] Control connection closed");
                currentControlSocket = null;
                controlSocketRef = null;

                // [TÙY CHỌN] Nếu muốn đóng chat khi mất kết nối thì thêm dòng này:
                // Platform.runLater(() -> HostChatWindow.hide());
            }
        }, "HostControlAccept").start();
    }

    private static void handleControlCommand(Robot robot, String command, Rectangle screenRect, AudioManager audioManager, UplinkManager uplinkManager, Socket controlSocket, String hostId) {
        try {
            if (command == null) return;
            String[] parts = command.split(":");
            if (parts.length < 2) return;

            String action = parts[0];

            switch (action) {
                case "MOUSE_CLICK":
                    if (parts.length >= 4) {
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        String button = parts[3];

                        // Treat incoming coordinates as absolute host pixels
                        int screenX = (int) x;
                        int screenY = (int) y;

                        int buttonMask = "PRIMARY".equals(button) ? InputEvent.BUTTON1_DOWN_MASK :
                                "SECONDARY".equals(button) ? InputEvent.BUTTON3_DOWN_MASK :
                                        InputEvent.BUTTON2_DOWN_MASK;

                        robot.mouseMove(screenX, screenY);
                        robot.mousePress(buttonMask);
                        robot.mouseRelease(buttonMask);
                    }
                    break;

                case "MOUSE_DRAG":
                    if (parts.length >= 3) {
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);

                        int screenX = (int) x;
                        int screenY = (int) y;

                        robot.mouseMove(screenX, screenY);
                    }
                    break;

                case "MOUSE_SCROLL":
                    if (parts.length >= 4) {
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        double deltaY = Double.parseDouble(parts[3]);

                        int screenX = (int) x;
                        int screenY = (int) y;

                        robot.mouseMove(screenX, screenY);
                        // Use a smaller divisor so each scroll gesture has more impact
                        int wheelAmount = (int) Math.round(deltaY / 20.0);
                        if (wheelAmount == 0 && deltaY != 0) {
                            wheelAmount = deltaY > 0 ? 1 : -1;
                        }
                        // Invert direction so viewer scroll up corresponds to host scroll up
                        wheelAmount = -wheelAmount;
                        System.out.println("MOUSE_SCROLL at (" + screenX + ", " + screenY + ") deltaY=" + deltaY + " wheel=" + wheelAmount);
                        robot.mouseWheel(wheelAmount); // Scale scroll amount
                    }
                    break;

                case "KEY_PRESSED":
                    if (parts.length >= 2) {
                        String keyCode = parts[1];
                        int key = getKeyCode(keyCode);
                        if (key != -1) {
                            robot.keyPress(key);
                        }
                    }
                    break;

                case "KEY_RELEASED":
                    if (parts.length >= 2) {
                        String keyCode = parts[1];
                        int key = getKeyCode(keyCode);
                        if (key != -1) {
                            robot.keyRelease(key);
                        }
                    }
                    break;

                case "KEY_TYPED":
                    if (parts.length >= 2) {
                        String character = parts[1];
                        if (character.length() == 1) {
                            char c = character.charAt(0);
                            int extendedKey = KeyEvent.getExtendedKeyCodeForChar(c);

                            // [FIX] Chỉ nhấn nếu mã phím hợp lệ
                            if (extendedKey != KeyEvent.VK_UNDEFINED) {
                                try {
                                    robot.keyPress(extendedKey);
                                    robot.keyRelease(extendedKey);
                                } catch (IllegalArgumentException ex) {
                                    System.err.println("Robot ignored invalid key code for char: " + c);
                                }
                            }
                        }
                    }
                    break;
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
                    // Receive viewer chat and show it on Host chat window only.
                    // Do NOT echo back automatically to avoid duplicates on Viewer side.
                    String text = command.length() > 5 ? command.substring(5) : "";
                    System.out.println("[Host] Received CHAT from viewer: '" + text + "'");
                    Platform.runLater(() -> {
                        try {
                            HostChatWindow.initIfNeeded();
                            // Ensure onSend callback is always wired when chat window is used
                            HostChatWindow.setOnSend(HostClient::sendChatFromUI);
                            HostChatWindow.show();
                            HostChatWindow.addMessage("Viewer", text);
                        } catch (Exception e) {
                            System.err.println("Error showing chat message: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                    break;
                case "CHAT_ACK":
                    // Acknowledgement from viewer that it received and processed a chat from host.
                    String ackText = command.length() > 9 ? command.substring(9) : "";
                    System.out.println("[Host] Received CHAT_ACK from viewer for: '" + ackText + "'");
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error handling control command: " + e.getMessage());
        }
    }

    // Host UI binds controller for chat updates
    public static void bindController(HostController ctrl) { hostControllerRef = ctrl; }

    private static void showChatError(String message) {
        System.err.println(message);
        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Chat");
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            } catch (Exception ignored) {}
        });
    }

    // Send chat from Host UI to Viewer via control socket
    public static void sendChatFromUI(String text) {
        try {
            if (hostIdRef == null || hostIdRef.isEmpty()) {
                System.err.println("[Host] sendChatFromUI aborted: hostIdRef is null/empty");
                showChatError("Không gửi được chat: hostId bị rỗng hoặc null.");
                return;
            }

            if (controlSocketRef == null || controlSocketRef.isClosed()) {
                System.err.println("[Host] sendChatFromUI aborted: controlSocketRef is null or closed");
                showChatError("Không gửi được chat: chưa có Viewer kết nối hoặc kết nối điều khiển đã mất.");
                return;
            }

            String trimmed = (text == null) ? "" : text.trim();
            if (trimmed.isEmpty()) return;

            System.out.println("[Host] Preparing to send chat message from host to viewer: '" + trimmed + "'");

            synchronized (controlWriteLock) {
                MessageModel reply = new MessageModel(Constant.ACTION_HOST, hostIdRef);
                reply.setMessage("CHAT:" + trimmed);
                SocketMethodHelpers.sendMessageNoTrack(controlSocketRef, reply);
                System.out.println("[Host] Chat message sent to viewer over control socket: '" + trimmed + "'");
            }

            // Cập nhật UI cửa sổ chat riêng (nếu đang dùng)
            Platform.runLater(() -> {
                HostChatWindow.initIfNeeded();
                HostChatWindow.show();
                HostChatWindow.addMessage("Host", trimmed);
            });
        } catch (Exception e) {
            showChatError("Lỗi gửi chat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Expose connection state so HostController can show a friendly message instead of failing silently
    public static boolean isControlConnected() {
        boolean connected = controlSocketRef != null && !controlSocketRef.isClosed();
        System.out.println("[Host] isControlConnected() -> " + connected + ", socketRef=" + controlSocketRef);
        return connected;
    }

    // Manages accepting an audio client and streaming microphone PCM when enabled (Host -> Viewer)
    private static class AudioManager {
        private final ServerSocket server;
        private final AtomicBoolean shouldRun;
        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private volatile Socket client;
        private Thread acceptThread;
        private Thread sendThread;
        private TargetDataLine micLine;

        AudioManager(ServerSocket server, BooleanSupplier runFlag) {
            this.server = server;
            this.shouldRun = new AtomicBoolean(true);
            // Map to external flag: when runFlag turns false, we stop
            new Thread(() -> {
                while (runFlag.getAsBoolean()) {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                this.shouldRun.set(false);
                disable();
                closeQuiet(server);
                closeQuiet(client);
            }, "AudioRunGuard").start();
        }

        void startAcceptLoop() {
            acceptThread = new Thread(() -> {
                try {
                    while (shouldRun.get()) {
                        client = server.accept();
                        System.out.println("Audio client connected: " + client.getRemoteSocketAddress());
                        // If already enabled, (re)start sending to new client
                        if (enabled.get()) startSender();
                    }
                } catch (IOException e) {
                    if (shouldRun.get()) System.err.println("Audio accept stopped: " + e.getMessage());
                }
            }, "AudioAcceptLoop");
            acceptThread.start();
        }

        void enable() { enabled.set(true); startSender(); }
        void disable() { enabled.set(false); stopSender(); }

        private synchronized void startSender() {
            if (sendThread != null && sendThread.isAlive()) return;
            if (client == null || client.isClosed()) return;
            sendThread = new Thread(() -> {
                AudioFormat fmt = new AudioFormat(16000f, 16, 1, true, false);
                try (OutputStream out = client.getOutputStream()) {
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
                    if (!AudioSystem.isLineSupported(info)) {
                        System.err.println("Microphone line not supported for format");
                        return;
                    }
                    micLine = (TargetDataLine) AudioSystem.getLine(info);
                    micLine.open(fmt);
                    micLine.start();
                    byte[] buf = new byte[1600]; // ~50ms
                    while (shouldRun.get() && enabled.get() && !client.isClosed()) {
                        int n = micLine.read(buf, 0, buf.length);
                        if (n > 0) out.write(buf, 0, n);
                    }
                } catch (IOException | LineUnavailableException e) {
                    if (shouldRun.get()) System.err.println("Audio send error: " + e.getMessage());
                } finally {
                    if (micLine != null) {
                        try { micLine.stop(); micLine.close(); } catch (Exception ignore) {}
                        micLine = null;
                    }
                }
            }, "AudioSender");
            sendThread.start();
        }

        private synchronized void stopSender() {
            if (micLine != null) {
                try { micLine.stop(); micLine.close(); } catch (Exception ignore) {}
                micLine = null;
            }
            if (sendThread != null) {
                try { sendThread.join(50); } catch (InterruptedException ignore) {}
                sendThread = null;
            }
        }

        private void closeQuiet(Closeable c) { try { if (c != null) c.close(); } catch (Exception ignore) {} }
        private void closeQuiet(ServerSocket s) { try { if (s != null) s.close(); } catch (Exception ignore) {} }
    }

    // Manages receiving PCM from viewer and playing on host speakers when enabled (Viewer -> Host)
    private static class UplinkManager {
        private final ServerSocket server;
        private final AtomicBoolean shouldRun;
        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private volatile Socket client;
        private Thread acceptThread;
        private Thread playThread;
        private SourceDataLine speakerLine;
        private ByteArrayOutputStream sample = new ByteArrayOutputStream();

        UplinkManager(ServerSocket server, BooleanSupplier runFlag) {
            this.server = server;
            this.shouldRun = new AtomicBoolean(true);
            new Thread(() -> {
                while (runFlag.getAsBoolean()) {
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                this.shouldRun.set(false);
                disable();
                closeQuiet(server);
                closeQuiet(client);
            }, "UplinkRunGuard").start();
        }

        void startAcceptLoop() {
            acceptThread = new Thread(() -> {
                try {
                    while (shouldRun.get()) {
                        client = server.accept();
                        System.out.println("Audio uplink client connected: " + client.getRemoteSocketAddress());
                        if (enabled.get()) startPlayer();
                    }
                } catch (IOException e) {
                    if (shouldRun.get()) System.err.println("Audio uplink accept stopped: " + e.getMessage());
                }
            }, "AudioUplinkAcceptLoop");
            acceptThread.start();
        }

        void enable() { enabled.set(true); startPlayer(); }
        void disable() { enabled.set(false); stopPlayer(); }

        private synchronized void startPlayer() {
            if (playThread != null && playThread.isAlive()) return;
            if (client == null || client.isClosed()) return;
            playThread = new Thread(() -> {
                // Match Viewer uplink format (44.1kHz, mono, 16-bit, LE)
                AudioFormat fmt = new AudioFormat(44100f, 16, 1, true, false);
                try (InputStream in = client.getInputStream()) {
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    if (!AudioSystem.isLineSupported(info)) {
                        System.err.println("Host speaker line not supported for format");
                        return;
                    }
                    speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                    speakerLine.open(fmt);
                    speakerLine.start();
                    // Buffer ~50ms @ 44.1kHz mono 16-bit ≈ 4410 bytes
                    byte[] buf = new byte[4410];
                    int n;
                    while (shouldRun.get() && enabled.get() && !client.isClosed() && (n = in.read(buf)) != -1) {
                        if (n > 0) {
                            speakerLine.write(buf, 0, n);
                            sample.write(buf, 0, n);
                        }
                    }
                } catch (IOException | LineUnavailableException e) {
                    if (shouldRun.get()) System.err.println("Audio uplink play error: " + e.getMessage());
                } finally {
                    if (speakerLine != null) {
                        try { speakerLine.drain(); speakerLine.stop(); speakerLine.close(); } catch (Exception ignore) {}
                        speakerLine = null;
                    }
                    if (sample.size() > 0) {
                        try { writeWavPcm16Le(getAudioSaveFile("host_received_test"), sample.toByteArray(), 16000, 1); } catch (Exception ignore) {}
                    }
                }
            }, "AudioUplinkPlayer");
            playThread.start();
        }

        private synchronized void stopPlayer() {
            if (speakerLine != null) {
                try { speakerLine.stop(); speakerLine.close(); } catch (Exception ignore) {}
                speakerLine = null;
            }
            if (playThread != null) {
                try { playThread.join(50); } catch (InterruptedException ignore) {}
                playThread = null;
            }
        }

        private void closeQuiet(Closeable c) { try { if (c != null) c.close(); } catch (Exception ignore) {} }
        private void closeQuiet(ServerSocket s) { try { if (s != null) s.close(); } catch (Exception ignore) {} }
    }

    private static int getKeyCode(String keyCode) {
        try {
            return KeyEvent.class.getField("VK_" + keyCode).getInt(null);
        } catch (Exception e) {
            // Handle special cases
            switch (keyCode) {
                case "SPACE": return KeyEvent.VK_SPACE;
                case "ENTER": return KeyEvent.VK_ENTER;
                case "TAB": return KeyEvent.VK_TAB;
                case "ESCAPE": return KeyEvent.VK_ESCAPE;
                case "BACK_SPACE": return KeyEvent.VK_BACK_SPACE;
                case "DELETE": return KeyEvent.VK_DELETE;
                case "UP": return KeyEvent.VK_UP;
                case "DOWN": return KeyEvent.VK_DOWN;
                case "LEFT": return KeyEvent.VK_LEFT;
                case "RIGHT": return KeyEvent.VK_RIGHT;

                // [THÊM] Ánh xạ phím số từ JavaFX (DIGITx) sang AWT (VK_x)
                case "DIGIT0": return KeyEvent.VK_0;
                case "DIGIT1": return KeyEvent.VK_1;
                case "DIGIT2": return KeyEvent.VK_2;
                case "DIGIT3": return KeyEvent.VK_3;
                case "DIGIT4": return KeyEvent.VK_4;
                case "DIGIT5": return KeyEvent.VK_5;
                case "DIGIT6": return KeyEvent.VK_6;
                case "DIGIT7": return KeyEvent.VK_7;
                case "DIGIT8": return KeyEvent.VK_8;
                case "DIGIT9": return KeyEvent.VK_9;

                // Các phím điều khiển khác
                case "CONTROL": return KeyEvent.VK_CONTROL;
                case "SHIFT": return KeyEvent.VK_SHIFT;
                case "ALT": return KeyEvent.VK_ALT;
                case "CAPS": return KeyEvent.VK_CAPS_LOCK;

                default: return -1;
            }
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/ultraviewdemo/demoView/host-view.fxml"));
        Scene scene = new Scene(loader.load(), 600, 500);
        // String cssPath = getClass().getResource("/com/example/ultraviewdemo/demoView/ultraview.css").toExternalForm();
        // scene.getStylesheets().add(cssPath);
        stage.setTitle("UltraView Remote - Host");
        stage.setScene(scene);
        // Bind controller for chat updates
        try {
            HostController ctrl = loader.getController();
            if (ctrl != null) bindController(ctrl);
        } catch (Exception ignore) {}
        // Initialize independent Host chat window and wire send handler
        Platform.runLater(() -> {
            try {
                System.out.println("[HostClient] Initializing HostChatWindow...");
                HostChatWindow.initIfNeeded();
                System.out.println("[HostClient] Setting onSend callback...");
                HostChatWindow.setOnSend(HostClient::sendChatFromUI);
                System.out.println("[HostClient] Showing HostChatWindow...");
                HostChatWindow.show();
                System.out.println("[HostClient] HostChatWindow initialized successfully");
            } catch (Exception e) {
                System.err.println("[HostClient] Error initializing HostChatWindow: " + e.getMessage());
                e.printStackTrace();
            }
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}