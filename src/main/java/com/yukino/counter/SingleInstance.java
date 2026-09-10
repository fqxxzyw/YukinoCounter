package com.yukino.counter;

import javax.swing.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class SingleInstance implements AutoCloseable {
    private static final int PORT = 39471;
    private ServerSocket server;
    private Thread listener;

    public boolean acquire(Runnable onShow, Runnable onExit, boolean requestExit) {
        try {
            server = new ServerSocket(PORT, 4, InetAddress.getLoopbackAddress());
            listener = new Thread(() -> listen(onShow, onExit), "single-instance");
            listener.setDaemon(true);
            listener.start();
            return true;
        } catch (IOException alreadyRunning) {
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), PORT)) {
                socket.getOutputStream().write((requestExit ? "EXIT\n" : "SHOW\n").getBytes(StandardCharsets.US_ASCII));
            } catch (IOException ignored) {}
            return false;
        }
    }

    private void listen(Runnable onShow, Runnable onExit) {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                String command = new String(socket.getInputStream().readNBytes(5), StandardCharsets.US_ASCII).strip();
                SwingUtilities.invokeLater(command.equals("EXIT") ? onExit : onShow);
            } catch (IOException e) {
                if (!server.isClosed()) e.printStackTrace();
            }
        }
    }

    @Override public void close() throws IOException { if (server != null) server.close(); }
}
