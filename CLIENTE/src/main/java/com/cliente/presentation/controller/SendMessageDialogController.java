package com.cliente.presentation.controller;

import com.cliente.application.service.MessageService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SendMessageDialogController {

    @FXML private TextArea messageArea;
    @FXML private Button sendButton;
    @FXML private Label statusLabel;

    /** Destinatario field — added programmatically to keep FXML minimal. */
    private TextField destinatarioField;

    @FXML
    public void initialize() {
        statusLabel.setVisible(false);
        messageArea.textProperty().addListener((obs, old, newVal) ->
            sendButton.setDisable(newVal.isBlank()));
        sendButton.setDisable(true);

        // Inject destinatario field before the messageArea parent VBox
        VBox parent = (VBox) messageArea.getParent();
        int insertIdx = parent.getChildren().indexOf(messageArea);

        Label destLabel = new Label("Destinatario (opcional — vacío = broadcast)");
        destLabel.getStyleClass().add("field-label");
        VBox.setMargin(destLabel, new Insets(0, 0, 4, 0));

        destinatarioField = new TextField();
        destinatarioField.setPromptText("username (dejar vacío para broadcast)");
        VBox.setMargin(destinatarioField, new Insets(0, 0, 8, 0));

        parent.getChildren().add(insertIdx, destinatarioField);
        parent.getChildren().add(insertIdx, destLabel);
    }

    @FXML
    private void handleSend() {
        String content = messageArea.getText().trim();
        if (content.isEmpty()) return;

        String destinatario = destinatarioField != null
                ? destinatarioField.getText().trim()
                : null;
        String dest = (destinatario == null || destinatario.isBlank()) ? null : destinatario;

        sendButton.setDisable(true);
        statusLabel.setVisible(true);
        statusLabel.setText("Enviando...");

        new Thread(() -> {
            try {
                MessageService.getInstance().sendMessage(content, dest);
                javafx.application.Platform.runLater(() -> {
                    String destInfo = dest != null ? " → " + dest : " (broadcast)";
                    statusLabel.setText("Mensaje enviado correctamente" + destInfo + ".");
                    messageArea.clear();
                    if (destinatarioField != null) destinatarioField.clear();
                    sendButton.setDisable(false);
                    // close after short delay
                    new Thread(() -> {
                        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                        javafx.application.Platform.runLater(this::handleCancel);
                    }).start();
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    sendButton.setDisable(false);
                });
            }
        }, "send-message-thread").start();
    }

    @FXML
    private void handleCancel() {
        ((Stage) sendButton.getScene().getWindow()).close();
    }
}

