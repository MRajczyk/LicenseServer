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
    private ArrayList<String> discoverList = new ArrayList<>();

    DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

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
                        boolean flagIpOnWhitelist = false;
                        for(String ipAddr : lic.IPaddresses) {
                            if(ipAddr.equals(ipAddress) || ipAddr.equals("any")) {
                                flagIpOnWhitelist = true;
                                break;
                            }
                        }
                        if(!flagIpOnWhitelist) {
                            return "Error! IP is not whitelisted";
                        } else {
                            if (lic.License == 0) {
                                return "Error! Number of license activations exhausted";
                            } else {
                                GrantedLicense newLicense = new GrantedLicense(ipAddress, Instant.now().getEpochSecond() + lic.ValidationTime);
                                if (activeLicenses.containsKey(username)) {
                                    boolean added_flag = false;
                                    for (GrantedLicense license : activeLicenses.get(username)) {
                                        if (license.getIPaddress().equals(ipAddress)) {
                                            license.setValidUntil(Instant.now().getEpochSecond() + lic.ValidationTime);
                                            added_flag = true;
                                            break;
                                        }
                                    }
                                    if (!added_flag) {
                                        activeLicenses.get(username).add(newLicense);
                                        lic.License -= 1;
                                    }
                                } else {
                                    ArrayList<GrantedLicense> newLicenseListForUsername = new ArrayList() {
                                    };
                                    newLicenseListForUsername.add(newLicense);
                                    activeLicenses.put(username, newLicenseListForUsername);
                                    lic.License -= 1;
                                }
                                if (lic.ValidationTime == 0) {
                                    return OffsetDateTime.now().format(df);
                                } else {
                                    return OffsetDateTime.now().plusSeconds(lic.ValidationTime).format(df);
                                }
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

    public void setTcpPort(Integer tcpPort) {
        this.tcpPort = tcpPort;
    }

    public void start() {
        System.out.println("Starting server...");
        try {
            serverSocket = new ServerSocket();
            serverSocket.setSoTimeout(5000);
            SocketAddress socketAddress = new InetSocketAddress("0.0.0.0", this.tcpPort);
            serverSocket.bind(socketAddress);
        } catch (IOException e) {
            System.out.println("Error while binding address, ");
        }
        System.out.println("Server is running..");

        //udp thread
        Runnable udpListener = () -> {
            while(true) {
                synchronized (shutdownSyncObject) {
                    if(shutdownFlag) {
                        return;
                    }
                }
                //receive
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
                }
                catch (IOException e) {
                    System.out.println("Error occured while binding multicast receiver socket! Thread shutdown.");
                    return;
                }
                byte[] buff = new byte[1024];
                DatagramPacket pack = new DatagramPacket(buff, buff.length);
                try {
                    udpSocket.receive(pack);
                    message = new String(buff, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    udpSocket.close();
                    continue;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //send tcp port
                try {
                    if(!message.startsWith("DISCOVER")) {
                        MulticastSocket ms = new MulticastSocket();
                        buff = ("INCORRECT REQUEST").getBytes(StandardCharsets.UTF_8);
                        DatagramPacket dp = new DatagramPacket(buff, buff.length, InetAddress.getByName("230.0.0.0"), 2323);
                        ms.setTimeToLive(10);
                        ms.send(dp);
                        ms.close();
                        continue;
                    }
                    discoverList.add("DISCOVER: " + pack.getAddress().toString());
                    MulticastSocket ms = new MulticastSocket();
                    buff = ("PORT:" + this.tcpPort).getBytes(StandardCharsets.UTF_8);
                    DatagramPacket dp = new DatagramPacket(buff, buff.length, InetAddress.getByName("230.0.0.0"), 2323);
                    ms.setTimeToLive(10);
                    ms.send(dp);
                    ms.close();
                } catch (IOException e) {
                    System.out.println("Error occured while sending mutlicast message");
                }
            }
        };
        Thread udpThread = new Thread(udpListener);
        udpThread.start();

        //thread to get user input so nothing blocks
        Runnable inputListener = () -> {
            while(true) {
                System.out.println("Enter p(rint), q(uit) or s(how port)");
                Scanner sc = new Scanner(System.in);
                String input = sc.nextLine();
                switch (input) {
                    case "q":
                        synchronized (shutdownSyncObject) {
                            shutdownFlag = true;
                            System.out.println("Shutting down. Waiting for socket to release the lock\n\tProgram will exit shortly...");
                            return;
                        }
                    case "p":
                        synchronized (licensesSyncObject) {
                            activeLicenses.forEach((k, v) -> {
                                System.out.println("License username: " + k + " | Licenses used: " + v.size());
                                v.forEach(gl -> {
                                    System.out.println("\tIPAddr: " + gl.getIPaddress() + " | License Valid For (seconds) " + (gl.getValidUntil() - Instant.now().getEpochSecond()));
                                });
                            });
                            System.out.println("All DISCOVER packets received:");
                            for(int i = 0; i < discoverList.size(); ++i) {
                                System.out.println(i + ". " + discoverList.get(i));
                            }
                        }
                        break;
                    case "s":
                        System.out.println(this.tcpPort);
                        break;
                }
            }
        };
        Thread userInputThread = new Thread(inputListener);
        userInputThread.start();

        for(License lic: licenses) {
            System.out.println(lic.LicenseUserName + " " + this.generateLicenseKey(lic.LicenseUserName));
        }

        //update licenses
        Runnable licenseUpdater = () -> {
            while(true) {
                synchronized (shutdownSyncObject) {
                    if(shutdownFlag) {
                        return;
                    }
                }
                synchronized (licensesSyncObject) {
                    ArrayList<String> keysToBeDeleted = new ArrayList<>();
                    activeLicenses.forEach((k,v) -> {
                        ArrayList<GrantedLicense> licensesToBeRemoved = new ArrayList<>();
                        for (GrantedLicense grantedLicense : v) {
                            //if not valid anymore, delete from licenses and re-add to licenses list
                            if(grantedLicense.getValidUntil() < Instant.now().getEpochSecond()) {
                                licensesToBeRemoved.add(grantedLicense);
                                for(License license : licenses) {
                                    if(license.LicenseUserName.equals(k)) {
                                        license.License += 1;
                                        break;
                                    }
                                }
                            }
                        }
                        licensesToBeRemoved.forEach(v::remove);
                        if(v.size() == 0) {
                            keysToBeDeleted.add(k);
                        }
                    });
                    keysToBeDeleted.forEach(key -> {
                        activeLicenses.remove(key);
                    });
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    //
                }
            }
        };
        Thread updateLicensesThread = new Thread(licenseUpdater);
        updateLicensesThread.start();

        while(true) {
            synchronized (shutdownSyncObject) {
                if(shutdownFlag) {
                    return;
                }
            }
            try {
                Socket clientSocket = this.serverSocket.accept();
                MLSClientHandler handler = new MLSClientHandler(clientSocket, this);
                Thread thread = new Thread(handler);
                thread.start();
            }
            catch (IOException e) {
                //expected
            }
        }
    }
}
