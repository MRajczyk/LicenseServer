package Server;

import com.google.gson.Gson;

import java.io.*;
import java.net.*;

public class MLSClientHandler implements Runnable {
    private final Socket clientSocket;
    private final char[] buffer = new char[1024];
    private MLServer serverInstance;

    MLSClientHandler(Socket socket, MLServer server) {
        this.clientSocket = socket;
        this.serverInstance = server;
    }

    private char[] convertStringToCharArray(String str) {
        char[] ch = new char[str.length()];
        for (int i = 0; i < str.length(); i++) {
            ch[i] = str.charAt(i);
        }
        return ch;
    }

    @Override
    public void run() {
        try {
            InetAddress localHost = Inet4Address.getLocalHost();
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localHost);
            String clientIpAddress = this.clientSocket.getInetAddress().toString().substring(1) + "/" + networkInterface.getInterfaceAddresses().get(0).getNetworkPrefixLength();
            System.out.println("New connection: " + clientIpAddress);

            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            try {
                int read = bufferedReader.read(buffer);
                String clientMsg = new String(buffer, 0, read);

                //process license
                Gson gson = new Gson();
                LicenseRequestModel licenseRequest = gson.fromJson(clientMsg, LicenseRequestModel.class);
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
