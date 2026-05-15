package com.cliente.presentation.controller;

import com.cliente.application.service.FileService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadController {

    @FXML private VBox dropZone;
    @FXML private TableView<UploadItem> uploadTable;
    @FXML private TableColumn<UploadItem, String>  colFileName;
    @FXML private TableColumn<UploadItem, String>  colFileSize;
    @FXML private TableColumn<UploadItem, String>  colStatus;
    @FXML private Button cancelButton;
    @FXML private Button uploadButton;
    @FXML private Label dropLabel;
    /** Campo de texto para el ID de cliente destinatario. Vacío = broadcast. */
    @FXML private TextField destinatarioField;

    private final ObservableList<UploadItem> queue = FXCollections.observableArrayList();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @FXML
    public void initialize() {
        colFileName.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        colFileSize.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if (item.equals("Completado")) setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                else if (item.startsWith("Error")) setStyle("-fx-text-fill: #dc2626;");
                else setStyle("-fx-text-fill: #2563eb;");
            }
        });

        uploadTable.setItems(queue);
        uploadTable.setPlaceholder(new Label("Ningún archivo en la cola."));
        uploadButton.setDisable(true);

        setupDropZone();
    }

    private void setupDropZone() {
        dropZone.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
                dropZone.getStyleClass().add("drop-zone-active");
            }
            e.consume();
        });

        dropZone.setOnDragExited(e -> dropZone.getStyleClass().remove("drop-zone-active"));

        dropZone.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) addFilesToQueue(db.getFiles());
            e.setDropCompleted(db.hasFiles());
            e.consume();
        });
    }

    @FXML
    private void handleSelectFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar archivos para subir");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));
        List<File> files = chooser.showOpenMultipleDialog(dropZone.getScene().getWindow());
        if (files != null) addFilesToQueue(files);
    }

    @FXML
    private void handleUpload() {
        for (UploadItem item : queue) {
            if (!item.getStatus().equals("Completado")) {
                submitUpload(item);
            }
        }
    }

    @FXML
    private void handleCancel() {
        queue.clear();
        uploadButton.setDisable(true);
    }

    private void addFilesToQueue(List<File> files) {
        for (File f : files) {
            boolean alreadyQueued = queue.stream()
                .anyMatch(i -> i.getFile().getAbsolutePath().equals(f.getAbsolutePath()));
            if (!alreadyQueued) queue.add(new UploadItem(f));
        }
        uploadButton.setDisable(queue.isEmpty());
    }

    private void submitUpload(UploadItem item) {
        item.setStatus("En cola...");
        uploadTable.refresh();

        String destinatario = (destinatarioField != null && destinatarioField.getText() != null
                && !destinatarioField.getText().isBlank())
                ? destinatarioField.getText().trim() : null;

        Task<Void> task = FileService.getInstance().createUploadTask(item.getFile(), destinatario);

        task.progressProperty().addListener((obs, old, newVal) -> {
            double pct = newVal.doubleValue();
            if (pct >= 0)
                javafx.application.Platform.runLater(() -> {
                    item.setStatus(String.format("%.0f%%", pct * 100));
                    uploadTable.refresh();
                });
        });

        task.setOnSucceeded(e ->
            javafx.application.Platform.runLater(() -> {
                item.setStatus("Completado");
                uploadTable.refresh();
            }));

        task.setOnFailed(e ->
            javafx.application.Platform.runLater(() -> {
                item.setStatus("Error: " + task.getException().getMessage());
                uploadTable.refresh();
            }));

        executor.submit(task);
    }

    // Inner model for the upload queue table
    public static class UploadItem {
        private final File file;
        private String status;

        public UploadItem(File file) {
            this.file = file;
            this.status = "Pendiente";
        }

        public File getFile() { return file; }
        public String getFileName() { return file.getName(); }
        public String getFileSize() { return formatSize(file.length()); }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
