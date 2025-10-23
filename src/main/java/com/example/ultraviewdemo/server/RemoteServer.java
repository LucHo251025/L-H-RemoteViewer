package com.example.ultraviewdemo.server;

import com.example.ultraviewdemo.helpers.Constant;
import com.example.ultraviewdemo.helpers.SocketMethodHelpers;
import com.example.ultraviewdemo.models.MessageModel;

import java.io.*;
import java.net.*;
import java.util.*;

public class RemoteServer {
    private static final int PORT = 5000;
    private static final int CONTROL_PORT = 5001;
    //private static Socket hostSocket;
    //private static Socket hostControlSocket;
    //private static final List<Socket> viewers = Collections.synchronizedList(new ArrayList<>());
    //private static final List<Socket> controlViewers = Collections.synchronizedList(new ArrayList<>());
    private static final  Map<String, String> hostPassword = new HashMap<>();
    private static final  Map<String, List<Socket>> hostViewers = new HashMap<>();
    private static final  Map<String, Socket> hostControllerSockets = new HashMap<>();
    private static final  Map<String, Socket> hostControllerViewers = new HashMap<>();

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(PORT);
        ServerSocket controlServer = new ServerSocket(CONTROL_PORT);
        System.out.println("Server started on port " + PORT);
        System.out.println("Control server started on port " + CONTROL_PORT);

        // Start control server in separate thread
        new Thread(() -> {
            try {
                while (true) {
                    Socket socket = controlServer.accept();
                    new Thread(() -> handleControlClient(socket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        while (true) {
            Socket socket = server.accept();
            new Thread(() -> handleClient(socket)).start();
        }
    }

    private static void handleClient(Socket socket) {
        try {

            MessageModel messageModel = SocketMethodHelpers.readMessage(socket);

            if (messageModel.getAction() == Constant.ACTION_HOST) {
                String id = messageModel.getOwner_id();
                String password = messageModel.getOwner_password();
                System.out.println("Host connected");
                hostPassword.put(id, password);
                hostViewers.put(id, Collections.synchronizedList(new ArrayList<>()));
                new HostHandler(socket, id).start();
            } else if (messageModel.getAction() == Constant.ACTION_VIEWER) {

                String id = messageModel.getPartner_id();
                String password = messageModel.getPartner_password();

                String host_pass = hostPassword.get(id);
                List<Socket> host_viewer = hostViewers.get(id);
                if (host_pass == null || host_viewer == null) {
                    // Host not connected / not sharing yet
                    messageModel.setMessage("NOT_READY");
                    SocketMethodHelpers.sendMessage(socket, messageModel);
                    System.out.println("Viewer attempted before host ready");
                    socket.close();
                    return;
                }
                if (!host_pass.equals(password)) {
                    messageModel.setMessage("AUTH_FAILED");
                    SocketMethodHelpers.sendMessage(socket, messageModel);
                    System.out.println("Viewer auth failed");
                    socket.close();
                    return;
                }
                // Host is ready and password is correct
                messageModel.setMessage("READY");
                SocketMethodHelpers.sendMessage(socket, messageModel);
                host_viewer.add(socket);
                System.out.println("Viewer connected");

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleControlClient(Socket socket) {
        try {
            MessageModel messageModel = SocketMethodHelpers.readMessage(socket);

            if (messageModel.getAction() == Constant.ACTION_VIEWER_CONTROLLER) {
                String id = messageModel.getPartner_id();
                String password = messageModel.getPartner_password();

                Socket host = hostControllerSockets.get(id);
                System.out.println("Viewer control connected");
                String host_pass = hostPassword.get(id);
                if(host_pass != null && host != null && host_pass.equals(password)){
                    new ControlHandler(socket, host).start();
                    System.out.println("Viewer control connected");
                }else{
                    System.out.println("Viewer control wrong password");
                }
            } else if (messageModel.getAction() == Constant.ACTION_HOST_CONTROLLER) {
                hostControllerSockets.put(messageModel.getOwner_id(), socket);
                //hostControlSocket = socket;
                System.out.println("Host control connected");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class HostHandler extends Thread {
        private final Socket host;
        private final String hostId;

        HostHandler(Socket host, String id) { this.host = host; this.hostId = id; }

        public void run() {
            try {
                InputStream in = host.getInputStream();
                while (true) {
                    int size = new DataInputStream(in).readInt();
                    byte[] buffer = in.readNBytes(size);
                    List<Socket> viewers = hostViewers.get(this.hostId);
                    synchronized (viewers) {
                        for (Socket viewer : viewers) {
                            try {
                                OutputStream out = viewer.getOutputStream();
                                new DataOutputStream(out).writeInt(size);
                                out.write(buffer);
                                out.flush();
                            } catch (IOException e) {
                                viewers.remove(viewer);
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Host disconnected");
            }
        }
    }

    static class ControlHandler extends Thread {
        private final Socket viewer;
        private final Socket hostControlSocket;

        ControlHandler(Socket viewer, Socket hostControlSocket) {
            this.viewer = viewer;
            this.hostControlSocket = hostControlSocket;
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(viewer.getInputStream()));
                String command;
                while ((command = in.readLine()) != null) {
                    // Forward control command to host
                    if (hostControlSocket != null && !hostControlSocket.isClosed()) {
                        PrintWriter out = new PrintWriter(hostControlSocket.getOutputStream(), true);
                        out.println(command);
                    }
                }
            } catch (IOException e) {
                System.out.println("Viewer control disconnected");
                //controlViewers.remove(viewer);
            }
        }
    }
}
