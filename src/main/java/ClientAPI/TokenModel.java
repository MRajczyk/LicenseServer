package ClientAPI;

public class TokenModel {
    public String LicenseUserName;
    public boolean License;
    public String Expired;

    public TokenModel(String licenseUserName, boolean license, String expired) {
        LicenseUserName = licenseUserName;
        License = license;
        Expired = expired;
    }
}
