package Server;

import java.util.InputMismatchException;
import java.util.Scanner;

public class MLServerMain {

    public static void main(String[] args) {
        MLServer MLServer = new MLServer();
        MLServer.loadLicenses();

        Scanner scanner = new Scanner(System.in);
        while(true) {
            System.out.println("Enter port on which the server shall listen to incoming calls");
            int port;
            try {
                port = Integer.parseInt(scanner.nextLine());
                MLServer.setTcpPort(port);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Invalid input, try again.");
            }
        }

        MLServer.start();
    }
}
