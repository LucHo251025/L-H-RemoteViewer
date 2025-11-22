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
    
    public static void sendHostChat(String text, String hostId) {
        try {
            Socket s = currentControlSocket;
            if (s == null || s.isClosed()) return;
            MessageModel reply = new MessageModel(Constant.ACTION_HOST, hostId);
            reply.setMessage("CHAT:" + text);
            SocketMethodHelpers.sendMessage(s, reply);
        } catch (Exception ignored) {}
    }

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
                controlSocketRef = controlSocket;
                currentControlSocket = controlSocket;
                MessageModel hostControlModel = SocketMethodHelpers.readMessage(controlSocket);
                // Authenticate viewer before handling control commands
                if (hostControlModel == null || hostControlModel.getPartner_password() == null || !hostControlModel.getPartner_password().equals(password)) {
                    try { controlSocket.close(); } catch (Exception ignore) {}
                    return;
                }
                Robot robot = new Robot();
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

                while (shouldRun.getAsBoolean() && hostControlModel != null) {
                    handleControlCommand(robot, hostControlModel.getMessage(), screenRect, audioManager, uplinkManager, controlSocket, hostId);
                    hostControlModel = SocketMethodHelpers.readMessage(controlSocket);
                }
            } catch (Exception e) {
                System.err.println("Control connection error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                currentControlSocket = null;
                controlSocketRef = null;
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
                        
                        // Scale coordinates to actual screen size
                        int screenX = (int) (x * screenRect.width / 1000);
                        int screenY = (int) (y * screenRect.height / 700);
                        
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
                        
                        int screenX = (int) (x * screenRect.width / 1000);
                        int screenY = (int) (y * screenRect.height / 700);
                        
                        robot.mouseMove(screenX, screenY);
                    }
                    break;
                    
                case "MOUSE_SCROLL":
                    if (parts.length >= 4) {
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        double deltaY = Double.parseDouble(parts[3]);
                        
                        int screenX = (int) (x * screenRect.width / 1000);
                        int screenY = (int) (y * screenRect.height / 700);
                        
                        robot.mouseMove(screenX, screenY);
                        robot.mouseWheel((int) (deltaY / 40)); // Scale scroll amount
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
                            robot.keyPress(KeyEvent.getExtendedKeyCodeForChar(c));
                            robot.keyRelease(KeyEvent.getExtendedKeyCodeForChar(c));
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
                    System.out.println("Received chat from viewer: " + text);
                    Platform.runLater(() -> {
                        try {
                            HostChatWindow.initIfNeeded();
                            HostChatWindow.show();
                            HostChatWindow.addMessage("Viewer", text);
                        } catch (Exception e) {
                            System.err.println("Error showing chat message: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
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
                showChatError("Không gửi được chat: hostId bị rỗng hoặc null.");
                return;
            }

            if (currentControlSocket == null || currentControlSocket.isClosed()) {
                showChatError("Không gửi được chat: chưa có Viewer kết nối hoặc kết nối điều khiển đã mất.");
                return;
            }

            System.out.println("Sending chat message from host to viewer: " + text);

            synchronized (controlWriteLock) {
                sendHostChat(text, hostIdRef);
                System.out.println("Chat message sent to viewer over control socket");
            }
            
            // Update UI immediately
            Platform.runLater(() -> {
                HostChatWindow.initIfNeeded();
                HostChatWindow.show();
                HostChatWindow.addMessage("Host", text);
            });
        } catch (Exception e) {
            showChatError("Lỗi gửi chat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Expose connection state so HostController can show a friendly message instead of failing silently
    public static boolean isControlConnected() {
        return controlSocketRef != null && !controlSocketRef.isClosed();
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
            HostChatWindow.initIfNeeded();
            HostChatWindow.setOnSend(HostClient::sendChatFromUI);
            HostChatWindow.show();
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
