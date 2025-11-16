package com.example.ultraviewdemo.peer;

import com.example.ultraviewdemo.helpers.Constant;
import com.example.ultraviewdemo.helpers.SocketMethodHelpers;
import com.example.ultraviewdemo.models.MessageModel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public class HostPeerServer {
    private final int streamPort;
    private final int controlPort;
    private final String hostId;
    private final String password;
    private final BooleanSupplier shouldRun;

    private ServerSocket streamServer;
    private ServerSocket controlServer;
    private final List<Socket> viewers = Collections.synchronizedList(new ArrayList<>());

    public HostPeerServer(int streamPort, int controlPort, String hostId, String password, BooleanSupplier shouldRun) {
        this.streamPort = streamPort;
        this.controlPort = controlPort;
        this.hostId = hostId;
        this.password = password;
        this.shouldRun = shouldRun;
    }

    public void start() throws IOException {
        streamServer = new ServerSocket(streamPort);
        controlServer = new ServerSocket(controlPort);

        new Thread(this::acceptStreamClients, "HostPeer-Stream-Accept").start();
        new Thread(this::streamLoop, "HostPeer-Stream-Loop").start();
        new Thread(this::acceptControlClients, "HostPeer-Control-Accept").start();
    }

    private void acceptStreamClients() {
        try {
            while (shouldRun.getAsBoolean()) {
                Socket s = streamServer.accept();
                new Thread(() -> handleNewViewer(s)).start();
            }
        } catch (IOException e) {
            // ignore on shutdown
        }
    }

    private void handleNewViewer(Socket s) {
        try {
            MessageModel hello = SocketMethodHelpers.readMessage(s);
            if (hello.getAction() == Constant.ACTION_VIEWER
                    && hostId.equals(hello.getPartner_id())
                    && password.equals(hello.getPartner_password())) {
                viewers.add(s);
            } else {
                s.close();
            }
        } catch (Exception e) {
            try { s.close(); } catch (IOException ignore) {}
        }
    }

    private void streamLoop() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            while (shouldRun.getAsBoolean()) {
                BufferedImage screen = robot.createScreenCapture(screenRect);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(screen, "jpg", baos);
                byte[] data = baos.toByteArray();

                synchronized (viewers) {
                    viewers.removeIf(Socket::isClosed);
                    for (int i = 0; i < viewers.size(); i++) {
                        Socket v = viewers.get(i);
                        try {
                            MessageModel frame = new MessageModel(Constant.ACTION_HOST, hostId);
                            frame.setOwner_password(password);
                            frame.setData(data);
                            SocketMethodHelpers.sendMessage(v, frame);
                        } catch (Exception e) {
                            try { v.close(); } catch (IOException ignore) {}
                        }
                    }
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            // stop
        }
    }

    private void acceptControlClients() {
        try {
            while (shouldRun.getAsBoolean()) {
                Socket s = controlServer.accept();
                new Thread(() -> handleControlClient(s)).start();
            }
        } catch (IOException e) {
            // ignore on shutdown
        }
    }

    private void handleControlClient(Socket s) {
        try {
            MessageModel hello = SocketMethodHelpers.readMessage(s);
            if (!(hello.getAction() == Constant.ACTION_VIEWER_CONTROLLER
                    && hostId.equals(hello.getPartner_id())
                    && password.equals(hello.getPartner_password()))) {
                s.close();
                return;
            }
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            while (shouldRun.getAsBoolean()) {
                MessageModel cmd = SocketMethodHelpers.readMessage(s);
                if (cmd == null || cmd.getMessage() == null) break;
                handleControlCommand(robot, cmd.getMessage(), screenRect);
            }
        } catch (Exception e) {
            try { s.close(); } catch (IOException ignore) {}
        }
    }

    private void handleControlCommand(Robot robot, String command, Rectangle screenRect) {
        try {
            String[] parts = command.split(":");
            if (parts.length < 2) return;
            String action = parts[0];
            switch (action) {
                case "MOUSE_CLICK":
                    if (parts.length >= 4) {
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        String button = parts[3];
                        int screenX = (int) (x * screenRect.width / 1000);
                        int screenY = (int) (y * screenRect.height / 700);
                        int buttonMask = "PRIMARY".equals(button) ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK :
                                "SECONDARY".equals(button) ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK :
                                        java.awt.event.InputEvent.BUTTON2_DOWN_MASK;
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
                        robot.mouseWheel((int) (deltaY / 40));
                    }
                    break;
                case "KEY_PRESSED":
                    if (parts.length >= 2) {
                        int key = getKeyCode(parts[1]);
                        if (key != -1) robot.keyPress(key);
                    }
                    break;
                case "KEY_RELEASED":
                    if (parts.length >= 2) {
                        int key = getKeyCode(parts[1]);
                        if (key != -1) robot.keyRelease(key);
                    }
                    break;
                case "KEY_TYPED":
                    if (parts.length >= 2) {
                        String character = parts[1];
                        if (character.length() == 1) {
                            char c = character.charAt(0);
                            robot.keyPress(java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c));
                            robot.keyRelease(java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c));
                        }
                    }
                    break;
            }
        } catch (Exception ignore) { }
    }

    private int getKeyCode(String keyCode) {
        try {
            return java.awt.event.KeyEvent.class.getField("VK_" + keyCode).getInt(null);
        } catch (Exception e) {
            switch (keyCode) {
                case "SPACE": return java.awt.event.KeyEvent.VK_SPACE;
                case "ENTER": return java.awt.event.KeyEvent.VK_ENTER;
                case "TAB": return java.awt.event.KeyEvent.VK_TAB;
                case "ESCAPE": return java.awt.event.KeyEvent.VK_ESCAPE;
                case "BACK_SPACE": return java.awt.event.KeyEvent.VK_BACK_SPACE;
                case "DELETE": return java.awt.event.KeyEvent.VK_DELETE;
                case "UP": return java.awt.event.KeyEvent.VK_UP;
                case "DOWN": return java.awt.event.KeyEvent.VK_DOWN;
                case "LEFT": return java.awt.event.KeyEvent.VK_LEFT;
                case "RIGHT": return java.awt.event.KeyEvent.VK_RIGHT;
                default: return -1;
            }
        }
    }
}
