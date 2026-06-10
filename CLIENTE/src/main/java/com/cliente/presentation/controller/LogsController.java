package com.cliente.presentation.controller;

import com.cliente.application.service.LogService;
import com.cliente.application.service.ServerService;
import com.cliente.domain.model.LogEntry;
import com.cliente.domain.model.PaginatedResult;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LogsController {

    private static final String FUENTE_LOCAL = "Local";

    @FXML private TableView<LogEntry> logsTable;
    @FXML private TableColumn<LogEntry, String> colDate;
    @FXML private TableColumn<LogEntry, String> colTime;
    @FXML private TableColumn<LogEntry, String> colDescription;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;
    @FXML private ComboBox<String> sourceCombo;

    private int paginaActual = 0;
    private int tamanoPagina = 50;
    private int totalPaginas = 1;

    private Button btnAnterior;
    private Button btnSiguiente;
    private Label pageInfoLabel;

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));

        logsTable.setPlaceholder(new Label("No hay registros de log disponibles."));
        colDescription.prefWidthProperty().bind(
            logsTable.widthProperty().subtract(colDate.getWidth() + colTime.getWidth() + 20));

        initSourceCombo();
        addPaginationBar();
        loadLogs();
    }

    private void initSourceCombo() {
        if (sourceCombo == null) return; // modo sin FXML actualizado

        List<String> opciones = new ArrayList<>();
        opciones.add(FUENTE_LOCAL);

        new Thread(() -> {
            try {
                List<Map<String, Object>> servers = ServerService.getInstance().listarServidores();
                List<String> ids = new ArrayList<>();
                for (Map<String, Object> m : servers) {
                    Object id = m.get("servidorId");
                    if (id != null) ids.add(id.toString());
                }
                Platform.runLater(() -> {
                    opciones.addAll(ids);
                    sourceCombo.setItems(FXCollections.observableArrayList(opciones));
                    sourceCombo.getSelectionModel().selectFirst();
                    sourceCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                        paginaActual = 0;
                        loadLogs();
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    sourceCombo.setItems(FXCollections.observableArrayList(opciones));
                    sourceCombo.getSelectionModel().selectFirst();
                });
            }
        }, "servers-combo-loader").start();
    }

    private void addPaginationBar() {
        btnAnterior = new Button("← Anterior");
        btnSiguiente = new Button("Siguiente →");
        pageInfoLabel = new Label("Página 1 de 1");

        btnAnterior.setDisable(true);
        btnSiguiente.setDisable(true);

        btnAnterior.setOnAction(e -> {
            if (paginaActual > 0) {
                paginaActual--;
                loadLogs();
            }
        });

        btnSiguiente.setOnAction(e -> {
            if (paginaActual < totalPaginas - 1) {
                paginaActual++;
                loadLogs();
            }
        });

        HBox paginationBar = new HBox(10);
        paginationBar.setAlignment(Pos.CENTER);
        paginationBar.getChildren().addAll(btnAnterior, pageInfoLabel, btnSiguiente);

        // logsTable's parent is the root VBox; append the pagination bar after the table
        VBox parent = (VBox) logsTable.getParent();
        parent.getChildren().add(paginationBar);
    }

    @FXML
    private void handleRefresh() {
        paginaActual = 0;
        loadLogs();
    }

    @FXML
    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar Logs");
        chooser.setInitialFileName("logs_sistema.csv");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(logsTable.getScene().getWindow());
        if (file == null) return;

        List<LogEntry> items = logsTable.getItems();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Fecha,Hora,Descripción");
            for (LogEntry e : items)
                pw.printf("\"%s\",\"%s\",\"%s\"%n", e.getDate(), e.getTime(), e.getDescription());
            new Alert(Alert.AlertType.INFORMATION, "Logs exportados correctamente.").showAndWait();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Error al exportar: " + ex.getMessage()).showAndWait();
        }
    }

    private void loadLogs() {
        statusLabel.setText("Cargando logs...");
        refreshButton.setDisable(true);
        if (btnAnterior != null) btnAnterior.setDisable(true);
        if (btnSiguiente != null) btnSiguiente.setDisable(true);

        final int pagina = paginaActual;
        final int tamano = tamanoPagina;
        final String fuente = (sourceCombo != null && sourceCombo.getValue() != null)
                ? sourceCombo.getValue() : FUENTE_LOCAL;

        new Thread(() -> {
            try {
                PaginatedResult<LogEntry> result;
                if (FUENTE_LOCAL.equals(fuente)) {
                    result = LogService.getInstance().getLogs(pagina, tamano);
                } else {
                    result = LogService.getInstance().getRemoteLogs(fuente, pagina, tamano);
                }
                Platform.runLater(() -> {
                    logsTable.setItems(FXCollections.observableArrayList(result.getRegistros()));
                    totalPaginas = Math.max(1, result.getTotalPaginas());
                    paginaActual = result.getPagina();

                    String pageInfo = "Página %d de %d (%d registros)".formatted(
                            paginaActual + 1, totalPaginas, result.getTotalRegistros());
                    pageInfoLabel.setText(pageInfo);
                    statusLabel.setText("%d registro(s) en esta página".formatted(result.getRegistros().size()));

                    btnAnterior.setDisable(paginaActual <= 0);
                    btnSiguiente.setDisable(paginaActual >= totalPaginas - 1);
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al cargar logs" +
                            (FUENTE_LOCAL.equals(fuente) ? "" : " remotos de [" + fuente + "]")
                            + ": " + e.getMessage());
                    refreshButton.setDisable(false);
                    if (btnAnterior != null) btnAnterior.setDisable(false);
                    if (btnSiguiente != null) btnSiguiente.setDisable(false);
                });
            }
        }, "logs-loader").start();
    }
}
