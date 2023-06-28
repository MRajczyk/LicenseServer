package ClientAPI;

import Server.LicenseRequestModel;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class ClientAPI {
    private String licenseUsername;
    private String licenseKey;
    private TokenModel token;
    private String serverIP;
    private Integer serverPort;

    private final Object serverInfoSyncObject = new Object();
    private final Object shutdownSyncObject = new Object();
    private Boolean shutdownFlag = false;

//    synchronized (shutdownSyncObject) {
//        if(shutdownFlag) {
//            return;
//        }
//    }

    void start() {
    //find server in local network
        synchronized (serverInfoSyncObject) {
//            this.serverIP = serverIP;
//            this.serverPort = serverPort;
        }

    }

    void start(String serverIP, Integer serverPort) {
        synchronized(serverInfoSyncObject) {
            this.serverIP = serverIP;
            this.serverPort = serverPort;
        }
    }

    void SetLicence(String licenseUsername, String licenseKey) {
        synchronized (serverInfoSyncObject) {
            this.licenseUsername = licenseUsername;
            this.licenseKey = licenseKey;
        }
    }

    String GetLicenseToken() {
        try {
            Socket clientSocket = new Socket(this.serverIP, this.serverPort);
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            char[] buffer = new char[1024];

            try {
                String response = this.serverInstance.getLicense(licenseRequest.LicenseUserName, licenseRequest.LicenseKey, clientIpAddress);

                StringBuilder stringBuilder = new StringBuilder();
                char[] ch;
                if(response.startsWith("Error!")) {
                    stringBuilder.append("{\"LicenseUserName\":\"").append(licenseRequest.LicenseUserName).append("\",\"License\":").append("false,").append("\"Description\":\"").append(response).append("\"}");
                    String str = stringBuilder.toString();
                    ch = this.convertStringToCharArray(str);
                } else {
                    stringBuilder.append("{\"LicenseUserName\":\"").append(licenseRequest.LicenseUserName).append("\",\"License\":").append("true,").append("\"Expired\":\"").append(response).append("\"}");
                    String str = stringBuilder.toString();
                    ch = this.convertStringToCharArray(str);
                }
                bufferedWriter.write(ch, 0, ch.length);
                bufferedWriter.flush();


                int read = bufferedReader.read(buffer);
                String clientMsg = new String(buffer, 0, read);

                //process license
                Gson gson = new Gson();
                TokenModel licenseRequest = gson.fromJson(clientMsg, TokenModel.class);
                if(licenseRequest == null || licenseRequest.LicenseKey.equals("")) {
                    String str = "Error! Invalid json data!";
                    char[] ch = new char[str.length()];
                    for (int i = 0; i < str.length(); i++) {
                        ch[i] = str.charAt(i);
                    }
                    bufferedWriter.write(ch, 0, ch.length);
                    bufferedWriter.flush();
                    return;
                }
            } catch (StringIndexOutOfBoundsException | SocketException e) {
                System.out.println("Exception! While serving client. There was either a connection error or a problem with message...");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    void Stop() {
        synchronized (serverInfoSyncObject) {
            this.token = null;
            this.licenseUsername = "";
            this.licenseKey = "";
            this.serverIP = "";
            this.serverPort = null;
        }
    }
}
