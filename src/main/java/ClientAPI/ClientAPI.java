package ClientAPI;
import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class ClientAPI {
    private String licenseUsername;
    private String licenseKey;
    private TokenModel token;
    private String serverIP;
    private Integer serverPort;

    private Socket clientSocket;
    Thread tokenRenewerThread;

    private final Object serverInfoSyncObject = new Object();
    private final Object shutdownSyncObject = new Object();
    private final Object tokenSyncObject = new Object();
    private Boolean shutdownFlag = false;

    public void start() {
    //find server in local network
        synchronized (serverInfoSyncObject) {
            //TODO: UDP
        }
    }

    public void start(String serverIP, Integer serverPort) {
        synchronized(serverInfoSyncObject) {
            this.serverIP = serverIP;
            this.serverPort = serverPort;
        }
    }

    public void setLicence(String licenseUsername, String licenseKey) {
        synchronized (serverInfoSyncObject) {
            this.licenseUsername = licenseUsername;
            this.licenseKey = licenseKey;
        }
    }

    private char[] convertStringToCharArray(String str) {
        char[] ch = new char[str.length()];
        for (int i = 0; i < str.length(); i++) {
            ch[i] = str.charAt(i);
        }
        return ch;
    }

    private TokenModel sendLicenseToServer() {
        try {
            System.out.println("Sending request to MLServer: " + LocalDateTime.now());
            clientSocket = new Socket(this.serverIP, this.serverPort);
            clientSocket.setSoTimeout(5000);
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            char[] ch;
            //build and send license request to MLServer
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{\"LicenseUserName\":\"").append(this.licenseUsername).append("\",\"LicenseKey\":").append("\"").append(this.licenseKey).append("\"}");
            ch = this.convertStringToCharArray(stringBuilder.toString());
            bufferedWriter.write(ch, 0, ch.length);
            bufferedWriter.flush();

            char[] buffer = new char[1024];
            int read = bufferedReader.read(buffer);
            String clientMsg = new String(buffer, 0, read);

            Gson gson = new Gson();
            return gson.fromJson(clientMsg, TokenModel.class);
        } catch (IOException e) {
            return new TokenModel(this.licenseUsername, false, "Error! Could not connect to MLServer");
        }
        finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                //
            }
        }
    }

    public TokenModel GetLicenseToken() {
        synchronized (tokenSyncObject) {
            if (this.token == null) {
                this.token = this.sendLicenseToServer();
                if (this.token.License) {
                    Runnable tokenRenewer = () -> {
                        try {
                            while (true) {
                                long secondsToWait;
                                if (this.token == null || this.token.Expired.startsWith("Error!")) {
                                    secondsToWait = 60;
                                } else {
                                    DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                                    LocalDateTime dateFromToken = LocalDateTime.parse(this.token.Expired, format);
                                    LocalDateTime dateNow = LocalDateTime.now();
                                    if (dateNow.isAfter(dateFromToken)) {
                                        //should never happen
                                        secondsToWait = 60;
                                    } else {
                                        Duration duration = Duration.between(dateNow, dateFromToken);
                                        secondsToWait = duration.getSeconds();
                                    }
                                }
                                Thread.sleep(secondsToWait * 1000);
                                synchronized (shutdownSyncObject) {
                                    if (this.shutdownFlag) {
                                        return;
                                    }
                                }
                                synchronized (tokenSyncObject) {
                                    this.token = this.sendLicenseToServer();
                                }
                            }
                        } catch (InterruptedException e) {
                            System.out.println("License-renewing thread shutting down.");
                        }
                    };
                    tokenRenewerThread = new Thread(tokenRenewer);
                    tokenRenewerThread.start();
                }
                return this.token;
            }
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        LocalDateTime tokenValidDate = LocalDateTime.parse(this.token.Expired, format);
        LocalDateTime now = LocalDateTime.now();
        synchronized (tokenSyncObject) {
            if (this.token.License && (now.isBefore(tokenValidDate) || tokenValidDate.isEqual(now))) {
                System.out.println("licence validity NOT refreshed, state returned!");
            } else if (!this.token.License || now.isAfter(tokenValidDate)) {
                this.token = sendLicenseToServer();
                return this.token;
            }
        }
        return this.token;
    }

    public void stop() {
        synchronized (shutdownSyncObject) {
            this.shutdownFlag = true;
            tokenRenewerThread.interrupt();
        }

        synchronized (serverInfoSyncObject) {
            this.token = null;
            this.licenseUsername = "";
            this.licenseKey = "";
            this.serverIP = "";
            this.serverPort = null;
        }
    }
}
