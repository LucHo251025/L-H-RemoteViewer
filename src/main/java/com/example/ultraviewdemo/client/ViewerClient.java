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
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Pos;
import java.io.*;
import java.net.*;

public class ViewerClient extends Application {
    private String serverHost = "localhost";
    private int serverPort = 5000; // unused in P2P; kept for compatibility
    private String hostId = "";
    private String password = "";
    private ImageView remoteImageView;
    private Socket controlSocket;
    private MessageModel viewerControlModel;
    private volatile int hostScreenWidth = 1920;
    private volatile int hostScreenHeight = 1080;

    // P2P target resolved from directory server
    private String hostIp;
    private int hostStreamPort;
    private int hostControlPort;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader connectLoader = new FXMLLoader(getClass().getResource("/com/example/ultraviewdemo/demoView/connect-host.fxml"));
        Scene connectScene = new Scene(connectLoader.load(), 900, 650);
        String cssPath = getClass().getResource("/com/example/ultraviewdemo/demoView/ultraview.css").toExternalForm();
        connectScene.getStylesheets().add(cssPath);
        stage.setTitle("UltraView Remote - Connect");
        stage.setScene(connectScene);
        stage.setMinWidth(700);
        stage.setMinHeight(500);
        stage.centerOnScreen();
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
                System.out.println("Attempting to connect to control at " + hostIp + ":" + hostControlPort);
                controlSocket = new Socket(hostIp, hostControlPort);
                viewerControlModel = new MessageModel(Constant.ACTION_VIEWER_CONTROLLER, hostId);
                viewerControlModel.setPartner_password(password);
                viewerControlModel.setPartner_id(hostId);
                SocketMethodHelpers.sendMessage(controlSocket, viewerControlModel);

                System.out.println("Control connection established successfully!");

                // Start a reader thread to receive host screen info and any future control messages
                Thread reader = new Thread(() -> {
                    try {
                        while (true) {
                            MessageModel incoming = SocketMethodHelpers.readMessage(controlSocket);
                            if (incoming == null) break;
                            String msg = incoming.getMessage();
                            if (msg != null && msg.startsWith("HOST_SCREEN:")) {
                                String[] p = msg.split(":");
                                if (p.length >= 3) {
                                    try {
                                        int w = Integer.parseInt(p[1]);
                                        int h = Integer.parseInt(p[2]);
                                        hostScreenWidth = Math.max(1, w);
                                        hostScreenHeight = Math.max(1, h);
                                        System.out.println("Host screen size received: " + hostScreenWidth + "x" + hostScreenHeight);
                                    } catch (NumberFormatException ignore) {}
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Control reader ended: " + e.getMessage());
                    }
                });
                reader.setDaemon(true);
                reader.start();
            } catch (Exception e) {
                System.err.println("Failed to establish control connection: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
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
        launch();
    }
}
