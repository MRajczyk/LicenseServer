package Server;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class MLServer {
    Payload licensesPayload;

    void loadLicenses() {
        StringBuilder licenseJsonFileContents = new StringBuilder();
        try {
            File myObj = new File("./licenses.json");
            Scanner sc = new Scanner(myObj);
            while (sc.hasNextLine()) {
                String data = sc.nextLine();
                licenseJsonFileContents.append(data);
            }
            sc.close();

            Gson gson = new Gson();
            licensesPayload = gson.fromJson(licenseJsonFileContents.toString(), Payload.class);

        } catch (FileNotFoundException e) {
            System.out.println("File licenses.json not found!");
        }
    }
}
