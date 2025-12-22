package com.example.ultraviewdemo.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.util.function.Consumer;

public class HostChatWindow {
    private static Stage stage;
    private static ListView<ChatMessage> chatList;
    private static TextField input;
    private static Consumer<String> onSend;
    private static Consumer<File> onFileSend;

    private static class ChatMessage {
        final boolean self;
        final String sender;
        final String text;
        final FileMessage fileMessage;
        
        ChatMessage(boolean self, String sender, String text) {
            this.self = self;
            this.sender = sender;
            this.text = text;
            this.fileMessage = null;
        }
        
        ChatMessage(boolean self, String sender, FileMessage fileMessage) {
            this.self = self;
            this.sender = sender;
            this.text = fileMessage.fileName;
            this.fileMessage = fileMessage;
        }
    }
    
    private static class FileMessage {
        final String fileName;
        final long fileSize;
        final String filePath;
        
        FileMessage(String fileName, long fileSize, String filePath) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.filePath = filePath;
        }
    }

    public static void initIfNeeded() {
        if (stage != null) return;

        Platform.runLater(() -> {
            if (stage != null) return;

            stage = new Stage(StageStyle.DECORATED);
            stage.setTitle("Host Chat");
            stage.setAlwaysOnTop(true);


            HBox headerRow = new HBox(8.0);
            headerRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label chatIcon = new Label("💬");
            chatIcon.setStyle("-fx-font-size: 14px;");
            Label chatTitle = new Label("Chat");
            chatTitle.setStyle("-fx-text-fill: #111827; -fx-font-size: 13px; -fx-font-weight: 700;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerRow.getChildren().addAll(chatIcon, chatTitle, spacer);

            chatList = new ListView<>();
            // Giảm kích thước PrefSize của list một chút cho vừa khung nhỏ
            chatList.setPrefSize(260, 300);
            chatList.setStyle("-fx-background-color: #fafafa; -fx-background-insets: 0; -fx-background-radius: 10; -fx-border-color: #e5e7eb; -fx-border-radius: 10;");

            // ... (Phần setCellFactory giữ nguyên) ...
            chatList.setCellFactory(lv -> new ListCell<>() {
                // ... (Code cũ giữ nguyên) ...
                @Override
                protected void updateItem(ChatMessage item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                        setText(null);
                        return;
                    }
                    Label bubble = new Label(item.text);
                    bubble.setWrapText(true);
                    bubble.setMaxWidth(180); // Giảm max width bong bóng chat cho vừa cửa sổ nhỏ
                    // ... (Phần style giữ nguyên) ...
                    bubble.setStyle(item.self
                            ? "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 8 10; -fx-background-radius: 12; -fx-font-weight: 500;"
                            : "-fx-background-color: #f3f4f6; -fx-text-fill: #111827; -fx-padding: 8 10; -fx-background-radius: 12; -fx-font-weight: 500;");

                    // ... (Phần còn lại của CellFactory giữ nguyên) ...
                    Label name = new Label(item.self ? "You" : (item.sender != null ? item.sender : "Peer"));
                    name.setStyle("-fx-font-size: 10px; -fx-text-fill: #6b7280; -fx-font-weight: 600;");
                    VBox msgBox = new VBox(2, name, bubble);
                    HBox row = new HBox();
                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);
                    if (item.self) {
                        row.getChildren().addAll(sp, msgBox);
                        row.setAlignment(Pos.CENTER_RIGHT);
                        msgBox.setAlignment(Pos.CENTER_RIGHT);
                    } else {
                        row.getChildren().addAll(msgBox, sp);
                        row.setAlignment(Pos.CENTER_LEFT);
                        msgBox.setAlignment(Pos.CENTER_LEFT);
                    }
                    setGraphic(row);
                    setText(null);
                }
            });

            input = new TextField();
            input.setPromptText("Type a message...");
            input.setStyle("-fx-background-color: transparent; -fx-text-fill: #111827; -fx-prompt-text-fill: #9ca3af; -fx-font-size: 12px; -fx-padding: 6 8; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #e5e7eb;");

            Button sendBtn = new Button("\u27a4"); // same arrow as viewer
            sendBtn.setDefaultButton(true);
            sendBtn.setStyle("-fx-background-color: linear-gradient(to right, #22d3ee, #0ea5e9); -fx-text-fill: white; -fx-background-radius: 10; -fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 32; -fx-min-height: 28; -fx-max-width: 32; -fx-max-height: 28; -fx-cursor: hand;");

            Button fileBtn = new Button("\ud83d\udcc1"); // file icon like viewer
            fileBtn.setStyle("-fx-background-color: rgba(148, 163, 184, 0.35); -fx-text-fill: #111827; -fx-background-radius: 10; -fx-font-size: 12px; -fx-min-width: 28; -fx-min-height: 28; -fx-max-width: 28; -fx-max-height: 28; -fx-cursor: hand;");
            fileBtn.setOnAction(e -> {
                FileChooser fileChooser = new FileChooser();
                File selectedFile = fileChooser.showOpenDialog(stage);
                if (selectedFile != null && onFileSend != null) {
                    onFileSend.accept(selectedFile);
                }
            });

            HBox inputRow = new HBox(6, fileBtn, input, sendBtn);
            inputRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            HBox.setHgrow(input, Priority.ALWAYS);

            VBox chatContainer = new VBox(8.0, chatList, inputRow);
            chatContainer.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-padding: 10; -fx-border-color: #e5e7eb; -fx-border-radius: 12;");

            VBox root = new VBox(8.0, headerRow, chatContainer);
            root.setPadding(new Insets(10));
            root.setStyle("-fx-background-color: #f8fafc;");
            // ---------------------

            // Wire actions (Giữ nguyên)
            Runnable sendAction = () -> {
                String text = input.getText();
                if (text == null) return;
                text = text.trim();
                if (text.isEmpty()) return;
                input.clear();
                if (onSend != null) onSend.accept(text);
            };
            sendBtn.setOnAction(e -> sendAction.run());
            input.setOnAction(e -> sendAction.run());

            // [THAY ĐỔI QUAN TRỌNG] Set kích thước cố định nhỏ gọn: Rộng 300, Cao 400
            stage.setScene(new Scene(root, 300, 400));
        });
    }

    public static void show() {
        initIfNeeded();
        Platform.runLater(() -> {
            if (stage != null) {
                // [FIX] Đã XÓA dòng stage.setFullScreen(true);

                stage.show();
                stage.toFront();

                // [TÙY CHỌN] Đặt vị trí cửa sổ ở góc dưới bên phải màn hình
                try {
                    javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
                    stage.setX(bounds.getMaxX() - 320); // 320 là khoảng cách từ cạnh phải
                    stage.setY(bounds.getMaxY() - 420); // 420 là khoảng cách từ cạnh dưới
                } catch (Exception ignored) {}
            }
        });
    }

    public static void hide() {
        Platform.runLater(() -> { if (stage != null) stage.hide(); });
    }

    public static void setOnSend(Consumer<String> handler) {
        System.out.println("[HostChatWindow] setOnSend called with handler: " + (handler != null ? "NOT NULL" : "NULL"));
        onSend = handler;
    }

    public static void setOnFileSend(Consumer<File> handler) {
        onFileSend = handler;
    }

    public static void addMessage(String sender, String text) {
        Platform.runLater(() -> {
            if (chatList != null) {
                boolean self = "Host".equalsIgnoreCase(sender);
                chatList.getItems().add(new ChatMessage(self, sender, text));
                chatList.scrollTo(chatList.getItems().size() - 1);
            }
        });
    }
}
