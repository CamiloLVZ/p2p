package com.cliente.presentation.controller;

import com.cliente.application.service.FileService;
import com.cliente.domain.enums.DownloadMode;
import com.cliente.domain.model.Document;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;

public class DownloadDialogController {

    @FXML private Label fileNameLabel;
    @FXML private RadioButton radioOriginal;
    @FXML private RadioButton radioHash;
    @FXML private RadioButton radioEncrypted;
    @FXML private Button downloadButton;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;

    private Document document;
    private final ToggleGroup modeGroup = new ToggleGroup();

    @FXML
    public void initialize() {
        radioOriginal.setToggleGroup(modeGroup);
        radioHash.setToggleGroup(modeGroup);
        radioEncrypted.setToggleGroup(modeGroup);
        radioOriginal.setSelected(true);
        statusLabel.setVisible(false);
        progressBar.setVisible(false);
    }

    public void setDocument(Document document) {
        this.document = document;
        fileNameLabel.setText(document.getName());
    }

    @FXML
    private void handleDownload() {
        DownloadMode mode = getSelectedMode();

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Seleccionar carpeta de destino");
        File dir = chooser.showDialog(downloadButton.getScene().getWindow());
        if (dir == null) return;

        Path destination = dir.toPath();
        Task<File> task = FileService.getInstance().createDownloadTask(document, mode, destination);

        progressBar.setVisible(true);
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.setVisible(true);
        statusLabel.textProperty().bind(task.messageProperty());
        downloadButton.setDisable(true);

        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            statusLabel.setText("Descarga completada en: " + destination.toAbsolutePath());
            downloadButton.setDisable(false);
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            statusLabel.textProperty().unbind();
            statusLabel.setText("Error: " + task.getException().getMessage());
            downloadButton.setDisable(false);
        });

        new Thread(task, "download-thread").start();
    }

    @FXML
    private void handleCancel() {
        ((Stage) downloadButton.getScene().getWindow()).close();
    }

    private DownloadMode getSelectedMode() {
        if (radioHash.isSelected()) return DownloadMode.HASH;
        if (radioEncrypted.isSelected()) return DownloadMode.ENCRYPTED;
        return DownloadMode.ORIGINAL;
    }
}
