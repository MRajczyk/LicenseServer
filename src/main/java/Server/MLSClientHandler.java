package Server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class MLSClientHandler implements Runnable {
    Socket clientSocket;
    char[] buffer = new char[1024];
    MLServer serverInstance;

    MLSClientHandler(Socket socket, MLServer server) {
        this.clientSocket = socket;
        this.serverInstance = server;
    }

    @Override
    public void run() {
        System.out.println("New connection: " + this.clientSocket.getInetAddress().getHostName() + ":" + this.clientSocket.getPort());
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            try {
                int read = bufferedReader.read(buffer);
                String clientMsg = new String(buffer, 0, read);
                //process license


                bufferedWriter.write(buffer, 0, read);
                bufferedWriter.flush();
            } catch (StringIndexOutOfBoundsException | SocketException e) {
                System.out.println("Exception! While serving client. There was either a connection error or a problem with message...");
            }
        } catch (IOException e) {
            System.out.println("There was a problem during establishing the connection");
        }
        finally {
            try {
                this.clientSocket.close();
            } catch (IOException e) {
                //
            }
        }
    }
}
