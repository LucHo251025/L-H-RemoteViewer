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
            try { streamServer.close(); controlServer.close(); audioServer.close(); uplinkServer.close(); } catch (Exception ignore) {}
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
            } catch (Exception e) { e.printStackTrace(); }
        }, "HostControlAccept").start();
    }

    private static void handleControlCommand(Robot robot, MessageModel incoming) {
        try {
            String command = incoming.getMessage();
            // XỬ LÝ AUDIO COMMAND
            if (command.startsWith("AUDIO_CMD:")) {
                if (command.startsWith("AUDIO:")) {
                    String state = command.substring(6);
                    boolean enable = "ON".equals(state);
                    enableAudioSystem(enable);
                    return;
                }
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
        ServerSocket ss = new ServerSocket(); ss.setReuseAddress(true); ss.bind(new InetSocketAddress(port)); return ss;
    }

    private static int getKeyCode(String s) {
        // Giữ nguyên logic mapping phím của bạn
        try { return KeyEvent.class.getField("VK_" + s).getInt(null); } catch (Exception e) { return -1; }
    }

    // Classes AudioManager & UplinkManager giữ nguyên logic nhưng có thể static inner class
    static class AudioManager {
        private final ServerSocket server;
        private final AtomicBoolean shouldRun;
        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private volatile Socket client;
        AudioManager(ServerSocket server, BooleanSupplier runFlag) {
            this.server = server; this.shouldRun = new AtomicBoolean(true);
            new Thread(() -> { while(runFlag.getAsBoolean()){try{Thread.sleep(500);}catch(Exception e){}} close(); }).start();
        }
        void startAcceptLoop() { new Thread(() -> { try { while(shouldRun.get()){ client = server.accept(); if(enabled.get()) startStream(); } }catch(Exception e){} }).start(); }
        void enable() { enabled.set(true); startStream(); }
        void disable() { enabled.set(false); }
        private void startStream() {
            if(client == null || client.isClosed()) return;
            new Thread(() -> {
                try(OutputStream out = client.getOutputStream()) {
                    TargetDataLine mic = AudioSystem.getTargetDataLine(new AudioFormat(16000f,16,1,true,false));
                    mic.open(); mic.start(); byte[] b=new byte[1024];
                    while(shouldRun.get() && enabled.get()){ int n=mic.read(b,0,b.length); if(n>0) out.write(b,0,n); }
                    mic.close();
                } catch(Exception e){}
            }).start();
        }
        void close() { try{server.close();}catch(Exception e){} }
    }

    static class UplinkManager {
        // Tương tự AudioManager nhưng là Speaker (SourceDataLine)
        private final ServerSocket server;
        private final AtomicBoolean shouldRun;
        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private volatile Socket client;
        UplinkManager(ServerSocket server, BooleanSupplier runFlag) {
            this.server = server; this.shouldRun = new AtomicBoolean(true);
            new Thread(() -> { while(runFlag.getAsBoolean()){try{Thread.sleep(500);}catch(Exception e){}} close(); }).start();
        }
        void startAcceptLoop() { new Thread(() -> { try { while(shouldRun.get()){ client = server.accept(); if(enabled.get()) startPlay(); } }catch(Exception e){} }).start(); }
        void enable() { enabled.set(true); startPlay(); }
        void disable() { enabled.set(false); }
        private void startPlay() {
            if(client == null || client.isClosed()) return;
            new Thread(() -> {
                try(InputStream in = client.getInputStream()) {
                    SourceDataLine spk = AudioSystem.getSourceDataLine(new AudioFormat(44100f,16,1,true,false));
                    spk.open(); spk.start(); byte[] b=new byte[4096]; int n;
                    while(shouldRun.get() && enabled.get() && (n=in.read(b))!=-1){ if(n>0) spk.write(b,0,n); }
                    spk.close();
                } catch(Exception e){}
            }).start();
        }
        void close() { try{server.close();}catch(Exception e){} }
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