package ClientAPI;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) throws InterruptedException {
        ClientAPI api = new ClientAPI();
        int port;
        String serverIP;

        Scanner scanner = new Scanner(System.in);
        portLoop:
        while(true) {
            System.out.println("Enter port for TCP connection");
            try {
                port = Integer.parseInt(scanner.nextLine());
                while(true) {
                    System.out.println("Enter ip address of the server");
                    try {
                        serverIP = scanner.nextLine();
                        api.start(serverIP, port);
                        break portLoop;
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input, try again.");
                    }
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input, try again.");
            }
        }

        api.setLicence("Radek", "9f3a08745c23449a53fc05d68eda1e1b");
        TokenModel licenseToken = api.GetLicenseToken();
        System.out.println(licenseToken.LicenseUserName + " " + licenseToken.License + " " + licenseToken.Expired);
        Thread.sleep(3000);
        licenseToken = api.GetLicenseToken();
        System.out.println(licenseToken.LicenseUserName + " " + licenseToken.License + " " + licenseToken.Expired);

        api.stop();
    }
}
