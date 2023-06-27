package Server;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.Objects;
import java.util.Scanner;

public class MLServer {
    private ServerSocket serverSocket;
    private Payload licensesPayload;
    private Integer tcpPort;

    private final Object shutdownSyncObject = false;
    private Boolean shutdownFlag = false;

    public void loadLicenses() {
        StringBuilder licenseJsonFileContents = new StringBuilder();
        try {
            File myObj = new File("./licenses.json");
            Scanner sc = new Scanner(myObj);
            while (sc.hasNextLine()) {
                String data = sc.nextLine();
                licenseJsonFileContents.append(data);
            }
            sc.close();

            Gson gson = new Gson();
            licensesPayload = gson.fromJson(licenseJsonFileContents.toString(), Payload.class);

        } catch (FileNotFoundException e) {
            System.out.println("File licenses.json not found!");
        }
    }

    public void setTcpPort(Integer tcpPort) {
        this.tcpPort = tcpPort;
    }

    public void start() {
        System.out.println("Starting server...");
        try {
            serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(10000);
            SocketAddress socketAddress = new InetSocketAddress("0.0.0.0", this.tcpPort);
            serverSocket.bind(socketAddress);
        } catch (IOException e) {
            System.out.println("Error while binding address, ");
        }

        //thread to get user input
        new Runnable() {
            @Override
            public void run() {
                while(true) {
                    Scanner sc = new Scanner(System.in);
                    if (Objects.equals(sc.nextLine(), "q") || Objects.equals(sc.nextLine(), "Q")) {
                        synchronized (shutdownSyncObject) {
                            shutdownFlag = true;
                            return;
                        }
                    }
                }
            }
        };

        while(true) {
            try {
                Socket clientSocket = this.serverSocket.accept();
                clientSocket.setSoTimeout(10000);
                MLSClientHandler handler = new MLSClientHandler(clientSocket, this);
                Thread thread = new Thread(handler);
                thread.start();
            } catch(SocketTimeoutException e) {
                synchronized (shutdownSyncObject) {
                    if(shutdownFlag) {
                        return;
                    }
                }
            }
            catch (IOException e) {
                System.out.println("Failed to set timeout time on socket for new connection");
            }
        }
    }
}
