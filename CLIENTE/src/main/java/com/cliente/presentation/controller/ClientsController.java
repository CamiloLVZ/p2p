package com.cliente.presentation.controller;

import com.cliente.application.service.ClientService;
import com.cliente.domain.model.Client;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class ClientsController {

    @FXML private TableView<Client> clientsTable;
    @FXML private TableColumn<Client, String>  colId;
    @FXML private TableColumn<Client, String>  colName;
    @FXML private TableColumn<Client, String>  colIp;
    @FXML private TableColumn<Client, Integer> colPort;
    @FXML private TableColumn<Client, String>  colStatus;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;

    /** Programmatically-created column — not declared in FXML to keep backward compat. */
    private TableColumn<Client, String> colServidorOrigen;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colIp.setCellValueFactory(new PropertyValueFactory<>("ip"));
        colPort.setCellValueFactory(new PropertyValueFactory<>("port"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(item.equalsIgnoreCase("Conectado")
                    ? "-fx-text-fill: #16a34a; -fx-font-weight: bold;"
                    : "-fx-text-fill: #dc2626;");
            }
        });

        // Add servidorOrigen column programmatically
        colServidorOrigen = new TableColumn<>("Servidor");
        colServidorOrigen.setPrefWidth(130);
        colServidorOrigen.setCellValueFactory(new PropertyValueFactory<>("servidorOrigen"));
        colServidorOrigen.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText("—");
                    setStyle("-fx-text-fill: #6b7280;");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold;");
                }
            }
        });
        clientsTable.getColumns().add(colServidorOrigen);

        clientsTable.setPlaceholder(new Label("No hay clientes conectados."));
        loadClients();
    }

    @FXML
    private void handleRefresh() {
        loadClients();
    }

    private void loadClients() {
        statusLabel.setText("Actualizando...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            try {
                List<Client> clients = ClientService.getInstance().getConnectedClients();
                Platform.runLater(() -> {
                    clientsTable.setItems(FXCollections.observableArrayList(clients));
                    statusLabel.setText(clients.size() + " cliente(s) conectado(s)");
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al obtener clientes: " + e.getMessage());
                    refreshButton.setDisable(false);
                });
            }
        }, "clients-loader").start();
    }
}
