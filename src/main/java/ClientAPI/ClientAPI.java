package ClientAPI;
import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
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
    private String serverIPFromUDP;
    private Integer serverPortFromUDP;

    private Socket clientSocket;
    Thread tokenRenewerThread;

    private final Object serverInfoSyncObject = new Object();
    private final Object shutdownSyncObject = new Object();
    private final Object tokenSyncObject = new Object();
    private Boolean shutdownFlag = false;

    Thread udpThread;

    public boolean discoverMLServer() {
    //find server in local network
        try {
            Runnable udpListener = () -> {
                synchronized (shutdownSyncObject) {
                    if (shutdownFlag) {
                        return;
                    }
                }
                byte[] buff = new byte[1024];
                //send discover request
                try {
                    MulticastSocket ms = new MulticastSocket();
                    buff = ("DISCOVER").getBytes(StandardCharsets.UTF_8);
                    DatagramPacket dp = new DatagramPacket(buff, buff.length, InetAddress.getByName("230.0.0.0"), 2323);
                    ms.setTimeToLive(10);
                    ms.send(dp);
                    ms.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Error occured while sending mutlicast message");
                    return;
                }
                //receive
                buff = new byte[1024];
                int mcPort = 2323;
                MulticastSocket udpSocket;
                String message;
                try {
                    udpSocket = new MulticastSocket(null);
                    udpSocket.setReuseAddress(true);
                    InetSocketAddress address = new InetSocketAddress("0.0.0.0", mcPort);
                    udpSocket.bind(address);
                    udpSocket.setSoTimeout(10000);
                    udpSocket.joinGroup(InetAddress.getByName("230.0.0.0"));
                } catch (IOException e) {
                    System.out.println("Error occured while binding multicast receiver socket! Thread shutdown.");
                    return;
                }
                DatagramPacket pack = new DatagramPacket(buff, buff.length);
                try {
                    udpSocket.receive(pack);
                    message = new String(buff, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    udpSocket.close();
                    System.out.println("Error occured while getting message from server! Thread shutdown.");
                    return;
                }

                if (message.startsWith("PORT")) {
                    synchronized (serverInfoSyncObject) {
                        try {
                            this.serverPortFromUDP = Integer.parseInt(message.substring(0, 9).split(":")[1]);
                            this.serverIPFromUDP = pack.getAddress().toString().substring(1);
                            System.out.println(this.serverIPFromUDP + ":" + this.serverPortFromUDP);
                        } catch (NumberFormatException e) {
                            System.out.println("Incorrect tcp received from server! Thread shutdown.");
                        }
                    }
                }
            };
            udpThread = new Thread(udpListener);
            udpThread.start();
            udpThread.join();
        } catch (InterruptedException e) {
            System.out.println("UDP-resolving thread shutting down");
            return false;
        }
        return this.serverIPFromUDP != null && !this.serverIPFromUDP.equals("");
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
            TokenModel response = gson.fromJson(clientMsg, TokenModel.class);
            if(!response.License) {
                TokenModelError errorResponse = gson.fromJson(clientMsg, TokenModelError.class);
                return new TokenModel(errorResponse.LicenseUserName, errorResponse.License, errorResponse.Description);
            } else {
                return response;
            }
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
        synchronized (tokenSyncObject) {
            if (!this.token.License) {
                this.token = sendLicenseToServer();
                return this.token;
            }
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        LocalDateTime tokenValidDate = LocalDateTime.parse(this.token.Expired, format);
        LocalDateTime now = LocalDateTime.now();
        synchronized (tokenSyncObject) {
            if (this.token.License && (now.isBefore(tokenValidDate) || tokenValidDate.isEqual(now))) {
                System.out.println("licence validity NOT refreshed, state returned!");
            } else if (now.isAfter(tokenValidDate)) {
                this.token = sendLicenseToServer();
                return this.token;
            }
        }
        return this.token;
    }

    public void stop() {
        synchronized (shutdownSyncObject) {
            this.shutdownFlag = true;
            if(tokenRenewerThread != null && tokenRenewerThread.isAlive()) {
                tokenRenewerThread.interrupt();
            }
            udpThread.interrupt();
        }

        synchronized (serverInfoSyncObject) {
            this.token = null;
            this.licenseUsername = "";
            this.licenseKey = "";
            this.serverIP = "";
            this.serverPort = null;
        }
    }

    public String getServerIPFromUDP() {
        return serverIPFromUDP;
    }

    public Integer getServerPortFromUDP() {
        return serverPortFromUDP;
    }
}
