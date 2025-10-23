package com.example.ultraviewdemo.server;

import com.example.ultraviewdemo.helpers.Constant;
import com.example.ultraviewdemo.helpers.SocketMethodHelpers;
import com.example.ultraviewdemo.models.MessageModel;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class DirectoryServer {
    private static final int PORT = 7000;

    private static class HostRecord {
        String password;
        String ip;
        int streamPort;
        int controlPort;
        long lastSeen;
    }

    private static final Map<String, HostRecord> hosts = new HashMap<>();

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Directory server started on port " + PORT);
        while (true) {
            Socket socket = server.accept();
            new Thread(() -> handle(socket)).start();
        }
    }

    private static void handle(Socket socket) {
        try {
            MessageModel req = SocketMethodHelpers.readMessage(socket);
            if (req.getAction() == Constant.ACTION_HOST_REGISTER) {
                MessageModel resp = new MessageModel();
                String hostId = req.getOwner_id();
                String password = req.getOwner_password();
                String payload = req.getMessage();
                String[] parts = payload != null ? payload.split(":") : new String[0];
                if (hostId == null || password == null || parts.length < 3) {
                    resp.setSuccess(false);
                    resp.setMessage("INVALID_REGISTER_PAYLOAD");
                } else {
                    HostRecord r = new HostRecord();
                    r.password = password;
                    r.ip = parts[0];
                    r.streamPort = Integer.parseInt(parts[1]);
                    r.controlPort = Integer.parseInt(parts[2]);
                    r.lastSeen = System.currentTimeMillis();
                    hosts.put(hostId, r);
                    resp.setSuccess(true);
                    resp.setMessage("REGISTERED");
                }
                SocketMethodHelpers.sendMessage(socket, resp);
            } else if (req.getAction() == Constant.ACTION_VIEWER_QUERY) {
                MessageModel resp = new MessageModel();
                String hostId = req.getPartner_id();
                String password = req.getPartner_password();
                HostRecord r = hosts.get(hostId);
                if (r == null) {
                    resp.setSuccess(false);
                    resp.setMessage("HOST_NOT_FOUND");
                } else if (!r.password.equals(password)) {
                    resp.setSuccess(false);
                    resp.setMessage("AUTH_FAILED");
                } else {
                    String payload = r.ip + ":" + r.streamPort + ":" + r.controlPort;
                    resp.setSuccess(true);
                    resp.setMessage(payload);
                }
                SocketMethodHelpers.sendMessage(socket, resp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignore) {}
        }
    }
}
