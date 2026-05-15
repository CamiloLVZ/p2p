package com.cliente.presentation.controller;

import com.cliente.MainApp;
import com.cliente.application.service.ConnectionService;
import com.cliente.domain.enums.Protocol;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ConnectionController {

    @FXML private TextField usernameField;
    @FXML private TextField ipField;
    @FXML private TextField portField;
    @FXML private ComboBox<String> protocolCombo;
    @FXML private Label errorLabel;
    @FXML private Button connectButton;
    @FXML private ProgressIndicator progressIndicator;

    @FXML
    public void initialize() {
        protocolCombo.getItems().addAll("TCP", "UDP");
        protocolCombo.setValue("TCP");
        ipField.setText("127.0.0.1");
        portField.setText("8080");
        progressIndicator.setVisible(false);
    }

    @FXML
    private void handleConnect() {
        clearError();

        String username = usernameField.getText().trim();
        String ip       = ipField.getText().trim();
        String portStr  = portField.getText().trim();
        String proto    = protocolCombo.getValue();

        if (username.isEmpty()) { showError("Ingrese un nombre de usuario."); return; }
        if (ip.isEmpty())       { showError("Ingrese la dirección IP del servidor."); return; }
        if (portStr.isEmpty())  { showError("Ingrese el número de puerto."); return; }
        if (proto == null)      { showError("Seleccione el protocolo."); return; }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("Puerto inválido. Debe ser un número entre 1 y 65535.");
            return;
        }

        Protocol protocol = proto.equals("TCP") ? Protocol.TCP : Protocol.UDP;
        setConnecting(true);

        final int finalPort = port;
        new Thread(() -> {
            try {
                ConnectionService.getInstance().connect(ip, finalPort, protocol, username);
                Platform.runLater(() -> {
                    try {
                        MainApp.showMainWindow();
                    } catch (Exception e) {
                        setConnecting(false);
                        showError("Error al abrir la ventana principal.");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setConnecting(false);
                    showError(mapError(e));
                });
            }
        }, "connect-thread").start();
    }

    private void setConnecting(boolean connecting) {
        connectButton.setDisable(connecting);
        connectButton.setText(connecting ? "Conectando..." : "Conectar");
        progressIndicator.setVisible(connecting);
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
    }

    private String mapError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "Error de conexión desconocido.";
        if (msg.contains("refused"))  return "Conexión rechazada. Verifique que el servidor esté activo.";
        if (msg.contains("timed out") || msg.contains("timeout"))
            return "Tiempo de espera agotado. El servidor no respondió.";
        if (msg.contains("unreachable") || msg.contains("No route"))
            return "Host inalcanzable. Verifique la dirección IP.";
        if (msg.contains("Capacidad") || msg.contains("full"))
            return "Capacidad máxima alcanzada. El servidor está lleno.";
        return "Error: " + msg;
    }
}
