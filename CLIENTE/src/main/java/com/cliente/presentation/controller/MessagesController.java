package com.cliente.presentation.controller;

import com.cliente.application.service.MessageService;
import com.cliente.domain.model.Message;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class MessagesController {

    @FXML private VBox messagesContainer;
    @FXML private ScrollPane scrollPane;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;

    private final List<Message> currentMessages = new ArrayList<>();

    /** Background polling — 3-second interval. */
    private Timeline pollingTimeline;

    @FXML
    public void initialize() {
        scrollPane.setFitToWidth(true);
        loadMessages();
        startPolling();

        // Stop polling automatically when this view is removed from the scene graph
        scrollPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopPolling();
            }
        });
    }

    // ── Public API for lifecycle hooks (called by MainController on disconnect) ──

    /** Start auto-refresh polling every 3 seconds. */
    public void startPolling() {
        if (pollingTimeline != null && pollingTimeline.getStatus() == Timeline.Status.RUNNING) {
            return; // already running
        }
        pollingTimeline = new Timeline(new KeyFrame(Duration.seconds(3), event -> pollMessages()));
        pollingTimeline.setCycleCount(Timeline.INDEFINITE);
        pollingTimeline.play();
    }

    /** Stop auto-refresh polling (call on disconnect). */
    public void stopPolling() {
        if (pollingTimeline != null) {
            pollingTimeline.stop();
            pollingTimeline = null;
        }
    }

    @FXML
    private void handleRefresh() {
        loadMessages();
    }

    @FXML
    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar Mensajes");
        chooser.setInitialFileName("mensajes.csv");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(messagesContainer.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Cliente,Contenido,Timestamp,Destinatario,ServidorOrigen");
            for (Message m : currentMessages)
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    m.getClientId() != null ? m.getClientId() : "",
                    m.getContent()  != null ? m.getContent().replace("\"", "\"\"") : "",
                    m.getTimestamp() != null ? m.getTimestamp() : "",
                    m.getDestinatario() != null ? m.getDestinatario() : "",
                    m.getServidorOrigen() != null ? m.getServidorOrigen() : "");
            new Alert(Alert.AlertType.INFORMATION, "Mensajes exportados correctamente.").showAndWait();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Error al exportar: " + ex.getMessage()).showAndWait();
        }
    }

    /**
     * Silent background poll — only updates UI when message count changes.
     * Runs on the polling thread; UI updates pushed via Platform.runLater().
     */
    private void pollMessages() {
        new Thread(() -> {
            try {
                List<Message> messages = MessageService.getInstance().getMessages();
                if (messages.size() != currentMessages.size()) {
                    Platform.runLater(() -> applyMessages(messages));
                }
            } catch (Exception ignored) {
                // Silent poll failure — do not disrupt the UI
            }
        }, "messages-poll").start();
    }

    private void loadMessages() {
        statusLabel.setText("Cargando mensajes...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            try {
                List<Message> messages = MessageService.getInstance().getMessages();
                Platform.runLater(() -> {
                    applyMessages(messages);
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    refreshButton.setDisable(false);
                });
            }
        }, "messages-loader").start();
    }

    private void applyMessages(List<Message> messages) {
        currentMessages.clear();
        currentMessages.addAll(messages);
        messagesContainer.getChildren().clear();
        for (Message m : messages) {
            messagesContainer.getChildren().add(buildMessageCard(m));
        }
        statusLabel.setText(messages.size() + " mensaje(s) recibido(s)");
    }

    private VBox buildMessageCard(Message message) {
        VBox card = new VBox(6);
        card.getStyleClass().add("message-card");

        // ── Header: cliente + badge origen + timestamp ──
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label clientLabel = new Label(message.getClientId() != null ? message.getClientId() : "—");
        clientLabel.getStyleClass().add("message-client-id");

        String origenTexto = resolverTextoOrigen(message);
        Label origenLabel = new Label(origenTexto);
        origenLabel.getStyleClass().add(
            "LOCAL".equals(message.getOrigen()) ? "badge-local" : "badge-externo");

        // servidorOrigen badge (only shown when present)
        if (message.getServidorOrigen() != null && !message.getServidorOrigen().isBlank()) {
            Label servidorLabel = new Label("srv:" + message.getServidorOrigen());
            servidorLabel.getStyleClass().add("badge-externo");
            servidorLabel.setStyle("-fx-background-color: #7c3aed; -fx-text-fill: white; "
                    + "-fx-padding: 2 6 2 6; -fx-background-radius: 8;");
            header.getChildren().add(servidorLabel);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timestampLabel = new Label(message.getTimestamp() != null ? message.getTimestamp() : "");
        timestampLabel.getStyleClass().add("message-timestamp");

        // ── Badge PRIVADO / BROADCAST ──
        boolean esPrivado = message.getDestinatario() != null && !message.getDestinatario().isBlank();
        Label privacyBadge = new Label(esPrivado ? "PRIVADO" : "BROADCAST");
        privacyBadge.setStyle(esPrivado
            ? "-fx-background-color: #d97706; -fx-text-fill: white; -fx-padding: 2 6 2 6; -fx-background-radius: 8; -fx-font-size: 10;"
            : "-fx-background-color: #6b7280; -fx-text-fill: white; -fx-padding: 2 6 2 6; -fx-background-radius: 8; -fx-font-size: 10;");

        header.getChildren().addAll(clientLabel, origenLabel, privacyBadge, spacer, timestampLabel);

        // ── Destinatario line (only shown for unicast messages) ──
        if (esPrivado) {
            Label destLabel = new Label("→ " + message.getDestinatario());
            destLabel.getStyleClass().add("message-hash");
            destLabel.setStyle("-fx-text-fill: #d97706; -fx-font-style: italic;");
            card.getChildren().add(destLabel);
        }

        // ── Contenido del mensaje ──
        Label contentLabel = new Label(message.getContent() != null ? message.getContent() : "");
        contentLabel.getStyleClass().add("message-content");
        contentLabel.setWrapText(true);

        // ── SHA-256 completo (seleccionable, con wrap) ──
        String hash = message.getHashSha256();
        Label hashLabel = new Label("SHA-256: " + (hash != null && !hash.isBlank() ? hash : "—"));
        hashLabel.getStyleClass().add("message-hash");
        hashLabel.setWrapText(true);

        // ── Contenido cifrado (seleccionable, con wrap) ──
        String cifrado = message.getContenidoCifrado();
        Label cifradoLabel = new Label("Cifrado: " + (cifrado != null && !cifrado.isBlank() ? cifrado : "—"));
        cifradoLabel.getStyleClass().add("message-hash");
        cifradoLabel.setWrapText(true);

        card.getChildren().addAll(header, contentLabel, hashLabel, cifradoLabel);
        return card;
    }

    /**
     * If origin is EXTERNO, show the IP in parentheses.
     * If LOCAL, just show "LOCAL".
     */
    private String resolverTextoOrigen(Message message) {
        String origen = message.getOrigen();
        if (origen == null) return "";
        if ("EXTERNO".equals(origen)) {
            String ip = message.getIpRemitente();
            return (ip != null && !ip.isBlank()) ? "EXTERNO (" + ip + ")" : "EXTERNO";
        }
        return origen;
    }
}

