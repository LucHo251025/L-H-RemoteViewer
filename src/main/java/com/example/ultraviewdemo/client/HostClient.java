package com.example.ultraviewdemo.client;

import com.example.ultraviewdemo.helpers.Constant;
import com.example.ultraviewdemo.helpers.SocketMethodHelpers;
import com.example.ultraviewdemo.models.MessageModel;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.function.BooleanSupplier;

public class HostClient extends Application {
    public static void shareLoop(String server, int port, String hostId, String password, BooleanSupplier shouldRun) throws Exception {
        // Start local stream/control servers on provided port and port+1
        ServerSocket streamServer = new ServerSocket(port);
        ServerSocket controlServer = new ServerSocket(port + 1);

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

        // Accept one control connection to handle input
        startControlAccept(controlServer, hostId, password, shouldRun);

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
        }
    }

    private static void startControlAccept(ServerSocket controlServer, String hostId, String password, BooleanSupplier shouldRun) {
        new Thread(() -> {
            try (Socket controlSocket = controlServer.accept()) {
                MessageModel hostControlModel = SocketMethodHelpers.readMessage(controlSocket);
                Robot robot = new Robot();
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

                // Send screen size info to viewer
                try {
                    MessageModel info = new MessageModel(Constant.ACTION_HOST, hostId);
                    info.setMessage("HOST_SCREEN:" + screenRect.width + ":" + screenRect.height);
                    SocketMethodHelpers.sendMessage(controlSocket, info);
                } catch (Exception ignore) {}

                while (shouldRun.getAsBoolean() && hostControlModel != null) {
                    handleControlCommand(robot, hostControlModel.getMessage(), screenRect);
                    hostControlModel = SocketMethodHelpers.readMessage(controlSocket);
                }
            } catch (Exception e) {
                System.err.println("Control connection error: " + e.getMessage());
            }
        }).start();
    }

    private static void handleControlCommand(Robot robot, String command, Rectangle screenRect) {
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
                        int wheelAmount = (int) Math.round(deltaY / 40.0);
                        if (wheelAmount == 0 && deltaY != 0) {
                            wheelAmount = deltaY > 0 ? 1 : -1;
                        }
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
                            robot.keyPress(KeyEvent.getExtendedKeyCodeForChar(c));
                            robot.keyRelease(KeyEvent.getExtendedKeyCodeForChar(c));
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error handling control command: " + e.getMessage());
        }
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
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
