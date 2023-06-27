package ClientAPI;

public class ClientAPI {
    private String licenseUsername;
    private String licenseKey;
    private String serverIP;
    private String serverPort;

    void start(String serverIP, String serverPort) {

    }

    void SetLicence(String licenseUsername, String licenseKey) {

    }

    String GetLicenseToken() {
        return "";
    }

    void Stop() {
        this.licenseUsername = "";
        this.licenseKey = "";
        this.serverIP = "";
        this.serverPort = "";
    }
}
