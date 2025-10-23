package com.example.ultraviewdemo.helpers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class SocketMethodHelpers {

	public static Socket createSocket() throws Exception {
		return new Socket("localhost", 8001);
	}

    public static void sendMessageNoTrack(Socket socket, Object message) {
        try {
            DataOutputStream outServer = new DataOutputStream(socket.getOutputStream());
            byte[] sentData = SocketMethodHelpers.objectToByteArray(message);
            outServer.writeInt(sentData.length);
            outServer.write(sentData);
            outServer.flush();
        }catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

	public static void sendMessage(Socket socket, Object message) throws Exception {
		DataOutputStream outServer = new DataOutputStream(socket.getOutputStream());
		byte[] sentData = SocketMethodHelpers.objectToByteArray(message);
		outServer.writeInt(sentData.length);
		outServer.write(sentData);
		outServer.flush();
	}

	public static <T> T readMessage(Socket socket) throws Exception {
		DataInputStream inputServer = new DataInputStream(socket.getInputStream());
		int length = inputServer.readInt();
		byte[] received = inputServer.readNBytes(length);

		T message = SocketMethodHelpers.byteArrayToType(received);

		return message;
	}

	public static <T> T byteArrayToType(byte[] data) throws Exception {
		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		ObjectInputStream ois = new ObjectInputStream(bis);
		return (T) ois.readObject();
	}

	public static byte[] objectToByteArray(Object object) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(object);
		oos.flush();
		return bos.toByteArray();
	}
	
	
	
}
