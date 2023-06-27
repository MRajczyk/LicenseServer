package Server;

public class GrantedLicense {
    private String IPaddress;
    private long validUntil;

    public GrantedLicense(String IPaddress, long validUntil) {
        this.IPaddress = IPaddress;
        this.validUntil = validUntil;
    }

    public String getIPaddress() {
        return IPaddress;
    }

    public void setIPaddress(String IPaddress) {
        this.IPaddress = IPaddress;
    }

    public long getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(long validUntil) {
        this.validUntil = validUntil;
    }
}
