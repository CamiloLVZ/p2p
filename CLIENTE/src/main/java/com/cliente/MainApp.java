package com.cliente;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import com.cliente.infrastructure.persistence.H2DatabaseManager;

import java.io.IOException;

public class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        H2DatabaseManager.inicializar();
        primaryStage = stage;
        primaryStage.setResizable(false);
        showConnectionScreen();
    }

    public static void showConnectionScreen() throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("fxml/connection.fxml"));
        Scene scene = new Scene(loader.load(), 480, 640);
        scene.getStylesheets().add(MainApp.class.getResource("css/main.css").toExternalForm());
        primaryStage.setTitle("Cliente JavaFX — Conexión al Servidor");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    public static void showMainWindow() throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 750);
        scene.getStylesheets().add(MainApp.class.getResource("css/main.css").toExternalForm());
        primaryStage.setTitle("Cliente JavaFX");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.centerOnScreen();
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    @Override
    public void stop() {
        H2DatabaseManager.cerrar();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
