package com.cliente.domain.enums;

public enum Protocol {
    TCP("TCP — Transmission Control Protocol"),
    UDP("UDP — User Datagram Protocol");

    private final String label;

    Protocol(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override
    public String toString() { return name(); }
}
