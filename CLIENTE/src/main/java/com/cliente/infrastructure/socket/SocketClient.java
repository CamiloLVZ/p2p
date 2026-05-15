package com.cliente.infrastructure.socket;

public interface SocketClient {
    void connect(String host, int port) throws Exception;
    String sendAndReceive(String json) throws Exception;
    void disconnect();
    boolean isConnected();
}
