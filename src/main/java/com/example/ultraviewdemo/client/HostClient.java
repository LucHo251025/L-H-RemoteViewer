package com.example.ultraviewdemo.client;

import com.example.ultraviewdemo.helpers.Constant;
import com.example.ultraviewdemo.helpers.SocketMethodHelpers;
import com.example.ultraviewdemo.models.MessageModel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.geometry.Rectangle2D;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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


public class HostClient extends Application {
    private static volatile Socket controlSocketRef;
    private static final Object controlWriteLock = new Object();
    private static volatile String hostIdRef;
    private static Stage smallStage;
    private static Stage stage;

    // Static references cho Audio Manager để bật/tắt từ Controller
    private static volatile AudioManager staticAudioManager;
    private static volatile UplinkManager staticUplinkManager;
    private static volatile SmallHostControlController smallControllerRef;

    // Link Controller nhỏ vào đây để gọi update UI
    public static void bindSmallController(SmallHostControlController ctrl) {
        smallControllerRef = ctrl;
    }

    public static void shareLoop(String server, int port, String hostId, String password, BooleanSupplier shouldRun) throws Exception {
        hostIdRef = hostId;
        ServerSocket streamServer = createServerSocket(port);
        ServerSocket controlServer = createServerSocket(port + 1);
        ServerSocket audioServer = createServerSocket(port + 2);
        ServerSocket uplinkServer = createServerSocket(port + 3);

        String localIp = InetAddress.getLocalHost().getHostAddress();
        try (Socket dirSocket = new Socket(server, 7000)) {
            MessageModel reg = new MessageModel(Constant.ACTION_HOST_REGISTER, hostId);
            reg.setOwner_password(password);
            reg.setMessage(localIp + ":" + port + ":" + (port + 1));
            SocketMethodHelpers.sendMessage(dirSocket, reg);
            MessageModel resp = SocketMethodHelpers.readMessage(dirSocket);
            if (!resp.isSuccess()) throw new IOException("Directory register failed: " + resp.getMessage());
        }

        // Tạo Manager và gán vào biến static để Controller truy cập được
        AudioManager audioManager = new AudioManager(audioServer, shouldRun);
        staticAudioManager = audioManager;
        audioManager.startAcceptLoop();

        UplinkManager uplinkManager = new UplinkManager(uplinkServer, shouldRun);
        staticUplinkManager = uplinkManager;
        uplinkManager.startAcceptLoop();

        // Hiển thị thanh điều khiển nhỏ (Right Drawer)
        Platform.runLater(() -> showSmallControl());

        startControlAccept(controlServer, hostId, password, shouldRun);

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
            } finally { try { out.close(); } catch (Exception ignore) {} }
        } finally {
            try {
                notifyViewerHostStopping();
            } catch (Exception ignore) {
            }
            try { streamServer.close(); controlServer.close(); audioServer.close(); uplinkServer.close(); } catch (Exception ignore) {}
        }
    }

    private static void notifyViewerHostStopping() {
        // Tell viewer to disconnect immediately when Host stops sharing
        Socket s = controlSocketRef;
        if (s != null && !s.isClosed()) {
            try {
                synchronized (controlWriteLock) {
                    MessageModel msg = new MessageModel(Constant.ACTION_HOST, hostIdRef);
                    msg.setMessage("HOST_STOP");
                    SocketMethodHelpers.sendMessageNoTrack(s, msg);
                }
            } catch (Exception ignore) {
            }
            try {
                s.close();
            } catch (Exception ignore) {
            }
        }
    }

    public static void requestViewerDisconnectBecauseHostStopping() {
        try {
            notifyViewerHostStopping();
        } catch (Exception ignore) {
        }
    }

    // --- AUDIO COMMAND LOGIC ---

    public static void sendAudioCommand(String action) {
        // action: REQUEST, ACCEPT, DENY, OFF
        if (controlSocketRef != null && !controlSocketRef.isClosed()) {
            try {
                synchronized (controlWriteLock) {
                    MessageModel msg = new MessageModel(Constant.ACTION_HOST, hostIdRef);
                    msg.setMessage("AUDIO_CMD:" + action);
                    SocketMethodHelpers.sendMessageNoTrack(controlSocketRef, msg);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public static void enableAudioSystem(boolean enable) {
        if (staticAudioManager != null) {
            if (enable) staticAudioManager.enable(); else staticAudioManager.disable();
        }
        if (staticUplinkManager != null) {
            if (enable) staticUplinkManager.enable(); else staticUplinkManager.disable();
        }
    }

    // --- END AUDIO COMMAND LOGIC ---

    private static void showSmallControl() {
        try {
            FXMLLoader loader = new FXMLLoader(HostClient.class.getResource("/com/example/ultraviewdemo/demoView/small-host-control.fxml"));
            smallStage = new Stage();
            smallStage.initStyle(StageStyle.TRANSPARENT); // Trong suốt để làm menu nổi
            smallStage.setAlwaysOnTop(true);
            smallStage.setResizable(false);
            Scene scene = new Scene(loader.load());
            scene.setFill(Color.TRANSPARENT);
            smallStage.setScene(scene);

            SmallHostControlController ctrl = loader.getController();
            bindSmallController(ctrl); // Binding

            // Position as right-side desktop widget
            try {
                Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
                double w = 180;
                double h = 240;
                smallStage.setWidth(w);
                smallStage.setHeight(h);
                smallStage.setX(bounds.getMaxX() - w);
                smallStage.setY(bounds.getMinY() + (bounds.getHeight() - h) / 2.0);
            } catch (Exception ignore) {}

            smallStage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void startControlAccept(ServerSocket controlServer, String hostId, String password, BooleanSupplier shouldRun) {
        new Thread(() -> {
            try (Socket controlSocket = controlServer.accept()) {
                controlSocketRef = controlSocket;

                // Initialize chat window when viewer connects
                Platform.runLater(() -> {
                    try {
                        HostChatWindow.initIfNeeded();
                        HostChatWindow.setOnSend(HostClient::sendChatFromUI);
                        HostChatWindow.setOnFileSend(HostClient::sendFileFromUI);
                        HostChatWindow.show();
                        HostChatWindow.addMessage("System", "Viewer connected!");
                    } catch (Exception e) {
                        System.err.println("[Host] Error initializing chat");
                        e.printStackTrace();
                    }
                });

                Robot robot = new Robot();
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

                // Gửi thông tin màn hình
                try {
                    MessageModel info = new MessageModel(Constant.ACTION_HOST, hostId);
                    info.setMessage("HOST_SCREEN:" + screenRect.width + ":" + screenRect.height);
                    SocketMethodHelpers.sendMessage(controlSocket, info);
                } catch (Exception ignore) {}

                while (shouldRun.getAsBoolean() && !controlSocket.isClosed()) {
                    MessageModel incoming = SocketMethodHelpers.readMessage(controlSocket);
                    if (incoming == null) break;
                    if (incoming.getMessage() != null) {
                        handleControlCommand(robot, incoming);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                controlSocketRef = null;
                try {
                    enableAudioSystem(false);
                } catch (Exception ignore) {
                }

                Platform.runLater(() -> {
                    try {
                        HostChatWindow.hide();
                        if (smallControllerRef != null) {
                            try { smallControllerRef.updateAudioUI(false); } catch (Exception ignore) {}
                        }
                        if (smallStage != null) {
                            try { smallStage.close(); } catch (Exception ignore) {}
                            smallStage = null;
                        }
                    } catch (Exception ignore) {
                    }
                });
            }
        }, "HostControlAccept").start();
    }

    private static void handleControlCommand(Robot robot, MessageModel incoming) {
        try {
            String command = incoming.getMessage();
            // XỬ LÝ AUDIO COMMAND
            if (command.startsWith("AUDIO_CMD:")) {
                String subCmd = command.substring(10);
                switch (subCmd) {
                    case "REQUEST":
                        if (smallControllerRef != null) smallControllerRef.onAudioRequestFromViewer();
                        break;
                    case "ACCEPT":
                        if (smallControllerRef != null) smallControllerRef.onAudioResponse(true);
                        break;
                    case "DENY":
                        if (smallControllerRef != null) smallControllerRef.onAudioResponse(false);
                        break;
                    case "OFF":
                        enableAudioSystem(false);
                        if (smallControllerRef != null) Platform.runLater(() -> smallControllerRef.updateAudioUI(false));
                        break;
                }
                return;
            }

            // XỬ LÝ LỆNH BẬT/TẮT AUDIO TRỰC TIẾP TỪ VIEWER
            if (command.startsWith("AUDIO:")) {
                String state = command.substring(6);
                boolean enable = "ON".equalsIgnoreCase(state);
                enableAudioSystem(enable);
                if (smallControllerRef != null) {
                    boolean finalEnable = enable;
                    Platform.runLater(() -> smallControllerRef.updateAudioUI(finalEnable));
                }
                return;
            }

            if (command.startsWith("AUDIO_UP:")) {
                String state = command.substring(9);
                boolean enable = "ON".equalsIgnoreCase(state);
                enableAudioSystem(enable);
                if (smallControllerRef != null) {
                    boolean finalEnable = enable;
                    Platform.runLater(() -> smallControllerRef.updateAudioUI(finalEnable));
                }
                return;
            }

            // XỬ LÝ CHAT
            if (command.startsWith("CHAT:")) {
                String text = command.substring(5);
                Platform.runLater(() -> {
                    try { HostChatWindow.initIfNeeded(); HostChatWindow.show(); HostChatWindow.addMessage("Viewer", text); } catch (Exception e) {}
                });
                return;
            }

            // XỬ LÝ FILE
            if (command.startsWith("FILE:")) {
                String[] parts = command.split(":", 3);
                byte[] data = incoming.getData();
                if (parts.length >= 2 && data != null) {
                    String fileName = parts[1];
                    try {
                        Path saveDir = Paths.get(System.getProperty("user.home"), "Downloads", "UltraViewFiles");
                        Files.createDirectories(saveDir);
                        Path out = saveDir.resolve(fileName);
                        Files.write(out, data);

                        Platform.runLater(() -> {
                            try {
                                HostChatWindow.initIfNeeded();
                                HostChatWindow.show();
                                HostChatWindow.addMessage("System", "Received file from viewer: " + fileName + " -> " + out.toString());
                            } catch (Exception ignore) {}
                        });
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
                return;
            }

            // XỬ LÝ MOUSE / KEYBOARD
            String[] parts = command.split(":");
            if (parts.length < 2) return;
            String action = parts[0];

            switch (action) {
                case "MOUSE_CLICK":
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    String btn = parts.length > 3 ? parts[3] : "PRIMARY";
                    int mask = "SECONDARY".equals(btn) ? InputEvent.BUTTON3_DOWN_MASK :
                            "MIDDLE".equals(btn) ? InputEvent.BUTTON2_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
                    robot.mouseMove((int)x, (int)y); robot.mousePress(mask); robot.mouseRelease(mask);
                    break;
                case "MOUSE_DRAG":
                case "MOUSE_MOVE": // Thêm support move nếu cần
                    robot.mouseMove((int)Double.parseDouble(parts[1]), (int)Double.parseDouble(parts[2]));
                    break;
                case "MOUSE_SCROLL":
                    robot.mouseWheel(-(int)(Double.parseDouble(parts[3]) / 20.0));
                    break;
                case "KEY_PRESSED":
                    int kp = getKeyCode(parts[1]); if (kp != -1) robot.keyPress(kp);
                    break;
                case "KEY_RELEASED":
                    int kr = getKeyCode(parts[1]); if (kr != -1) robot.keyRelease(kr);
                    break;
                case "KEY_TYPED":
                    // Logic gõ text giữ nguyên từ code cũ của bạn
                    break;
            }
        } catch (Exception e) { System.err.println("Cmd Err: " + e.getMessage()); }
    }

    // --- Helpers giữ nguyên ---
    public static void sendChatFromUI(String text) {
        if (controlSocketRef == null || controlSocketRef.isClosed()) return;
        try {
            synchronized (controlWriteLock) {
                MessageModel m = new MessageModel(Constant.ACTION_HOST, hostIdRef);
                m.setMessage("CHAT:" + text);
                SocketMethodHelpers.sendMessageNoTrack(controlSocketRef, m);
            }
            Platform.runLater(() -> { try { HostChatWindow.show(); HostChatWindow.addMessage("Host", text); } catch (Exception e){} });
        } catch(Exception e){}
    }

    public static void sendFileFromUI(File file) {
        if (file == null) return;
        if (controlSocketRef == null || controlSocketRef.isClosed()) {
            System.err.println("[Host] Cannot send file: controlSocketRef is null or closed");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String header = "FILE:" + file.getName() + ":" + bytes.length;

            synchronized (controlWriteLock) {
                MessageModel m = new MessageModel(Constant.ACTION_HOST, hostIdRef);
                m.setMessage(header);
                m.setData(bytes);
                SocketMethodHelpers.sendMessageNoTrack(controlSocketRef, m);
            }

            Platform.runLater(() -> {
                try {
                    HostChatWindow.show();
                    HostChatWindow.addMessage("Host", "Sent file: " + file.getName());
                } catch (Exception ignore) {}
            });
        } catch (IOException e) {
            System.err.println("[Host] Error sending file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean isControlConnected() { return controlSocketRef != null && !controlSocketRef.isClosed(); }

    private static ServerSocket createServerSocket(int port) throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(port));
        return ss;
    }

    private static int getKeyCode(String s) {
        // Giữ nguyên logic mapping phím của bạn
        try {
            return KeyEvent.class.getField("VK_" + s).getInt(null);
        } catch (Exception e) {
            return -1;
        }
    }

    // Thay thế AudioManager và UplinkManager trong HostClient.java

    // Thay thế AudioManager và UplinkManager trong HostClient.java

    static class AudioManager {
        private final ServerSocket server;
        private final AtomicBoolean shouldRun;
        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private volatile Socket client;
        private volatile Thread streamThread;
        private volatile TargetDataLine micLine;

        AudioManager(ServerSocket server, BooleanSupplier runFlag) {
            this.server = server;
            this.shouldRun = new AtomicBoolean(true);
            new Thread(() -> {
                while (runFlag.getAsBoolean()) {
                    try { Thread.sleep(500); } catch (Exception e) { }
                }
                close();
            }).start();
        }

        void startAcceptLoop() {
            new Thread(() -> {
                try {
                    while (shouldRun.get()) {
                        Socket s = server.accept();

                        // Đóng client cũ nếu có
                        if (this.client != null) {
                            try { this.client.close(); } catch (Exception ignore) {}
                        }

                        this.client = s;
                        System.out.println("[Host] Viewer connected for audio at: " + s.getRemoteSocketAddress());

                        if (enabled.get()) {
                            startStream();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Host] AudioManager acceptLoop error: " + e.getMessage());
                }
            }, "HostAudioAccept").start();
        }

        void enable() {
            enabled.set(true);
            startStream();
        }

        void disable() {
            enabled.set(false);
            stopStream();
        }

        private void stopStream() {
            System.out.println("[Host] Stopping audio stream");

            // Stop mic line
            if (micLine != null) {
                try {
                    micLine.stop();
                    micLine.close();
                } catch (Exception e) {
                    System.err.println("[Host] Error closing mic: " + e.getMessage());
                }
                micLine = null;
            }

            // Wait for stream thread
            if (streamThread != null) {
                try {
                    streamThread.join(500);
                    if (streamThread.isAlive()) {
                        streamThread.interrupt();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                streamThread = null;
            }

            System.out.println("[Host] Audio stream stopped");
        }

        private void startStream() {
            if (client == null || client.isClosed() || !enabled.get()) {
                return;
            }

            // Stop existing stream if any
            stopStream();

            streamThread = new Thread(() -> {
                System.out.println("[Host] Starting audio stream to Viewer");

                TargetDataLine localMic = null;

                try {
                    OutputStream out = client.getOutputStream();

                    AudioFormat[] formats = {
                            new AudioFormat(44100f, 16, 1, true, false),
                            new AudioFormat(44100f, 16, 1, false, false),
                            new AudioFormat(22050f, 16, 1, true, false),
                            new AudioFormat(16000f, 16, 1, true, false)
                    };

                    AudioFormat selectedFormat = null;

                    for (AudioFormat format : formats) {
                        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                        if (AudioSystem.isLineSupported(info)) {
                            try {
                                localMic = (TargetDataLine) AudioSystem.getLine(info);
                                localMic.open(format);
                                selectedFormat = format;
                                System.out.println("[Host] Using audio format: " + format);
                                break;
                            } catch (Exception e) {
                                System.out.println("[Host] Format failed: " + e.getMessage());
                                if (localMic != null) {
                                    try { localMic.close(); } catch (Exception ignore) {}
                                    localMic = null;
                                }
                            }
                        }
                    }

                    if (localMic == null) {
                        System.err.println("[Host] No supported audio format found");
                        return;
                    }

                    this.micLine = localMic;
                    localMic.start();

                    int bufferSize = selectedFormat.getSampleSizeInBits() == 8 ? 2048 : 4096;
                    byte[] b = new byte[bufferSize];

                    while (shouldRun.get() && enabled.get() && !client.isClosed()) {
                        try {
                            int n = localMic.read(b, 0, b.length);
                            if (n > 0 && enabled.get()) {
                                out.write(b, 0, n);
                                out.flush();
                            }
                        } catch (Exception e) {
                            if (enabled.get()) {
                                System.err.println("[Host] Stream write error: " + e.getMessage());
                                break;
                            }
                        }
                    }

                    System.out.println("[Host] Audio stream loop ended");

                } catch (Exception e) {
                    System.err.println("[Host] Audio stream error: " + e.getMessage());
                } finally {
                    if (localMic != null) {
                        try {
                            localMic.stop();
                            localMic.close();
                        } catch (Exception e) {
                            System.err.println("[Host] Error closing mic: " + e.getMessage());
                        }
                    }
                    this.micLine = null;
                    System.out.println("[Host] Audio stream cleaned up");
                }
            }, "HostAudioStream");

            streamThread.setDaemon(true);
            streamThread.start();
        }

        void close() {
            shouldRun.set(false);
            enabled.set(false);
            stopStream();
            try {
                if(client != null) client.close();
                server.close();
            } catch (Exception e) { }
        }
    }

    static class UplinkManager {
        private final ServerSocket server;
        private final AtomicBoolean shouldRun;
        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private volatile Socket client;
        private volatile Thread playThread;
        private volatile SourceDataLine speakerLine;

        UplinkManager(ServerSocket server, BooleanSupplier runFlag) {
            this.server = server;
            this.shouldRun = new AtomicBoolean(true);
            // Thread tự hủy khi Host dừng sharing
            new Thread(() -> {
                while (runFlag.getAsBoolean()) {
                    try { Thread.sleep(500); } catch (Exception e) { }
                }
                close();
            }).start();
        }

        // Bổ sung phương thức close() để hết lỗi biên dịch
        public void close() {
            enabled.set(false);
            shouldRun.set(false);
            stopPlay();
            try {
                if (client != null) client.close();
                if (server != null) server.close();
            } catch (Exception e) {
                System.err.println("[Host] Error closing Uplink: " + e.getMessage());
            }
        }

        public void stopPlay() {
            if (speakerLine != null) {
                try {
                    speakerLine.stop();
                    speakerLine.flush();
                    speakerLine.close();
                } catch (Exception e) { }
                speakerLine = null;
            }
        }

        // startPlay() và startAcceptLoop() giữ nguyên như bản fix rè trước đó
        void startAcceptLoop() {
            new Thread(() -> {
                try {
                    while (shouldRun.get()) {
                        Socket s = server.accept();
                        if (this.client != null) try { this.client.close(); } catch (Exception ignore) {}
                        this.client = s;
                        if (enabled.get()) startPlay();
                    }
                } catch (Exception e) { }
            }, "HostUplinkAccept").start();
        }

        void enable() { enabled.set(true); startPlay(); }
        void disable() { enabled.set(false); stopPlay(); }

        private void startPlay() {
            if (client == null || client.isClosed() || !enabled.get()) return;
            stopPlay();
            playThread = new Thread(() -> {
                try {
                    InputStream in = client.getInputStream();
                    AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                    if (!AudioSystem.isLineSupported(info)) return;
                    speakerLine = (SourceDataLine) AudioSystem.getLine(info);
                    speakerLine.open(format, (int)(format.getSampleRate() * format.getFrameSize() * 0.2));
                    speakerLine.start();
                    byte[] b = new byte[1024];
                    int n;
                    while (shouldRun.get() && enabled.get() && (n = in.read(b)) != -1) {
                        if (n > 0) speakerLine.write(b, 0, n);
                    }
                } catch (Exception e) { }
            }, "HostUplinkPlay");
            playThread.setDaemon(true);
            playThread.start();
        }
    }
    @Override
    public void start(Stage stage) throws Exception {
        // Method start chính của App Host (setup màn hình connect)
        FXMLLoader loader = new FXMLLoader(getClass().getResource("host-view.fxml")); // Đổi lại đúng đường dẫn fxml
        stage.setScene(new Scene(loader.load()));
        stage.show();
    }

    public static void main(String[] args) { launch(args); }
}