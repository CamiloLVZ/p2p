package com.cliente.domain.enums;

public enum DownloadMode {
    ORIGINAL("Original", "Descarga directa sin modificaciones"),
    HASH("Con Hash", "Incluye suma de comprobación SHA-256"),
    ENCRYPTED("Encriptado", "Cifrado AES-256 automático");

    private final String label;
    private final String description;

    DownloadMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
