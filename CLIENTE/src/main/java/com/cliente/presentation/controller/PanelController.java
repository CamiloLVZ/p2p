package com.cliente.presentation.controller;

import com.cliente.application.service.ConnectionService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;

public class PanelController {

    @FXML private Label clientIdLabel;
    @FXML private Label hostLabel;
    @FXML private Label portLabel;
    @FXML private Label protocolLabel;
    @FXML private Label statusLabel;
    @FXML private Circle statusDot;

    @FXML
    public void initialize() {
        ConnectionService conn = ConnectionService.getInstance();

        clientIdLabel.setText(conn.getClientId() != null ? conn.getClientId() : "Asignado por servidor");
        hostLabel.setText(conn.getHost() != null ? conn.getHost() : "—");
        portLabel.setText(conn.getPort() > 0 ? String.valueOf(conn.getPort()) : "—");
        protocolLabel.setText(conn.getProtocol() != null ? conn.getProtocol().name() : "—");

        boolean connected = conn.isConnected();
        statusLabel.setText(connected ? "Conectado" : "Desconectado");
        statusDot.getStyleClass().clear();
        statusDot.getStyleClass().add(connected ? "status-dot-online" : "status-dot-offline");
    }
}
