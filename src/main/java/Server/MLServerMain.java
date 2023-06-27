package Server;

import java.util.Scanner;

public class MLServerMain {

    public static void main(String[] args) {
        MLServer MLServer = new MLServer();
        MLServer.loadLicenses();

        System.out.println("Enter port on which the server shall listen to incoming calls");
        Scanner scanner = new Scanner(System.in);
        Integer port = scanner.nextInt();

        MLServer.start();
    }
}
