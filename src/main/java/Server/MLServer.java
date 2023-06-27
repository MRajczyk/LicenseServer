package Server;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MLServer {
    private final String LICENSES_FILENAME = "licenses.json";

    private ServerSocket serverSocket;
    private Integer tcpPort;

    private final Object shutdownSyncObject = new Object();
    private Boolean shutdownFlag = false;

    private final Object licensesSyncObject = new Object();
    private License[] licenses;
    private Map<String, ArrayList<GrantedLicense>> activeLicenses = new HashMap<>();

    DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public Object getLicensesSyncObject() {
        return licensesSyncObject;
    }

    public String generateLicenseKey(String username) {
        Hasher hasher = Hashing.md5().newHasher();
        hasher.putString(username, StandardCharsets.UTF_8);
        HashCode md5 = hasher.hash();

        return md5.toString();
    }

    public String getLicense(String username, String licenseKey, String ipAddress) {
        synchronized (licensesSyncObject) {
            for(License lic: licenses){
                if(lic.LicenseUserName.equals(username)) {
                    if(generateLicenseKey(username).equals(licenseKey)) {
                        //TODO: add IP validation pamietac o 'any'
                        if(lic.License == 0) {
                            return "Error! Number of license activations exhausted";
                        } else {
                            GrantedLicense newLicense = new GrantedLicense(ipAddress, Instant.now().getEpochSecond() + lic.ValidationTime);
                            if(activeLicenses.containsKey(username)) {
                                boolean added_flag = false;
                                for(GrantedLicense license : activeLicenses.get(username)) {
                                    if(license.getIPaddress().equals(ipAddress)) {
                                        license.setValidUntil(lic.ValidationTime);
                                        added_flag = true;
                                        break;
                                    }
                                }
                                if(!added_flag) {
                                    activeLicenses.get(username).add(newLicense);
                                    lic.License -= 1;
                                }
                            } else {
                                ArrayList<GrantedLicense> newLicenseListForUsername = new ArrayList(){};
                                newLicenseListForUsername.add(newLicense);
                                activeLicenses.put(username, newLicenseListForUsername);
                                lic.License -= 1;
                            }
                            OffsetDateTime.now().toString();
                            if(lic.ValidationTime == 0) {
                                return OffsetDateTime.now().format(df);
                            } else {
                                return OffsetDateTime.now().plusSeconds(lic.ValidationTime).format(df);
                            }
                        }
                    } else {
                        return "Error! Entered license key is invalid!";
                    }
                }
            }
            return "Error! There's no license for " + username;
        }
    }

    public void loadLicenses() {
        StringBuilder licenseJsonFileContents = new StringBuilder();
        try {
            File myObj = new File(LICENSES_FILENAME);
            Scanner sc = new Scanner(myObj);
            while (sc.hasNextLine()) {
                String data = sc.nextLine();
                licenseJsonFileContents.append(data);
            }
            sc.close();

            Gson gson = new Gson();
            LicenseJsonModel licensesObject = gson.fromJson(licenseJsonFileContents.toString(), LicenseJsonModel.class);
            this.licenses = licensesObject.payload;
            for(License license : this.licenses) {
                if(license.License == 0) {
                    //assign -1 for infinite-workstation licenses
                    license.License = -1;
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("File licenses.json not found!");
        }
    }

    private void updateLicenses() {

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

        //thread to get user input so nothing blocks
        Runnable inputListener = () -> {
            while(true) {
                System.out.println("Enter p(rint), q(uit) or s(how port)");
                Scanner sc = new Scanner(System.in);
                String input = sc.nextLine();
                if (input.equals("q")) {
                    synchronized (shutdownSyncObject) {
                        shutdownFlag = true;
                        return;
                    }
                } else if(input.equals("p")) {
                    synchronized (licensesSyncObject) {
                        //print
                        System.out.println("print placeholder");
                    }
                } else if(input.equals("s")) {
                    System.out.println(this.tcpPort);
                }
            }
        };
        Thread userInputThread = new Thread(inputListener);
        userInputThread.start();

//        for(License lic: licenses) {
//            System.out.println(lic.LicenseUserName + " " + this.generateLicenseKey(lic.LicenseUserName));
//        }

        while(true) {
            try {
                Socket clientSocket = this.serverSocket.accept();
                //ustawic na nieblokujace czy cos, idk jak
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
