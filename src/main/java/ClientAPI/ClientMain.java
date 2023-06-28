package ClientAPI;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) throws InterruptedException {
        ClientAPI api = new ClientAPI();
        int port;
        String serverIP;

        Scanner scanner = new Scanner(System.in);
        while(true) {
            System.out.println("Do you want to enter server data manually(1) or try to resolve it with UDP DISCOVER message(2)?");
            try {
                int answer = Integer.parseInt(scanner.nextLine());
                if(answer == 2) {
                    System.out.println("Please wait while api connects to the server...");
                    if(api.discoverMLServer()) {
                        api.start(api.getServerIPFromUDP(), api.getServerPortFromUDP());
                    }
                } else {
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
                }
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input, try again.");
            }
        }

        api.setLicence("Guest", "adb831a7fdd83dd1e2a309ce7591dff8");
        TokenModel licenseToken = api.GetLicenseToken();
        System.out.println(licenseToken.LicenseUserName + " " + licenseToken.License + " " + licenseToken.Expired);
        Thread.sleep(10000);
        licenseToken = api.GetLicenseToken();
        System.out.println(licenseToken.LicenseUserName + " " + licenseToken.License + " " + licenseToken.Expired);

        api.stop();
    }
}
