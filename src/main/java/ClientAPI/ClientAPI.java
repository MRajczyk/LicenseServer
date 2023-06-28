package ClientAPI;
import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;

public class ClientAPI {
    private String licenseUsername;
    private String licenseKey;
    private TokenModel token;
    private String serverIP;
    private Integer serverPort;

    private Socket clientSocket;

    private final Object serverInfoSyncObject = new Object();
    private final Object shutdownSyncObject = new Object();
    private Boolean shutdownFlag = false;

//    synchronized (shutdownSyncObject) {
//        if(shutdownFlag) {
//            return;
//        }
//    }

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

            //process license
            Gson gson = new Gson();
            return gson.fromJson(clientMsg, TokenModel.class);
        } catch (IOException e) {
            return new TokenModel(this.licenseUsername, false, "Could not connect to MLServer");
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
        if(this.token == null) {
            TokenModel returnedToken = this.sendLicenseToServer();
            //start refreshing thread
            this.token = returnedToken;
        }

        return this.token;
    }

    public void Stop() {
        synchronized (shutdownSyncObject) {
            this.shutdownFlag = true;
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
