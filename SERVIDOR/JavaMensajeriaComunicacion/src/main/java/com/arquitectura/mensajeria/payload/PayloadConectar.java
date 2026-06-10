package com.arquitectura.mensajeria.payload;

public class PayloadConectar {

    private String username;

    public PayloadConectar() {}

    public PayloadConectar(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}