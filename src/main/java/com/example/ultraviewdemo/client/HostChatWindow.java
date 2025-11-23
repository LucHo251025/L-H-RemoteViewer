package com.example.ultraviewdemo.client;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private static ListView<String> chatList;
    private static TextField input;
    private static Consumer<String> onSend;

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

            chatList = new ListView<>();
            chatList.setPrefSize(280, 320);

            input = new TextField();
            input.setPromptText("Type a message...");

            Button sendBtn = new Button("Send");
            sendBtn.setDefaultButton(true);

            HBox inputRow = new HBox(8, input, sendBtn);
            HBox.setHgrow(input, Priority.ALWAYS);

            VBox root = new VBox(8, chatList, inputRow);
            root.setPadding(new Insets(8));

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
                chatList.getItems().add((sender != null ? sender + ": " : "") + text);
                chatList.scrollTo(chatList.getItems().size() - 1);
            }
        });
    }
}
