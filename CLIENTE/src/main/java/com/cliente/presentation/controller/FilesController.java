package com.cliente.presentation.controller;

import com.cliente.MainApp;
import com.cliente.application.service.FileService;
import com.cliente.domain.model.Document;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class FilesController {

    @FXML private TableView<Document> filesTable;
    @FXML private TableColumn<Document, String> colName;
    @FXML private TableColumn<Document, String> colSize;
    @FXML private TableColumn<Document, String> colType;
    @FXML private TableColumn<Document, String> colDate;
    @FXML private TableColumn<Document, String> colHash;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;
    @FXML private Button downloadButton;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colSize.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(cd.getValue().getFormattedSize()));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        // Hash: muestra el valor completo — la celda se ajusta al ancho de la columna.
        // El usuario puede expandir la columna arrastrando para ver más contenido.
        colHash.setCellValueFactory(cd -> {
            String h = cd.getValue().getHashSha256();
            return new javafx.beans.property.SimpleStringProperty(h != null && !h.isBlank() ? h : "—");
        });
        // Celda con texto que se recorta con ellipsis pero muestra completo al expandir
        colHash.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTextOverrun(OverrunStyle.ELLIPSIS);
                    setTooltip(new Tooltip(item));
                }
            }
        });

        // Columna Origen: muestra LOCAL o EXTERNO (con IP si es externo)
        TableColumn<Document, String> colOrigen = new TableColumn<>("Origen");
        colOrigen.setCellValueFactory(cd -> {
            Document d = cd.getValue();
            String origen = d.getOrigen();
            if ("EXTERNO".equals(origen)) {
                String ip = d.getIpRemitente();
                String display = (ip != null && !ip.isBlank()) ? "EXTERNO (" + ip + ")" : "EXTERNO";
                return new javafx.beans.property.SimpleStringProperty(display);
            }
            return new javafx.beans.property.SimpleStringProperty(origen != null ? origen : "");
        });
        colOrigen.setPrefWidth(130);
        filesTable.getColumns().add(colOrigen);

        filesTable.setPlaceholder(new Label("No hay documentos disponibles."));
        filesTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, newVal) -> downloadButton.setDisable(newVal == null));
        downloadButton.setDisable(true);

        loadFiles();
    }

    @FXML
    private void handleRefresh() {
        loadFiles();
    }

    @FXML
    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar Documentos");
        chooser.setInitialFileName("documentos.csv");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(filesTable.getScene().getWindow());
        if (file == null) return;

        List<Document> items = filesTable.getItems();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Nombre,Tamaño,Tipo,Fecha,SHA-256");
            for (Document d : items)
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    d.getName(), d.getFormattedSize(), d.getType(), d.getDate(),
                    d.getHashSha256() != null ? d.getHashSha256() : "");
            new Alert(Alert.AlertType.INFORMATION, "Documentos exportados correctamente.").showAndWait();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Error al exportar: " + ex.getMessage()).showAndWait();
        }
    }

    @FXML
    private void handleDownload() {
        Document selected = filesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("fxml/download-dialog.fxml"));
            Parent root = loader.load();
            DownloadDialogController controller = loader.getController();
            controller.setDocument(selected);

            Stage dialog = new Stage();
            dialog.initOwner(MainApp.getPrimaryStage());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("Opciones de Descarga");
            dialog.setResizable(false);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                MainApp.class.getResource("css/main.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR,
                "No se pudo abrir el diálogo: " + e.getMessage()).showAndWait();
        }
    }

    private void loadFiles() {
        statusLabel.setText("Cargando documentos...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            try {
                List<Document> docs = FileService.getInstance().listDocuments();
                Platform.runLater(() -> {
                    filesTable.setItems(FXCollections.observableArrayList(docs));
                    statusLabel.setText(docs.size() + " documento(s) disponible(s)");
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    refreshButton.setDisable(false);
                });
            }
        }, "files-loader").start();
    }
}
