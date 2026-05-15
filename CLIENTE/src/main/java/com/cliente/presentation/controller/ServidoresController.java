package com.cliente.presentation.controller;

import com.cliente.application.service.ServerService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controlador de la vista de servidores peer conocidos.
 *
 * Carga la lista de servidores desde {@link ServerService#listarServidores()} en un
 * hilo background y la muestra en un TableView con columnas ID, Host, Puerto y Estado.
 */
public class ServidoresController {

    @FXML private TableView<ServidorItem> servidoresTable;
    @FXML private TableColumn<ServidorItem, String>  colId;
    @FXML private TableColumn<ServidorItem, String>  colHost;
    @FXML private TableColumn<ServidorItem, String>  colPuerto;
    @FXML private TableColumn<ServidorItem, String>  colEstado;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("servidorId"));
        colHost.setCellValueFactory(new PropertyValueFactory<>("host"));
        colPuerto.setCellValueFactory(new PropertyValueFactory<>("puerto"));
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estado"));

        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                boolean activo = item.equalsIgnoreCase("CONECTADO") || item.equalsIgnoreCase("ACTIVO");
                setStyle(activo
                        ? "-fx-text-fill: #16a34a; -fx-font-weight: bold;"
                        : "-fx-text-fill: #6b7280;");
            }
        });

        servidoresTable.setPlaceholder(new Label("No hay servidores conocidos."));
        loadServidores();
    }

    @FXML
    private void handleRefresh() {
        loadServidores();
    }

    private void loadServidores() {
        statusLabel.setText("Cargando servidores...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            try {
                List<Map<String, Object>> raw = ServerService.getInstance().listarServidores();
                List<ServidorItem> items = new ArrayList<>();
                for (Map<String, Object> m : raw) {
                    items.add(ServidorItem.fromMap(m));
                }
                Platform.runLater(() -> {
                    ObservableList<ServidorItem> data = FXCollections.observableArrayList(items);
                    servidoresTable.setItems(data);
                    statusLabel.setText(items.size() + " servidor(es) conocido(s)");
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al obtener servidores: " + e.getMessage());
                    refreshButton.setDisable(false);
                });
            }
        }, "servidores-loader").start();
    }

    // ── Inner model ───────────────────────────────────────────────────────────

    public static class ServidorItem {
        private final String servidorId;
        private final String host;
        private final String puerto;
        private final String estado;

        public ServidorItem(String servidorId, String host, String puerto, String estado) {
            this.servidorId = servidorId;
            this.host = host;
            this.puerto = puerto;
            this.estado = estado;
        }

        public static ServidorItem fromMap(Map<String, Object> m) {
            String id     = m.getOrDefault("servidorId", "—").toString();
            String host   = m.getOrDefault("host", "—").toString();
            Object pObj   = m.get("puerto");
            String puerto = pObj != null ? pObj.toString() : "—";
            String estado = m.getOrDefault("estado", "DESCONOCIDO").toString();
            return new ServidorItem(id, host, puerto, estado);
        }

        public String getServidorId() { return servidorId; }
        public String getHost() { return host; }
        public String getPuerto() { return puerto; }
        public String getEstado() { return estado; }
    }
}
