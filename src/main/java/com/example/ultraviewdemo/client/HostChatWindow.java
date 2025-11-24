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
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.function.Consumer;

/**
 * A small, independent chat window for the Host to view messages from the Viewer and send replies.
 * This UI is standalone and does not depend on host-view.fxml.
 */
public class HostChatWindow {
    private static Stage stage;
    private static ListView<ChatMessage> chatList;
    private static TextField input;
    private static Consumer<String> onSend;

    private static class ChatMessage {
        final boolean self;
        final String sender;
        final String text;
        ChatMessage(boolean self, String sender, String text) {
            this.self = self;
            this.sender = sender;
            this.text = text;
        }
    }

    public static void initIfNeeded() {
        if (stage != null) {
            System.out.println("[HostChatWindow] initIfNeeded() called but stage already exists");
            return;
        }
        System.out.println("[HostChatWindow] initIfNeeded() called - creating new stage");
        Platform.runLater(() -> {
            if (stage != null) {
                System.out.println("[HostChatWindow] Stage already created in another thread, returning");
                return;
            }
            System.out.println("[HostChatWindow] Creating new HostChatWindow stage...");
            stage = new Stage(StageStyle.DECORATED);
            stage.setTitle("Host Chat");
            stage.setAlwaysOnTop(true);

            // Chat header with icon and title
            HBox headerRow = new HBox(8.0);
            headerRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label chatIcon = new Label("💬");
            chatIcon.setStyle("-fx-font-size: 14px;");
            Label chatTitle = new Label("Chat");
            chatTitle.setStyle("-fx-text-fill: #111827; -fx-font-size: 13px; -fx-font-weight: 700;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            headerRow.getChildren().addAll(chatIcon, chatTitle, spacer);

            // Styled chat list with custom cell factory
            chatList = new ListView<>();
            chatList.setPrefSize(280, 320);
            chatList.setStyle("-fx-background-color: #fafafa; -fx-background-insets: 0; -fx-background-radius: 10; -fx-border-color: #e5e7eb; -fx-border-radius: 10;");
            
            // Set custom cell factory for styled chat bubbles
            chatList.setCellFactory(lv -> new ListCell<>() {
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
                    bubble.setMaxWidth(200);
                    bubble.setStyle(item.self
                            ? "-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 10 12; -fx-background-radius: 16; -fx-font-weight: 500;"
                            : "-fx-background-color: #f3f4f6; -fx-text-fill: #111827; -fx-padding: 10 12; -fx-background-radius: 16; -fx-font-weight: 500;");
                    Label name = new Label(item.self ? "You" : (item.sender != null ? item.sender : "Peer"));
                    name.setStyle("-fx-font-size: 11px; -fx-text-fill: #6b7280; -fx-font-weight: 600;");
                    VBox msgBox = new VBox(4, name, bubble);
                    HBox row = new HBox();
                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    if (item.self) {
                        row.getChildren().addAll(spacer, msgBox);
                        row.setAlignment(Pos.CENTER_RIGHT);
                        msgBox.setAlignment(Pos.CENTER_RIGHT);
                    } else {
                        row.getChildren().addAll(msgBox, spacer);
                        row.setAlignment(Pos.CENTER_LEFT);
                        msgBox.setAlignment(Pos.CENTER_LEFT);
                    }
                    setGraphic(row);
                    setText(null);
                }
            });

            // Styled input field and send button
            input = new TextField();
            input.setPromptText("Type a message...");
            input.setStyle("-fx-background-radius: 10; -fx-border-radius: 10; -fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-padding: 8 10; -fx-text-inner-color: #111827; -fx-text-fill: #000000; -fx-prompt-text-fill: #9ca3af;");

            Button sendBtn = new Button("Send");
            sendBtn.setDefaultButton(true);
            sendBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 10; -fx-font-weight: 700; -fx-padding: 8 14;");

            HBox inputRow = new HBox(8, input, sendBtn);
            inputRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            HBox.setHgrow(input, Priority.ALWAYS);

            // Main container with card styling
            VBox chatContainer = new VBox(10.0, chatList, inputRow);
            chatContainer.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-padding: 12; -fx-border-color: #e5e7eb; -fx-border-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 6, 0, 0, 1);");

            VBox root = new VBox(8.0, headerRow, chatContainer);
            root.setPadding(new Insets(12));
            root.setStyle("-fx-background-color: #f8fafc;");

            // Wire actions
            Runnable sendAction = () -> {
                String text = input.getText();
                System.out.println("[HostChatWindow] Send button pressed, text: '" + text + "'");
                if (text == null) return;
                text = text.trim();
                if (text.isEmpty()) return;
                input.clear();
                System.out.println("[HostChatWindow] onSend callback is: " + (onSend != null ? "SET" : "NULL"));
                if (onSend != null) {
                    onSend.accept(text);
                    System.out.println("[HostChatWindow] onSend.accept() called");
                } else {
                    System.err.println("[HostChatWindow] ERROR: onSend callback is NULL!");
                }
            };
            sendBtn.setOnAction(e -> sendAction.run());
            input.setOnAction(e -> sendAction.run());

            stage.setScene(new Scene(root));
            System.out.println("[HostChatWindow] Stage created and scene set successfully");
        });
    }

    public static void show() {
        System.out.println("[HostChatWindow] show() called");
        initIfNeeded();
        Platform.runLater(() -> { 
            if (stage != null) {
                System.out.println("[HostChatWindow] Actually showing stage");
                stage.show();
                stage.toFront();
            } else {
                System.err.println("[HostChatWindow] ERROR: stage is null when trying to show!");
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
