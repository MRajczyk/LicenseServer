package ClientAPI;

public class TokenModelError {
    public String LicenseUserName;
    public boolean License;
    public String Description;

    public TokenModelError(String licenseUserName, boolean license, String description) {
        LicenseUserName = licenseUserName;
        License = license;
        Description = description;
    }
}
